package org.example.dndncore.worker.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.example.dndncore.staffing.model.StaffingLog;
import org.example.dndncore.staffing.model.ZoneMain;
import org.example.dndncore.staffing.model.ZoneSub;
import org.example.dndncore.staffing.repository.StaffingLogRepository;
import org.example.dndncore.staffing.repository.ZoneMainRepository;
import org.example.dndncore.worker.model.entity.AttendanceLog;
import org.example.dndncore.worker.model.entity.SafetyAccident;
import org.example.dndncore.worker.model.entity.Worker;
import org.example.dndncore.worker.model.enums.AttendanceEventType;
import org.example.dndncore.worker.repository.AttendanceLogRepository;
import org.example.dndncore.worker.repository.SafetyAccidentRepository;
import org.example.dndncore.worker.repository.WorkerRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * 더미 출결 시딩 서비스.
 * 과거 출결 이벤트는 {@code attendance_log}에만 기록한다.
 * {@code attendance_record}는 당일 로스터 스냅샷 전용이므로 시딩 대상에서 제외한다.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class AttendanceSeedService {

    private static final int SEED_DAYS_BACK = 10;

    private static final int[][] DAY_PATTERNS = {
            {1, 2, 5},
            {1, 2, 3},
            {1, 2, 3, 4, 5, 6},
            {1, 2, 3, 4, 5, 6, 7},
            {1, 2, 3, 4, 5, 6, 7, 8},
            {1, 2, 3, 4, 5, 6, 7, 8, 9},
    };

    private static final LocalTime[] NORMAL_INS  = {
            LocalTime.of(7, 30), LocalTime.of(8, 0), LocalTime.of(8, 30), LocalTime.of(9, 0)
    };
    private static final LocalTime[] NORMAL_OUTS = {
            LocalTime.of(17, 0), LocalTime.of(17, 30), LocalTime.of(18, 0), LocalTime.of(18, 30)
    };

    private static final LocalTime OVERNIGHT_EARLY_IN  = LocalTime.of(6, 0);
    private static final LocalTime OVERNIGHT_LATE_OUT  = LocalTime.of(23, 0);

    private static final String[][] ZONE_TABLE_FALLBACK = {
            {"A구역", "1공구"},
            {"A구역", "2공구"},
            {"B구역", "1공구"},
            {"B구역", "2공구"},
            {"C구역", "1공구"},
            {"C구역", "2공구"},
    };

    private record ZoneEntry(String mainTitle, String subTitle, String tradeName) {}

    private final WorkerRepository workerRepository;
    private final AttendanceLogRepository attendanceLogRepository;
    private final SafetyAccidentRepository accidentRepository;
    private final StaffingLogRepository staffingLogRepository;
    private final ZoneMainRepository zoneMainRepository;
    private final FatigueCalculationService fatigueCalculationService;

    /** records 필드는 하위 호환용 — 항상 0 (과거 record 시딩 안 함) */
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

        attendanceLogRepository.deleteAllByWorkerIdxInAndWorkDateBetween(workerIdxes, seedFrom, yesterday);
        staffingLogRepository.deleteAllByWorkerIdxInAndWorkDateBetween(workerIdxes, seedFrom, yesterday);

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

            LocalTime normalIn  = NORMAL_INS[clockGroup];
            LocalTime normalOut = NORMAL_OUTS[clockGroup];

            for (int daysAgo : pattern) {
                LocalDate workDate = today.minusDays(daysAgo);
                if (workDate.isBefore(seedFrom)) continue;

                LocalTime clockIn;
                LocalTime clockOut;
                if (shortOvernight && daysAgo == 1) {
                    clockIn  = OVERNIGHT_EARLY_IN;
                    clockOut = normalOut;
                } else if (shortOvernight && daysAgo == 2) {
                    clockIn  = normalIn;
                    clockOut = OVERNIGHT_LATE_OUT;
                } else {
                    clockIn  = normalIn;
                    clockOut = normalOut;
                }

                ZoneEntry zone = zoneEntries.get((int)((wid + daysAgo) % zoneEntries.size()));

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

        if (!logsToSave.isEmpty()) attendanceLogRepository.saveAll(logsToSave);
        if (!staffingLogsToSave.isEmpty()) staffingLogRepository.saveAll(staffingLogsToSave);
        int accidentCount = accidentsToSave.size();
        if (!accidentsToSave.isEmpty()) accidentRepository.saveAll(accidentsToSave);

        fatigueCalculationService.bulkRecalculateAndPersist(workers, yesterday);

        log.info("[더미시딩] siteCode={} workers={} logs={} staffingLogs={} accidents={} (attendance_record 미사용)",
                siteCode, workers.size(), logsToSave.size(), staffingLogsToSave.size(), accidentCount);
        return new SeedResult(siteCode, workers.size(), 0,
                logsToSave.size(), staffingLogsToSave.size(), accidentCount);
    }

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
}
