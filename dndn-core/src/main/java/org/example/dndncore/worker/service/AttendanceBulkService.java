package org.example.dndncore.worker.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.example.dndncore.worker.model.entity.AttendanceLog;
import org.example.dndncore.worker.model.entity.AttendanceRecord;
import org.example.dndncore.worker.model.entity.Worker;
import org.example.dndncore.worker.model.enums.AttendanceEventType;
import org.example.dndncore.worker.model.enums.AttendanceStatus;
import org.example.dndncore.worker.model.enums.EmploymentKind;
import org.example.dndncore.worker.repository.AttendanceLogRepository;
import org.example.dndncore.worker.repository.AttendanceRecordRepository;
import org.example.dndncore.worker.repository.WorkerRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * 현장+날짜 단위 근태 일괄 변경.
 * 각 근무자마다 같은 상태 내에서 출퇴근 시각을 조금씩 달리 부여한다.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class AttendanceBulkService {

    // 출근 시각 후보 (상태별)
    private static final int[] IN_HOURS_PRESENT    = {5, 6, 7};
    private static final int[] IN_HOURS_LATE        = {8, 9};
    private static final int[] IN_HOURS_EARLY_LEAVE = {5, 6, 7};
    private static final int[] IN_HOURS_LEAVE       = {5, 6, 7, 8, 9, 10};

    // 퇴근 시각 후보
    private static final int[] OUT_HOURS_EARLY_LEAVE = {13, 14, 15};
    private static final int[] OUT_HOURS_LEAVE       = {18, 19, 20, 21};

    private final WorkerRepository workerRepository;
    private final AttendanceRecordRepository attendanceRecordRepository;
    private final AttendanceLogRepository attendanceLogRepository;
    private final FatigueCalculationService fatigueCalculationService;

    public record BulkResult(String siteCode, String date, String targetStatus, int total) {}

    @Transactional
    public BulkResult bulkOverride(String siteCode, LocalDate date, String targetStatus) {
        List<Worker> workers = workerRepository.findAllBySiteCode(siteCode);
        if (workers.isEmpty()) {
            log.warn("[일괄출결변경] siteCode={} 등록된 근로자 없음", siteCode);
            return new BulkResult(siteCode, date.toString(), targetStatus, 0);
        }

        List<Long> workerIdxes = workers.stream().map(Worker::getIdx).toList();

        // 고용구분 보존을 위해 기존 레코드 선조회
        Map<Long, AttendanceRecord> existingMap = attendanceRecordRepository
                .findAllByWorkerIdxInAndWorkDate(workerIdxes, date)
                .stream()
                .collect(Collectors.toMap(ar -> ar.getWorker().getIdx(), ar -> ar));

        attendanceRecordRepository.deleteAllByWorkerIdxInAndWorkDate(workerIdxes, date);
        attendanceRecordRepository.flush();

        List<AttendanceRecord> toSave = new ArrayList<>();
        String ts = targetStatus.toUpperCase();

        for (Worker worker : workers) {
            long wid = worker.getIdx();
            int minuteOffset = (int)((wid % 4) * 15); // 0 / 15 / 30 / 45

            AttendanceRecord prev = existingMap.get(wid);
            EmploymentKind ek = (prev != null && prev.getEmploymentKind() != null)
                    ? prev.getEmploymentKind()
                    : (worker.getEmploymentKind() != null ? worker.getEmploymentKind() : EmploymentKind.REGULAR);

            LocalTime clockIn = null;
            LocalTime clockOut = null;
            AttendanceStatus status;
            BigDecimal manDays;

            switch (ts) {
                case "PENDING" -> {
                    status = AttendanceStatus.PENDING;
                    manDays = BigDecimal.ZERO;
                }
                case "PRESENT" -> {
                    int h = IN_HOURS_PRESENT[(int)(wid % IN_HOURS_PRESENT.length)];
                    clockIn = LocalTime.of(h, minuteOffset);
                    status = AttendanceStatus.PRESENT;
                    manDays = BigDecimal.ZERO;
                }
                case "LATE" -> {
                    int h = IN_HOURS_LATE[(int)(wid % IN_HOURS_LATE.length)];
                    clockIn = LocalTime.of(h, minuteOffset);
                    status = AttendanceStatus.LATE;
                    manDays = BigDecimal.ZERO;
                }
                case "EARLY_LEAVE" -> {
                    int hi = IN_HOURS_EARLY_LEAVE[(int)(wid % IN_HOURS_EARLY_LEAVE.length)];
                    int ho = OUT_HOURS_EARLY_LEAVE[(int)(wid % OUT_HOURS_EARLY_LEAVE.length)];
                    clockIn  = LocalTime.of(hi, minuteOffset);
                    clockOut = LocalTime.of(ho, minuteOffset);
                    status = AttendanceStatus.EARLY_LEAVE;
                    manDays = new BigDecimal("0.5");
                }
                case "LEAVE" -> {
                    // 프론트 deriveAttendanceTag: PRESENT + clockOut → "퇴근"
                    int hi = IN_HOURS_LEAVE[(int)(wid % IN_HOURS_LEAVE.length)];
                    int ho = OUT_HOURS_LEAVE[(int)(wid % OUT_HOURS_LEAVE.length)];
                    clockIn  = LocalTime.of(hi, minuteOffset);
                    clockOut = LocalTime.of(ho, minuteOffset);
                    status = AttendanceStatus.PRESENT;
                    manDays = BigDecimal.ONE;
                }
                default -> throw new IllegalArgumentException("지원하지 않는 targetStatus: " + targetStatus);
            }

            toSave.add(AttendanceRecord.builder()
                    .worker(worker)
                    .workDate(date)
                    .clockIn(clockIn)
                    .clockOut(clockOut)
                    .manDays(manDays)
                    .attendanceStatus(status)
                    .employmentKind(ek)
                    .siteCode(siteCode)
                    .build());
        }

        attendanceRecordRepository.saveAll(toSave);

        // attendance_log 동기화 + 피로도 재계산
        // FatigueCalculationService는 attendance_log(CLOCK_IN) 기반으로 streak을 산정한다.
        attendanceLogRepository.deleteAllByWorkerIdxInAndWorkDate(workerIdxes, date);

        if ("PRESENT".equals(ts) || "LATE".equals(ts)) {
            // 출근/지각 → 오늘 CLOCK_IN 로그 추가 → ref=date 재계산 → streak +1(오늘 포함)
            List<AttendanceLog> logsToSave = new ArrayList<>(workers.size());
            for (AttendanceRecord record : toSave) {
                logsToSave.add(AttendanceLog.builder()
                        .workerIdx(record.getWorker().getIdx())
                        .siteCode(siteCode)
                        .workDate(date)
                        .eventType(AttendanceEventType.CLOCK_IN)
                        .recognizedAt(record.getClockIn())
                        .build());
            }
            attendanceLogRepository.saveAll(logsToSave);
            log.info("[일괄출결변경] attendance_log CLOCK_IN 저장 완료: {}건", logsToSave.size());
        } else {
            // 미출근(PENDING) 등 → 오늘 로그 삭제만 수행(위에서 완료)
            // ref=date로 재계산 시 오늘 CLOCK_IN 없으므로 streak이 어제 기준으로 줄어든다.
            log.info("[일괄출결변경] attendance_log 삭제 완료 (targetStatus={})", ts);
        }

        // 공통 피로도 재계산 — PRESENT/LATE는 오늘 포함, PENDING은 오늘 제외로 자동 반영
        fatigueCalculationService.bulkRecalculateAndPersist(workers, date);
        log.info("[일괄출결변경] 피로도 재계산 완료: siteCode={} date={}", siteCode, date);

        log.info("[일괄출결변경] siteCode={} date={} targetStatus={} total={}",
                siteCode, date, targetStatus, toSave.size());
        return new BulkResult(siteCode, date.toString(), targetStatus, toSave.size());
    }
}
