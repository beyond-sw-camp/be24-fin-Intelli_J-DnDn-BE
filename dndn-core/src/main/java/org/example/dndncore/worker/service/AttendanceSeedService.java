package org.example.dndncore.worker.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.example.dndncore.staffing.model.StaffingLog;
import org.example.dndncore.staffing.model.ZoneMain;
import org.example.dndncore.staffing.model.ZoneSub;
import org.example.dndncore.staffing.repository.StaffingLogRepository;
import org.example.dndncore.staffing.repository.ZoneMainRepository;
import org.example.dndncore.worker.model.entity.AttendanceLog;
import org.example.dndncore.worker.model.entity.AttendanceRecord;
import org.example.dndncore.worker.model.entity.SafetyAccident;
import org.example.dndncore.worker.model.entity.Worker;
import org.example.dndncore.worker.model.enums.AttendanceEventType;
import org.example.dndncore.worker.model.enums.AttendanceStatus;
import org.example.dndncore.worker.model.enums.EmploymentKind;
import org.example.dndncore.worker.repository.AttendanceLogRepository;
import org.example.dndncore.worker.repository.AttendanceRecordRepository;
import org.example.dndncore.worker.repository.SafetyAccidentRepository;
import org.example.dndncore.worker.repository.WorkerRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * 더미 출결 시딩 서비스.
 * 근무자별 idx에 따라 최근 7일(어제~7일 전) 내 2~4일치 출결·구역배치 이력을 생성하고 피로도를 재산정한다.
 * 오늘(당일) 데이터는 건드리지 않는다.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class AttendanceSeedService {

    // 최대 탐색 범위 — 9일 전까지 (오늘은 배치/게이트가 관리, 시딩 대상 아님)
    private static final int SEED_DAYS_BACK = 10;

    // 근무자별 출근 패턴 (daysAgo 기준, 1=어제 ~ 9=9일전)
    // idx % 6으로 배분 — ref = 어제(D-1) 기준 연속 일수 → streak 점수
    //   group 0: 어제+2일전+5일전       → streak=2, 0pt
    //   group 1: 어제~3일전             → streak=3, 0pt
    //   group 2: 어제~6일전             → streak=6, 10pt
    //   group 3: 어제~7일전             → streak=7, 20pt
    //   group 4: 어제~8일전             → streak=8, 30pt
    //   group 5: 어제~9일전             → streak=9, 40pt  ← 고위험 시나리오 핵심
    private static final int[][] DAY_PATTERNS = {
            {1, 2, 5},                                 // 0: 분산
            {1, 2, 3},                                 // 1: 3일 연속
            {1, 2, 3, 4, 5, 6},                       // 2: 6일 연속 → 10pt
            {1, 2, 3, 4, 5, 6, 7},                   // 3: 7일 연속 → 20pt
            {1, 2, 3, 4, 5, 6, 7, 8},               // 4: 8일 연속 → 30pt
            {1, 2, 3, 4, 5, 6, 7, 8, 9},           // 5: 9일 연속 → 40pt
    };

    // 정상 출퇴근 시각 그룹 (idx % 4) — 근무자마다 다른 루틴
    private static final LocalTime[] NORMAL_INS  = {
            LocalTime.of(7, 30), LocalTime.of(8, 0), LocalTime.of(8, 30), LocalTime.of(9, 0)
    };
    private static final LocalTime[] NORMAL_OUTS = {
            LocalTime.of(17, 0), LocalTime.of(17, 30), LocalTime.of(18, 0), LocalTime.of(18, 30)
    };

    // 야간 휴게 부족 시나리오: daysAgo=1(어제) 이른 출근 + daysAgo=2(2일전) 늦은 퇴근
    // ref=어제 기준 overnight 점검: clockIn[어제]=6:00, clockOut[2일전]=23:00 → gap=7h < 10h → +30pt
    private static final LocalTime OVERNIGHT_EARLY_IN  = LocalTime.of(6, 0);
    private static final LocalTime OVERNIGHT_LATE_OUT  = LocalTime.of(23, 0);

    // 구역 정보를 DB에서 찾지 못할 때 사용하는 폴백
    private static final String[][] ZONE_TABLE_FALLBACK = {
            {"A구역", "1공구"},
            {"A구역", "2공구"},
            {"B구역", "1공구"},
            {"B구역", "2공구"},
            {"C구역", "1공구"},
            {"C구역", "2공구"},
    };

    private record ZoneEntry(String mainTitle, String subTitle, String tradeName) {}

    private List<ZoneEntry> loadZones(String siteCode) {
        List<ZoneMain> zoneMains = zoneMainRepository.findAllByProject_NameContainingOrderByDisplayOrderAsc(siteCode);
        List<ZoneEntry> entries = new ArrayList<>();
        for (ZoneMain zm : zoneMains) {
            for (ZoneSub zs : zm.getZoneSubs()) {
                entries.add(new ZoneEntry(zm.getTitle(), zs.getTitle(),
                        zs.getTradeName() != null ? zs.getTradeName() : "미지정"));
            }
        }
        if (entries.isEmpty()) {
            log.warn("[더미시딩] siteCode={} 등록된 구역 없음 — 폴백 구역 사용", siteCode);
            for (String[] row : ZONE_TABLE_FALLBACK) {
                entries.add(new ZoneEntry(row[0], row[1], "미지정"));
            }
        }
        return entries;
    }

    private final WorkerRepository workerRepository;
    private final AttendanceRecordRepository attendanceRecordRepository;
    private final AttendanceLogRepository attendanceLogRepository;
    private final SafetyAccidentRepository accidentRepository;
    private final StaffingLogRepository staffingLogRepository;
    private final ZoneMainRepository zoneMainRepository;
    private final FatigueCalculationService fatigueCalculationService;

    public record SeedResult(String siteCode, int workers, int records, int logs, int staffingLogs, int accidents) {}

    @Transactional
    public SeedResult seedDemoHistory(String siteCode) {
        LocalDate today = LocalDate.now();
        LocalDate yesterday = today.minusDays(1);
        LocalDate seedFrom = today.minusDays(SEED_DAYS_BACK);

        List<Worker> workers = workerRepository.findAllBySiteCode(siteCode);
        if (workers.isEmpty()) {
            log.warn("[더미시딩] siteCode={} 등록된 근로자 없음", siteCode);
            return new SeedResult(siteCode, 0, 0, 0, 0, 0);
        }

        List<ZoneEntry> zoneEntries = loadZones(siteCode);

        List<Long> workerIdxes = workers.stream().map(Worker::getIdx).toList();

        // 기존 시딩 데이터 삭제 (어제까지) — 오늘 레코드는 배치/게이트가 관리하므로 건드리지 않음
        attendanceRecordRepository.deleteAllByWorkerIdxInAndWorkDateBetween(workerIdxes, seedFrom, yesterday);
        attendanceLogRepository.deleteAllByWorkerIdxInAndWorkDateBetween(workerIdxes, seedFrom, yesterday);
        staffingLogRepository.deleteAllByWorkerIdxInAndWorkDateBetween(workerIdxes, seedFrom, yesterday);

        // 사고 중복 방지를 위한 bulk pre-query (루프 N+1 제거)
        LocalDate accidentDate15 = today.minusDays(15);
        LocalDate accidentDate22 = today.minusDays(22);
        List<Long> acc15Candidates = workers.stream()
                .map(Worker::getIdx).filter(wid -> wid % 4 == 0).toList();
        List<Long> acc22Candidates = workers.stream()
                .map(Worker::getIdx).filter(wid -> wid % 7 == 1).toList();
        Set<Long> existingAcc15 = acc15Candidates.isEmpty() ? Set.of()
                : new HashSet<>(accidentRepository.findWorkerIdxesWithAccidentOnDate(
                        acc15Candidates, accidentDate15, "낙하물"));
        Set<Long> existingAcc22 = acc22Candidates.isEmpty() ? Set.of()
                : new HashSet<>(accidentRepository.findWorkerIdxesWithAccidentOnDate(
                        acc22Candidates, accidentDate22, "끼임"));

        List<AttendanceRecord> recordsToSave = new ArrayList<>();
        List<AttendanceLog> logsToSave = new ArrayList<>();
        List<StaffingLog> staffingLogsToSave = new ArrayList<>();
        List<SafetyAccident> accidentsToSave = new ArrayList<>();

        for (Worker worker : workers) {
            long wid = worker.getIdx();
            int[] pattern  = DAY_PATTERNS[(int)(wid % 6)];
            int clockGroup = (int)(wid % 4);
            boolean shortOvernight = wid % 3 == 0;

            boolean accident15 = wid % 4 == 0;
            boolean accident22 = wid % 7 == 1;

            EmploymentKind ek = worker.getEmploymentKind() != null
                    ? worker.getEmploymentKind() : EmploymentKind.REGULAR;

            LocalTime normalIn  = NORMAL_INS[clockGroup];
            LocalTime normalOut = NORMAL_OUTS[clockGroup];

            for (int daysAgo : pattern) {
                LocalDate workDate = today.minusDays(daysAgo);
                if (workDate.isBefore(seedFrom)) continue;

                LocalTime clockIn;
                LocalTime clockOut;
                if (shortOvernight && daysAgo == 1) {
                    // 어제: 이른 출근 — ref=어제 overnight 점검의 clockIn 기준점
                    clockIn  = OVERNIGHT_EARLY_IN;
                    clockOut = normalOut;
                } else if (shortOvernight && daysAgo == 2) {
                    // 2일전: 늦은 퇴근 — 어제 이른 출근과 쌍으로 10시간 미만 휴게 성립
                    clockIn  = normalIn;
                    clockOut = OVERNIGHT_LATE_OUT;
                } else {
                    clockIn  = normalIn;
                    clockOut = normalOut;
                }

                ZoneEntry zone = zoneEntries.get((int)((wid + daysAgo) % zoneEntries.size()));

                recordsToSave.add(AttendanceRecord.builder()
                        .worker(worker)
                        .workDate(workDate)
                        .clockIn(clockIn)
                        .clockOut(clockOut)
                        .manDays(new BigDecimal("1.0"))
                        .attendanceStatus(AttendanceStatus.PRESENT)
                        .employmentKind(ek)
                        .siteCode(worker.getSiteCode())
                        .build());

                logsToSave.add(AttendanceLog.builder()
                        .workerIdx(wid)
                        .siteCode(worker.getSiteCode())
                        .workDate(workDate)
                        .eventType(AttendanceEventType.CLOCK_IN)
                        .recognizedAt(clockIn)
                        .build());
                logsToSave.add(AttendanceLog.builder()
                        .workerIdx(wid)
                        .siteCode(worker.getSiteCode())
                        .workDate(workDate)
                        .eventType(AttendanceEventType.CLOCK_OUT)
                        .recognizedAt(clockOut)
                        .build());

                staffingLogsToSave.add(StaffingLog.builder()
                        .workerIdx(wid)
                        .workDate(workDate)
                        .zoneMainTitle(zone.mainTitle())
                        .zoneSubTitle(zone.subTitle())
                        .tradeName(!"미지정".equals(zone.tradeName()) ? zone.tradeName()
                                : (worker.getTrade() != null ? worker.getTrade() : "미지정"))
                        .siteCode(worker.getSiteCode())
                        .build());
            }

            // 사고 이력 — bulk pre-query 결과로 중복 건너뜀
            if (accident15 && !existingAcc15.contains(wid)) {
                ZoneEntry az15 = zoneEntries.get((int)(wid % zoneEntries.size()));
                accidentsToSave.add(SafetyAccident.builder()
                        .worker(worker)
                        .occurredAt(accidentDate15)
                        .accidentType("낙하물")
                        .zoneMain(az15.mainTitle())
                        .zoneSub(null)
                        .resolution("안전모 착용 지도 완료")
                        .build());
            }
            if (accident22 && !existingAcc22.contains(wid)) {
                ZoneEntry az22 = zoneEntries.get((int)((wid + 1) % zoneEntries.size()));
                accidentsToSave.add(SafetyAccident.builder()
                        .worker(worker)
                        .occurredAt(accidentDate22)
                        .accidentType("끼임")
                        .zoneMain(az22.mainTitle())
                        .zoneSub(null)
                        .resolution("안전 교육 이수 완료")
                        .build());
            }
        }

        attendanceRecordRepository.saveAll(recordsToSave);
        if (!logsToSave.isEmpty()) attendanceLogRepository.saveAll(logsToSave);
        if (!staffingLogsToSave.isEmpty()) staffingLogRepository.saveAll(staffingLogsToSave);
        int accidentCount = accidentsToSave.size();
        if (!accidentsToSave.isEmpty()) accidentRepository.saveAll(accidentsToSave);

        // 피로도 재산정 — 어제 기준 (오늘 데이터 없으므로 ref=yesterday)
        fatigueCalculationService.bulkRecalculateAndPersist(workers, yesterday);

        log.info("[더미시딩] siteCode={} workers={} records={} logs={} staffingLogs={} accidents={}",
                siteCode, workers.size(), recordsToSave.size(), logsToSave.size(),
                staffingLogsToSave.size(), accidentCount);
        return new SeedResult(siteCode, workers.size(), recordsToSave.size(),
                logsToSave.size(), staffingLogsToSave.size(), accidentCount);
    }
}
