package org.example.dndncore.worker.service;

import lombok.RequiredArgsConstructor;
import org.example.dndncore.common.exception.BaseException;
import org.example.dndncore.staffing.model.Trade;
import org.example.dndncore.worker.model.dto.WorkerDetailDto;
import org.example.dndncore.worker.model.entity.AttendanceLog;
import org.example.dndncore.worker.model.entity.Worker;
import org.example.dndncore.worker.model.enums.AttendanceEventType;
import org.example.dndncore.worker.repository.AttendanceLogRepository;
import org.example.dndncore.worker.repository.SafetyAccidentRepository;
import org.example.dndncore.worker.repository.WorkerRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

import static org.example.dndncore.common.model.BaseResponseStatus.FAIL;

/**
 * 작업자 피로도(위험) 점수 산출 — 근태·사고·마스터 공종을 종합하여 {@link Worker}에 스냅샷 저장한다.
 *
 * <p>연속 근무·야간 휴게 계산의 이력 소스는 {@code attendance_log}(CLOCK_IN/OUT 이벤트)이며,
 * {@code attendance_record}는 당일 현장 로스터 스냅샷으로만 사용한다.</p>
 */
@Service
@RequiredArgsConstructor
public class FatigueCalculationService {

    public static final int SCORE_CAP = 100;
    public static final int HIGH_RISK_THRESHOLD = 80;

    private static final int ACCIDENT_LAST_DAYS_WINDOW = 30;
    /** 연속 근무 판단을 위해 과거 몇 일 치 로그를 스캔할지 */
    private static final int LOOKBACK_SCAN_DAYS = 120;

    private static final int PT_ACCIDENT = 20;
    private static final int TRADE_UNKNOWN_POINTS = 5;
    private static final String TRADE_UNKNOWN_KEY = "UNKNOWN";

    private final WorkerRepository workerRepository;
    private final AttendanceLogRepository attendanceLogRepository;
    private final SafetyAccidentRepository accidentRepository;

