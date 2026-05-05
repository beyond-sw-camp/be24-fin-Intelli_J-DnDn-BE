package org.example.dndn.worker.service;

import lombok.RequiredArgsConstructor;
import org.example.dndn.common.exception.BaseException;
import org.example.dndn.staffing.model.Trade;
import org.example.dndn.worker.model.dto.WorkerDetailDto;
import org.example.dndn.worker.model.entity.AttendanceRecord;
import org.example.dndn.worker.model.entity.Worker;
import org.example.dndn.worker.model.enums.AttendanceStatus;
import org.example.dndn.worker.repository.AttendanceRecordRepository;
import org.example.dndn.worker.repository.SafetyAccidentRepository;
import org.example.dndn.worker.repository.WorkerRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.example.dndn.common.model.BaseResponseStatus.FAIL;

/**
 * 작업자 피로도(위험) 점수 산출 — 근태·사고·마스터 공종을 종합하여 {@link Worker}에 스냅샷 저장한다.
 *
 * <p>항목별 합이 100점을 초과할 수 있으며, 저장·고위험 판정에는 합계를 100으로 캡한 값을 사용한다.</p>
 */
@Service
@RequiredArgsConstructor
public class FatigueCalculationService {

    public static final int SCORE_CAP = 100;
    public static final int HIGH_RISK_THRESHOLD = 80;

    private static final int ACCIDENT_LAST_DAYS_WINDOW = 30;
    /** 연속 근무(출근 처리) 일수 임계: 6·7·8·9일 구간별 점수 */
    private static final int LOOKBACK_SCAN_DAYS = 120;

    private static final int PT_ACCIDENT = 20;

    private static final int TRADE_UNKNOWN_POINTS = 10;
    private static final String TRADE_UNKNOWN_KEY = "UNKNOWN";

    /** 직종별 물리·화학 위험 가중치 (5–20점, SUM 은 규격상 {@link #SCORE_CAP} 내로 캡) */
    private static final Map<Trade, Integer> TRADE_RISK_POINTS =
            Map.of(
                    Trade.TILE, 6,
                    Trade.REBAR, 14,
                    Trade.CARPENTER, 17,
                    Trade.WELDER, 20);

    private static final List<AttendanceStatus> ONSITE_STATUS =
            List.of(AttendanceStatus.PRESENT, AttendanceStatus.LATE);

    private final WorkerRepository workerRepository;
    private final AttendanceRecordRepository attendanceRepository;
    private final SafetyAccidentRepository accidentRepository;

