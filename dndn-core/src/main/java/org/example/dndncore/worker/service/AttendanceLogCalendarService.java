package org.example.dndncore.worker.service;

import lombok.RequiredArgsConstructor;
import org.example.dndncore.staffing.model.StaffingLog;
import org.example.dndncore.staffing.repository.StaffingLogRepository;
import org.example.dndncore.worker.config.ManagementAttendanceProperties;
import org.example.dndncore.worker.model.dto.WorkerDetailDto;
import org.example.dndncore.worker.model.entity.AttendanceLog;
import org.example.dndncore.worker.model.entity.AttendanceRecord;
import org.example.dndncore.worker.model.enums.AttendanceEventType;
import org.example.dndncore.worker.model.enums.AttendanceStatus;
import org.example.dndncore.worker.model.enums.EmploymentKind;
import org.example.dndncore.worker.repository.AttendanceLogRepository;
import org.example.dndncore.worker.repository.AttendanceRecordRepository;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.TreeSet;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * {@code attendance_log} 이벤트를 월별 출결 캘린더 DTO로 변환한다.
 * {@code attendance_record}는 당일 로스터 스냅샷 전용 — 조회 월에 오늘이 포함되면 당일 행만 record를 우선한다.
 */
@Service
@RequiredArgsConstructor
public class AttendanceLogCalendarService {

    private final AttendanceLogRepository attendanceLogRepository;
    private final AttendanceRecordRepository attendanceRecordRepository;
    private final StaffingLogRepository staffingLogRepository;
    private final ManagementAttendanceProperties attendanceProps;

    public List<WorkerDetailDto.AttendanceRes> buildMonthlyCalendar(
            Long workerIdx, LocalDate from, LocalDate to, EmploymentKind defaultEmploymentKind) {

        List<AttendanceLog> logs =
                attendanceLogRepository.findAllByWorkerIdxAndWorkDateBetween(workerIdx, from, to);

        Map<LocalDate, LocalTime> clockInByDate = new HashMap<>();
        Map<LocalDate, LocalTime> clockOutByDate = new HashMap<>();
        for (AttendanceLog log : logs) {
            LocalDate d = log.getWorkDate();
            if (log.getEventType() == AttendanceEventType.CLOCK_IN) {
                clockInByDate.merge(d, log.getRecognizedAt(), (a, b) -> a.isBefore(b) ? a : b);
            } else if (log.getEventType() == AttendanceEventType.CLOCK_OUT) {
                clockOutByDate.merge(d, log.getRecognizedAt(), (a, b) -> a.isAfter(b) ? a : b);
            }
        }

        Map<LocalDate, StaffingLog> zoneByDate = staffingLogRepository
                .findAllByWorkerIdxAndWorkDateBetween(workerIdx, from, to)
                .stream()
                .collect(Collectors.toMap(StaffingLog::getWorkDate, Function.identity(),
                        (existing, newer) -> existing));

        LocalDate today = LocalDate.now();
        Optional<AttendanceRecord> todayRecord =
                attendanceRecordRepository.findByWorkerIdxAndWorkDate(workerIdx, today);

        TreeSet<LocalDate> dates = new TreeSet<>(clockInByDate.keySet());
        if (todayRecord.isPresent() && !today.isBefore(from) && !today.isAfter(to)) {
            dates.add(today);
        }

        EmploymentKind fallbackEk = defaultEmploymentKind != null
                ? defaultEmploymentKind : EmploymentKind.REGULAR;

        List<WorkerDetailDto.AttendanceRes> result = new ArrayList<>();
        for (LocalDate d : dates) {
            StaffingLog sl = zoneByDate.get(d);
            String zoneMain = sl != null ? sl.getZoneMainTitle() : null;
            String zoneSub = sl != null ? sl.getZoneSubTitle() : null;

            if (d.equals(today) && todayRecord.isPresent()) {
                result.add(WorkerDetailDto.AttendanceRes.from(todayRecord.get(), zoneMain, zoneSub));
                continue;
            }

            LocalTime clockIn = clockInByDate.get(d);
            LocalTime clockOut = clockOutByDate.get(d);
            if (clockIn == null) {
                continue;
            }

            AttendanceStatus status = deriveStatusFromLog(clockIn, clockOut);
            BigDecimal manDays = deriveManDays(status, clockIn, clockOut);

            result.add(WorkerDetailDto.AttendanceRes.builder()
                    .date(d)
                    .clockIn(clockIn)
                    .clockOut(clockOut)
                    .employmentKind(fallbackEk)
                    .attendanceStatus(status)
                    .manDays(manDays)
                    .zoneMain(zoneMain)
                    .zoneSub(zoneSub)
                    .zoneDisplay(WorkerDetailDto.formatZoneLine(zoneMain, zoneSub))
                    .build());
        }

        result.sort(Comparator.comparing(WorkerDetailDto.AttendanceRes::getDate).reversed());
        return result;
    }

    private AttendanceStatus deriveStatusFromLog(LocalTime clockIn, LocalTime clockOut) {
        LocalTime lateDeadline = attendanceProps.getOfficialStart()
                .plusMinutes(attendanceProps.getLateGraceMinutes());

        if (clockOut == null) {
            return clockIn.isAfter(lateDeadline) ? AttendanceStatus.LATE : AttendanceStatus.PRESENT;
        }
        if (clockOut.isBefore(attendanceProps.getOfficialEnd())) {
            return AttendanceStatus.EARLY_LEAVE;
        }
        if (clockIn.isAfter(lateDeadline)) {
            return AttendanceStatus.LATE;
        }
        return AttendanceStatus.PRESENT;
    }

    private static BigDecimal deriveManDays(AttendanceStatus status, LocalTime clockIn, LocalTime clockOut) {
        if (status == AttendanceStatus.EARLY_LEAVE) {
            return new BigDecimal("0.5");
        }
        if (clockOut != null && clockIn != null) {
            return BigDecimal.ONE;
        }
        return BigDecimal.ZERO;
    }
}