    /** 상세 프로필 조회 시 등 재계산 + DB 반영 후 응답용 DTO 를 돌려준다. */
    @Transactional
    public WorkerDetailDto.FatigueSummaryRes recalculateAndPersist(Long workerIdx, LocalDate referenceDate) {
        LocalDate ref = referenceDate != null ? referenceDate : LocalDate.now();
        Worker worker = workerRepository.findById(workerIdx)
                .orElseThrow(() -> new BaseException(FAIL));

        // 사고 점수
        LocalDate accidentFrom = ref.minusDays(ACCIDENT_LAST_DAYS_WINDOW);
        boolean accidentInWindow =
                accidentRepository.existsByWorkerIdxAndOccurredAtBetween(workerIdx, accidentFrom, ref);
        int ptAccident = accidentInWindow ? PT_ACCIDENT : 0;

        // 출퇴근 로그에서 날짜별 최초 출근·최종 퇴근 시각 집계
        LocalDate attendanceFrom = ref.minusDays(LOOKBACK_SCAN_DAYS);
        List<AttendanceLog> logRows =
                attendanceLogRepository.findAllByWorkerIdxAndWorkDateBetween(workerIdx, attendanceFrom, ref);

        Set<LocalDate> workedDates = new HashSet<>();
        Map<LocalDate, LocalTime> clockInByDate  = new HashMap<>();
        Map<LocalDate, LocalTime> clockOutByDate = new HashMap<>();
        for (AttendanceLog log : logRows) {
            if (log.getEventType() == AttendanceEventType.CLOCK_IN) {
                workedDates.add(log.getWorkDate());
                // 하루에 여러 CLOCK_IN 이 있으면 가장 이른 값 유지
                clockInByDate.merge(log.getWorkDate(), log.getRecognizedAt(),
                        (existing, next) -> existing.isBefore(next) ? existing : next);
            } else if (log.getEventType() == AttendanceEventType.CLOCK_OUT) {
                // 하루에 여러 CLOCK_OUT 이 있으면 가장 늦은 값 유지
                clockOutByDate.merge(log.getWorkDate(), log.getRecognizedAt(),
                        (existing, next) -> existing.isAfter(next) ? existing : next);
            }
        }

        int streakDays = consecutiveOnsiteDaysEnding(workedDates, ref);
        int ptStreak = streakScore(streakDays);

        OvernightEval overnightEval = overnightRestEval(clockInByDate, clockOutByDate, ref);
        int ptOvernight = overnightEval.points();

        int ptTradeRisk = resolveTradeRiskPoints(worker);

        int rawSum = ptAccident + ptStreak + ptOvernight + ptTradeRisk;
        int capped = Math.min(SCORE_CAP, rawSum);
        boolean highRisk = capped >= HIGH_RISK_THRESHOLD;

        LocalDateTime now = LocalDateTime.now();
        worker.replaceFatigueSnapshot(capped, highRisk, ptAccident, ptStreak, ptOvernight, ptTradeRisk, now);
        workerRepository.save(worker);

        String accidentDetail =
                accidentInWindow
                        ? ACCIDENT_LAST_DAYS_WINDOW + "일 이내 안전사고 등록 내역이 있습니다.(" + PT_ACCIDENT + "점 반영)"
                        : "최근 " + ACCIDENT_LAST_DAYS_WINDOW + "일간 안전사고 이력이 없습니다.";

        String streakDetail =
                switch (ptStreak) {
                    case 10 -> streakDays + "일 연속 출근(CLOCK_IN 기준) → +10점";
                    case 20 -> streakDays + "일 연속 출근(CLOCK_IN 기준) → +20점";
                    case 30 -> streakDays + "일 연속 출근(CLOCK_IN 기준) → +30점";
                    case 40 -> streakDays + "일 연속 출근(CLOCK_IN 기준) → +40점";
                    default -> streakDays <= 5
                            ? "연속 출근 " + streakDays + "일(6일 미만이라 가점 없음)"
                            : streakDays + "일 연속 출근이지만 구간 규칙에 해당하지 않습니다.";
                };

        Trade classifiedTrade = Trade.classifyWorker(worker);
        String tradeCategoryKey =
                classifiedTrade != null ? classifiedTrade.name() : TRADE_UNKNOWN_KEY;

        String tradeExplanation;
        if (classifiedTrade != null) {
            tradeExplanation =
                    "마스터 공종("
                            + humanTradeLabel(worker, classifiedTrade)
                            + ") 매핑 → "
                            + tradeCategoryKey
                            + ", 위험도 "
                            + ptTradeRisk
                            + "점 적용";
        } else {
            tradeExplanation =
                    "공종 키워드 미매칭(목공/목수/형틀/철근/용접/장비/굴착/토목/토공/배수/타일/마감/인부/보통공/정리) — 기본("
                            + TRADE_UNKNOWN_POINTS
                            + "점)·trade="
                            + nullable(worker.getTrade());
        }

        return WorkerDetailDto.FatigueSummaryRes.builder()
                .referenceDate(ref)
                .calculatedAt(now)
                .totalScore(capped)
                .rawScoreSum(rawSum)
                .cappedRemainderLost(Math.max(0, rawSum - capped))
                .highRiskWorker(highRisk)
                .accidentScore(ptAccident)
                .accidentOccurredLast30Days(accidentInWindow)
                .accidentDetail(accidentDetail)
                .streakScore(ptStreak)
                .onsiteStreakDays(streakDays)
                .streakDetail(streakDetail)
                .overnightScore(ptOvernight)
                .overnightDetail(overnightEval.note())
                .tradeRiskScore(ptTradeRisk)
                .tradeCategoryKey(tradeCategoryKey)
                .tradeDetail(tradeExplanation)
                .scoreCap(SCORE_CAP)
                .highRiskThreshold(HIGH_RISK_THRESHOLD)
                .build();
    }

    // 전체 workers를 3 쿼리로 처리: IN(사고) + IN(로그) + saveAll
    @Transactional
    public void bulkRecalculateAndPersist(List<Worker> workers, LocalDate referenceDate) {
        LocalDate ref = referenceDate != null ? referenceDate : LocalDate.now();
        List<Long> workerIdxes = workers.stream().map(Worker::getIdx).toList();

        LocalDate accidentFrom = ref.minusDays(ACCIDENT_LAST_DAYS_WINDOW);
        Set<Long> idxesWithAccident = new HashSet<>(
                accidentRepository.findWorkerIdxesWithAccidentBetween(workerIdxes, accidentFrom, ref));

        LocalDate attendanceFrom = ref.minusDays(LOOKBACK_SCAN_DAYS);
        Map<Long, List<AttendanceLog>> logsByWorker = attendanceLogRepository
                .findAllByWorkerIdxInAndWorkDateBetween(workerIdxes, attendanceFrom, ref)
                .stream()
                .collect(Collectors.groupingBy(AttendanceLog::getWorkerIdx));

        LocalDateTime now = LocalDateTime.now();
        for (Worker worker : workers) {
            Long wid = worker.getIdx();
            int ptAccident = idxesWithAccident.contains(wid) ? PT_ACCIDENT : 0;

            List<AttendanceLog> logRows = logsByWorker.getOrDefault(wid, List.of());
            Set<LocalDate> workedDates = new HashSet<>();
            Map<LocalDate, LocalTime> clockInByDate  = new HashMap<>();
            Map<LocalDate, LocalTime> clockOutByDate = new HashMap<>();
            for (AttendanceLog log : logRows) {
                if (log.getEventType() == AttendanceEventType.CLOCK_IN) {
                    workedDates.add(log.getWorkDate());
                    clockInByDate.merge(log.getWorkDate(), log.getRecognizedAt(),
                            (a, b) -> a.isBefore(b) ? a : b);
                } else if (log.getEventType() == AttendanceEventType.CLOCK_OUT) {
                    clockOutByDate.merge(log.getWorkDate(), log.getRecognizedAt(),
                            (a, b) -> a.isAfter(b) ? a : b);
                }
            }

            int streakDays  = consecutiveOnsiteDaysEnding(workedDates, ref);
            int ptStreak    = streakScore(streakDays);
            OvernightEval overnight = overnightRestEval(clockInByDate, clockOutByDate, ref);
            int ptOvernight = overnight.points();
            int ptTradeRisk = resolveTradeRiskPoints(worker);

            int rawSum = ptAccident + ptStreak + ptOvernight + ptTradeRisk;
            int capped  = Math.min(SCORE_CAP, rawSum);
            worker.replaceFatigueSnapshot(capped, capped >= HIGH_RISK_THRESHOLD,
                    ptAccident, ptStreak, ptOvernight, ptTradeRisk, now);
        }

        workerRepository.saveAll(workers);
    }