    /** 상세 프로필 조회 시 등 재계산 + DB 반영 후 응답용 DTO 를 돌려준다. */
    @Transactional
    public WorkerDetailDto.FatigueSummaryRes recalculateAndPersist(Long workerIdx, LocalDate referenceDate) {
        LocalDate ref = referenceDate != null ? referenceDate : LocalDate.now();
        Worker worker = workerRepository.findById(workerIdx)
                .orElseThrow(() -> new BaseException(FAIL));

        LocalDate accidentFrom = ref.minusDays(ACCIDENT_LAST_DAYS_WINDOW);
        boolean accidentInWindow =
                accidentRepository.existsByWorkerIdxAndOccurredAtBetween(workerIdx, accidentFrom, ref);
        int ptAccident = accidentInWindow ? PT_ACCIDENT : 0;

        LocalDate attendanceFrom = ref.minusDays(LOOKBACK_SCAN_DAYS);
        List<AttendanceRecord> attendanceRows =
                attendanceRepository.findAllByWorkerIdxAndWorkDateBetweenOrderByWorkDateDesc(
                        workerIdx, attendanceFrom, ref);
        Map<LocalDate, AttendanceRecord> byDate = new HashMap<>(attendanceRows.size());
        for (AttendanceRecord a : attendanceRows) {
            byDate.put(a.getWorkDate(), a);
        }

        int streakDays = consecutiveOnsiteDaysEnding(byDate, ref);
        int ptStreak = streakScore(streakDays);

        OvernightEval overnightEval = overnightRestEval(byDate, ref);
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
                    case 10 -> streakDays + "일 연속 출근 처리 → 연속 근무 " + streakDays + "일 구간 적용(+10점)";
                    case 20 -> streakDays + "일 연속 출근 처리 → 연속 근무 " + streakDays + "일 구간 적용(+20점)";
                    case 30 -> streakDays + "일 연속 출근 처리 → 연속 근무 " + streakDays + "일 구간 적용(+30점)";
                    case 40 -> streakDays + "일 연속 출근 처리 → 연속 근무 " + streakDays + "일 구간 적용(+40점)";
                    default -> streakDays <= 5
                            ? "연속 근무 " + streakDays + "일(출근:PRESENT/LATE)·6일 미만이라 가점 없음"
                            : streakDays + "일 연속 출근 처리되었지만 구간 규칙에 해당하지 않습니다.";
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
                    "목공·철근·용접·타일 문자열 미매칭 — 기본("
                            + TRADE_UNKNOWN_POINTS
                            + "점)·subLabel="
                            + nullable(worker.getSubLabel());
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

    private static int resolveTradeRiskPoints(Worker worker) {
        Trade t = Trade.classifyWorker(worker);
        if (t == null) {
            return TRADE_UNKNOWN_POINTS;
        }
        return TRADE_RISK_POINTS.getOrDefault(t, TRADE_UNKNOWN_POINTS);
    }

    /** 한글 라벨·Trade 매칭 설명 보조용 */
    private static String humanTradeLabel(Worker w, Trade t) {
        return w.getSubLabel() != null && !w.getSubLabel().isBlank()
                ? t.name() + "(" + w.getSubLabel().trim() + ")"
                : t.name();
    }

    private static String nullable(String s) {
        return s == null ? "" : s;
    }

    private static boolean onsite(AttendanceRecord a) {
        return a != null && a.getAttendanceStatus() != null && ONSITE_STATUS.contains(a.getAttendanceStatus());
    }

    /** {@code referenceDate} 포함 거꾸로 연속해서 {@link #ONSITE_STATUS} 근무로 이어진 캘린더 일수 */
    static int consecutiveOnsiteDaysEnding(Map<LocalDate, AttendanceRecord> byDate, LocalDate referenceDate) {
        int streak = 0;
        LocalDate d = referenceDate;
        while (true) {
            AttendanceRecord ar = byDate.get(d);
            if (!onsite(ar)) {
                break;
            }
            streak++;
            d = d.minusDays(1);
        }
        return streak;
    }

    /**
     * 6연속→10점, 7연속→20점, 8연속 이상→30점, 9연속 이상→40점(상위 구간 우선 적용하지 않음: 가장 높은 구간 하나만 선택).
     */
    static int streakScore(int streakDays) {
        if (streakDays >= 9) {
            return 40;
        }
        if (streakDays >= 8) {
            return 30;
        }
        if (streakDays >= 7) {
            return 20;
        }
        if (streakDays >= 6) {
            return 10;
        }
        return 0;
    }

    /** 전일 퇴근 시각부터 당일 출근 시각까지 휴식이 {@code REST_MIN_HOURS} 미만이면 근무 연속 과밀로 간주(30점). */
    OvernightEval overnightRestEval(Map<LocalDate, AttendanceRecord> byDate, LocalDate referenceDate) {
        Optional<AttendanceRecord> curr = Optional.ofNullable(byDate.get(referenceDate));
        LocalDate prevDate = referenceDate.minusDays(1);
        Optional<AttendanceRecord> prev = Optional.ofNullable(byDate.get(prevDate));

        if (curr.isEmpty() || prev.isEmpty()) {
            return OvernightEval.none("당일 또는 전일 근태 행‧시각 미존재로 휴게 간격 판단 생략");
        }
        AttendanceRecord today = curr.get();
        AttendanceRecord yesterday = prev.get();
        LocalTime clockInToday = today.getClockIn();
        LocalTime clockOutYesterday = yesterday.getClockOut();
        if (clockInToday == null || clockOutYesterday == null) {
            return OvernightEval.none("당일 출근 또는 전일 퇴근 시각이 비어 연속 간격 검사 불가");
        }

        LocalDateTime endPrevShift = yesterday.getWorkDate().atTime(clockOutYesterday);
        LocalDateTime startCurrShift = today.getWorkDate().atTime(clockInToday);

        Duration rest = Duration.between(endPrevShift, startCurrShift);
        boolean insufficient = rest.compareTo(RestConstants.MIN_GAP) < 0;
        String note =
                String.format(
                        "전일(%s) 퇴근 %s → 금일(%s) 출근 %s, 휴게 %.2f시간. %s",
                        yesterday.getWorkDate(),
                        clockOutYesterday,
                        today.getWorkDate(),
                        clockInToday,
                        rest.toMinutes() / 60.0,
                        insufficient
                                ? "10시간 미만으로 과밀 휴게(+30점)"
                                : "10시간 이상 휴게로 해당 없음");

        return insufficient ? OvernightEval.hit(note) : OvernightEval.none(note);
    }

    /** 밤샘·근접 교대 과밀 휴게 판별용 상수 */
    private static final class RestConstants {
        static final Duration MIN_GAP = Duration.ofHours(10);
    }

    private record OvernightEval(int points, String note) {

        static OvernightEval none(String note) {
            return new OvernightEval(0, note);
        }

        static OvernightEval hit(String note) {
            return new OvernightEval(30, note);
        }
    }
}