    private static int resolveTradeRiskPoints(Worker worker) {
        Trade t = Trade.classifyWorker(worker);
        return t == null ? TRADE_UNKNOWN_POINTS : t.fatigueRiskWeight();
    }

    private static String humanTradeLabel(Worker w, Trade t) {
        return w.getTrade() != null && !w.getTrade().isBlank()
                ? t.name() + "(" + w.getTrade().trim() + ")"
                : t.name();
    }

    private static String nullable(String s) {
        return s == null ? "" : s;
    }

    /**
     * referenceDate 기준으로 거꾸로 CLOCK_IN 이벤트가 연속으로 존재하는 캘린더 일수.
     *
     * <p>referenceDate 당일 출근 기록이 없는 경우(예: 어제 퇴근 후 오늘 미출근 상태로 조회),
     * 전일(referenceDate - 1)부터 카운트를 시작한다. 전일도 출근 기록이 없으면 0을 반환한다.</p>
     */
    static int consecutiveOnsiteDaysEnding(Set<LocalDate> workedDates, LocalDate referenceDate) {
        if (workedDates.isEmpty()) return 0;
        // 기준일에 출근 기록이 없으면 전일을 시작점으로 사용
        LocalDate start = workedDates.contains(referenceDate)
                ? referenceDate
                : referenceDate.minusDays(1);
        if (!workedDates.contains(start)) return 0;
        int streak = 0;
        LocalDate d = start;
        while (workedDates.contains(d)) {
            streak++;
            d = d.minusDays(1);
        }
        return streak;
    }

    /**
     * 6연속→10점, 7연속→20점, 8연속→30점, 9연속 이상→40점.
     */
    static int streakScore(int streakDays) {
        if (streakDays >= 9) return 40;
        if (streakDays >= 8) return 30;
        if (streakDays >= 7) return 20;
        if (streakDays >= 6) return 10;
        return 0;
    }

    /** 전일 퇴근 ~ 당일 출근 간격이 10시간 미만이면 과밀 휴게(30점). */
    OvernightEval overnightRestEval(Map<LocalDate, LocalTime> clockInByDate,
                                    Map<LocalDate, LocalTime> clockOutByDate,
                                    LocalDate referenceDate) {
        LocalTime clockInToday    = clockInByDate.get(referenceDate);
        LocalDate prevDate        = referenceDate.minusDays(1);
        LocalTime clockOutYesterday = clockOutByDate.get(prevDate);

        if (clockInToday == null || clockOutYesterday == null) {
            return OvernightEval.none("당일 출근 또는 전일 퇴근 시각이 비어 연속 간격 검사 불가");
        }

        Duration rest = Duration.between(prevDate.atTime(clockOutYesterday),
                                         referenceDate.atTime(clockInToday));
        boolean insufficient = rest.compareTo(RestConstants.MIN_GAP) < 0;
        String note = String.format(
                "전일(%s) 퇴근 %s → 금일(%s) 출근 %s, 휴게 %.2f시간. %s",
                prevDate, clockOutYesterday, referenceDate, clockInToday,
                rest.toMinutes() / 60.0,
                insufficient ? "10시간 미만으로 과밀 휴게(+30점)" : "10시간 이상 휴게로 해당 없음");

        return insufficient ? OvernightEval.hit(note) : OvernightEval.none(note);
    }

    private static final class RestConstants {
        static final Duration MIN_GAP = Duration.ofHours(10);
    }

    private record OvernightEval(int points, String note) {
        static OvernightEval none(String note) { return new OvernightEval(0, note); }
        static OvernightEval hit(String note)  { return new OvernightEval(30, note); }
    }
}
