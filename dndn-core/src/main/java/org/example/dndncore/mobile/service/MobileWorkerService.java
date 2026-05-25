package org.example.dndncore.mobile.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.example.dndncore.auth.security.JwtProvider;
import org.example.dndncore.common.exception.BaseException;
import org.example.dndncore.mobile.dto.MobileAuthDto;
import org.example.dndncore.mobile.dto.MobileWorkerDto;
import org.example.dndncore.sse.SseEmitterRegistry;
import org.example.dndncore.staffing.model.StaffingAssignment;
import org.example.dndncore.staffing.model.StaffingLog;
import org.example.dndncore.staffing.repository.StaffingAssignmentRepository;
import org.example.dndncore.staffing.repository.StaffingLogRepository;
import org.example.dndncore.worker.config.ManagementAttendanceProperties;
import org.example.dndncore.worker.model.entity.AttendanceRecord;
import org.example.dndncore.worker.model.entity.AttendanceLog;
import org.example.dndncore.worker.model.entity.Worker;
import org.example.dndncore.worker.model.enums.AttendanceEventType;
import org.example.dndncore.worker.model.enums.AttendanceStatus;
import org.example.dndncore.worker.repository.AttendanceLogRepository;
import org.example.dndncore.worker.repository.AttendanceRecordRepository;
import org.example.dndncore.worker.repository.WorkerRepository;
import org.example.dndncore.worker.service.FatigueCalculationService;
import org.example.dndncore.worker.service.WorkerDetailService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZoneId;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import static org.example.dndncore.common.model.BaseResponseStatus.MOBILE_WORKER_INVALID_CREDENTIALS;
import static org.example.dndncore.common.model.BaseResponseStatus.MOBILE_WORKER_NOT_ROSTERED;
import static org.example.dndncore.common.model.BaseResponseStatus.WORKER_ATTENDANCE_NOT_FOUND;
import static org.example.dndncore.common.model.BaseResponseStatus.WORKER_CLOCK_IN_REQUIRED;
import static org.example.dndncore.common.model.BaseResponseStatus.WORKER_NOT_FOUND;

@Slf4j
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class MobileWorkerService {

    private static final ZoneId KST = ZoneId.of("Asia/Seoul");

    private final WorkerRepository workerRepository;
    private final AttendanceRecordRepository attendanceRepository;
    private final AttendanceLogRepository attendanceLogRepository;
    private final StaffingAssignmentRepository staffingAssignmentRepository;
    private final StaffingLogRepository staffingLogRepository;
    private final JwtProvider jwtProvider;
    private final ManagementAttendanceProperties attendanceProps;
    private final FatigueCalculationService fatigueCalculationService;
    private final WorkerDetailService workerDetailService;
    private final SseEmitterRegistry sseEmitterRegistry;

    // ─────────────────────────────────────────────────
    // POST /mobile/auth/login
    // ─────────────────────────────────────────────────

    @Transactional
    public MobileAuthDto.LoginRes login(String name, String phone) {
        String trimmedName = name == null ? "" : name.trim();
        String trimmedPhone = phone == null ? "" : phone.trim();
        String phoneDigits = trimmedPhone.replaceAll("\\D", "");

        Worker worker = workerRepository.findByNameAndPhone(trimmedName, trimmedPhone, phoneDigits)
                .orElseThrow(() -> new BaseException(MOBILE_WORKER_INVALID_CREDENTIALS));

        // 오늘 근무 명단 여부 확인 (요구사항 1)
        LocalDate today = LocalDate.now(KST);
        AttendanceRecord todayRecord = attendanceRepository
                .findByWorkerIdxAndWorkDate(worker.getIdx(), today)
                .orElseThrow(() -> new BaseException(MOBILE_WORKER_NOT_ROSTERED));

        String token = jwtProvider.generateWorkerToken(worker.getIdx(), worker.getName());
        return MobileAuthDto.LoginRes.builder()
                .accessToken(token)
                .workerIdx(worker.getIdx())
                .name(worker.getName())
                .siteName(worker.getSite())
                .siteCode(worker.getSiteCode())
                .jobType(worker.getTrade())
                .jobRank(worker.getJobRank() != null ? worker.getJobRank().name() : null)
                .affiliationKind(worker.getAffiliationKind() != null ? worker.getAffiliationKind().name() : null)
                .employmentKind(todayRecord.getEmploymentKind() != null ? todayRecord.getEmploymentKind().name() : null)
                .phoneNumber(worker.getPhone())
                .emergencyContact(worker.getEmergencyPhone())
                .emergencyRelation(worker.getEmergencyRelation())
                .bloodType(worker.getBloodType())
                .profileImageUrl(worker.getProfileImageUrl())
                .attendanceStatus(todayRecord.getAttendanceStatus() != null
                        ? todayRecord.getAttendanceStatus().name()
                        : AttendanceStatus.ABSENT.name())
                .build();
    }

    // ─────────────────────────────────────────────────
    // GET /mobile/worker/profile
    // ─────────────────────────────────────────────────

    public MobileWorkerDto.ProfileRes getProfile(Long workerIdx) {
        Worker worker = findWorker(workerIdx);
        LocalDate today = LocalDate.now(KST);
        AttendanceRecord todayRecord = attendanceRepository
                .findByWorkerIdxAndWorkDate(workerIdx, today)
                .orElse(null);

        String employmentKind = (todayRecord != null && todayRecord.getEmploymentKind() != null)
                ? todayRecord.getEmploymentKind().name()
                : (worker.getEmploymentKind() != null ? worker.getEmploymentKind().name() : null);
        String attendanceStatus = (todayRecord != null && todayRecord.getAttendanceStatus() != null)
                ? todayRecord.getAttendanceStatus().name()
                : AttendanceStatus.ABSENT.name();

        return MobileWorkerDto.ProfileRes.builder()
                .workerIdx(worker.getIdx())
                .name(worker.getName())
                .siteName(worker.getSite())
                .siteCode(worker.getSiteCode())
                .jobType(worker.getTrade())
                .jobRank(worker.getJobRank() != null ? worker.getJobRank().name() : null)
                .affiliationKind(worker.getAffiliationKind() != null ? worker.getAffiliationKind().name() : null)
                .employmentKind(employmentKind)
                .phoneNumber(worker.getPhone())
                .emergencyContact(worker.getEmergencyPhone())
                .emergencyRelation(worker.getEmergencyRelation())
                .bloodType(worker.getBloodType())
                .profileImageUrl(worker.getProfileImageUrl())
                .attendanceStatus(attendanceStatus)
                .build();
    }

    // ─────────────────────────────────────────────────
    // GET /mobile/worker/today
    // ─────────────────────────────────────────────────

    public MobileWorkerDto.TodayRes getToday(Long workerIdx) {
        ensureExists(workerIdx);
        LocalDate today = LocalDate.now(KST);

        AttendanceRecord record = attendanceRepository
                .findByWorkerIdxAndWorkDate(workerIdx, today)
                .orElse(null);

        MobileWorkerDto.PlacementRes placement = resolvePlacement(workerIdx, today);

        MobileWorkerDto.AttendanceSnapshotRes attendanceSnap = null;
        boolean canClockIn = false;
        boolean canClockOut = false;

        if (record != null) {
            String clockIn = record.getClockIn() != null ? record.getClockIn().toString().substring(0, 5) : null;
            String clockOut = record.getClockOut() != null ? record.getClockOut().toString().substring(0, 5) : null;
            attendanceSnap = MobileWorkerDto.AttendanceSnapshotRes.builder()
                    .attendanceStatus(record.getAttendanceStatus() != null
                            ? record.getAttendanceStatus().name()
                            : AttendanceStatus.PENDING.name())
                    .clockIn(clockIn)
                    .clockOut(clockOut)
                    .build();
            canClockIn = record.getClockIn() == null;
            canClockOut = record.getClockIn() != null && record.getClockOut() == null;
        }

        return MobileWorkerDto.TodayRes.builder()
                .workDate(today.toString())
                .attendance(attendanceSnap)
                .placement(placement)
                .canClockIn(canClockIn)
                .canClockOut(canClockOut)
                .rostered(record != null)
                .build();
    }

    // ─────────────────────────────────────────────────
    // POST /mobile/worker/attendance
    // ─────────────────────────────────────────────────

    @Transactional
    public MobileWorkerDto.TodayRes recordAttendance(Long workerIdx, String action, String recognizedAtStr) {
        Worker worker = findWorker(workerIdx);
        LocalDate today = LocalDate.now(KST);

        AttendanceRecord old = attendanceRepository.findByWorkerIdxAndWorkDate(workerIdx, today)
                .orElseThrow(() -> new BaseException(WORKER_ATTENDANCE_NOT_FOUND));

        LocalTime recognizedAt = parseOrNow(recognizedAtStr);
        AttendanceStatus newStatus = null;

        if ("CHECK_IN".equals(action)) {
            LocalTime deadline = attendanceProps.getOfficialStart()
                    .plusMinutes(attendanceProps.getLateGraceMinutes());
            newStatus = recognizedAt.isAfter(deadline)
                    ? AttendanceStatus.LATE
                    : AttendanceStatus.PRESENT;

            attendanceRepository.delete(old);
            attendanceRepository.flush();
            attendanceRepository.save(AttendanceRecord.builder()
                    .worker(worker)
                    .workDate(today)
                    .clockIn(recognizedAt)
                    .clockOut(old.getClockOut())
                    .manDays(old.getManDays())
                    .attendanceStatus(newStatus)
                    .employmentKind(old.getEmploymentKind())
                    .siteCode(worker.getSiteCode())
                    .build());
            attendanceLogRepository.save(AttendanceLog.builder()
                    .workerIdx(worker.getIdx())
                    .workDate(today)
                    .siteCode(worker.getSiteCode())
                    .eventType(AttendanceEventType.CLOCK_IN)
                    .recognizedAt(recognizedAt)
                    .build());
            attendanceLogRepository.flush();
            fatigueCalculationService.recalculateAndPersist(worker.getIdx(), today);

        } else if ("CHECK_OUT".equals(action)) {
            if (old.getClockIn() == null) {
                throw new BaseException(WORKER_CLOCK_IN_REQUIRED);
            }
            newStatus = recognizedAt.isBefore(attendanceProps.getOfficialEnd())
                    ? AttendanceStatus.EARLY_LEAVE
                    : AttendanceStatus.LEAVE;

            attendanceRepository.delete(old);
            attendanceRepository.flush();
            attendanceRepository.save(AttendanceRecord.builder()
                    .worker(worker)
                    .workDate(today)
                    .clockIn(old.getClockIn())
                    .clockOut(recognizedAt)
                    .manDays(old.getManDays())
                    .attendanceStatus(newStatus)
                    .employmentKind(old.getEmploymentKind())
                    .siteCode(worker.getSiteCode())
                    .build());
            attendanceLogRepository.save(AttendanceLog.builder()
                    .workerIdx(worker.getIdx())
                    .workDate(today)
                    .siteCode(worker.getSiteCode())
                    .eventType(AttendanceEventType.CLOCK_OUT)
                    .recognizedAt(recognizedAt)
                    .build());
        } else {
            throw new BaseException(WORKER_ATTENDANCE_NOT_FOUND); // invalid action
        }

        // SSE 이벤트 broadcast — 관리자 웹에 실시간 반영
        if (newStatus != null) {
            try {
                java.util.Map<String, Object> event = new java.util.LinkedHashMap<>();
                event.put("workerIdx", worker.getIdx());
                event.put("name", worker.getName());
                event.put("action", action);
                event.put("time", formatTime(recognizedAt));
                event.put("siteCode", worker.getSiteCode());
                event.put("attendanceStatus", newStatus.name());
                sseEmitterRegistry.broadcast(worker.getSiteCode(), event);
            } catch (Exception e) {
                log.warn("[SSE] attendance broadcast failed: {}", e.getMessage());
            }
        }

        return getToday(workerIdx);
    }

    // ─────────────────────────────────────────────────
    // GET /mobile/worker/attendance-history
    // ─────────────────────────────────────────────────

    public List<MobileWorkerDto.AttendanceHistoryItemRes> getAttendanceHistory(Long workerIdx, int days) {
        ensureExists(workerIdx);
        int safeDays = Math.max(1, Math.min(90, days));
        LocalDate today = LocalDate.now(KST);
        LocalDate from = today.minusDays(safeDays - 1L);

        // attendance_record: 출퇴근 실적
        List<AttendanceRecord> records = attendanceRepository
                .findAllByWorkerIdxAndWorkDateBetweenOrderByWorkDateDesc(workerIdx, from, today);

        // staffing_log: 확정 배치 스냅샷 (날짜별 최신 1건 dedup)
        List<StaffingLog> logs = staffingLogRepository
                .findAllByWorkerIdxAndWorkDateBetween(workerIdx, from, today);
        Map<LocalDate, StaffingLog> logByDate = new HashMap<>();
        for (StaffingLog log : logs) {
            logByDate.putIfAbsent(log.getWorkDate(), log); // 이미 최신순 정렬 기대
        }

        return records.stream()
                .map(r -> {
                    StaffingLog log = logByDate.get(r.getWorkDate());
                    return MobileWorkerDto.AttendanceHistoryItemRes.builder()
                            .id(String.valueOf(r.getIdx()))
                            .date(r.getWorkDate().toString())
                            .clockIn(formatTime(r.getClockIn()))
                            .clockOut(formatTime(r.getClockOut()))
                            .attendanceStatus(r.getAttendanceStatus() != null
                                    ? r.getAttendanceStatus().name()
                                    : AttendanceStatus.ABSENT.name())
                            .zoneMain(log != null ? log.getZoneMainTitle() : null)
                            .zoneSub(log != null ? log.getZoneSubTitle() : null)
                            .zoneDisplay(formatZoneLine(
                                    log != null ? log.getZoneMainTitle() : null,
                                    log != null ? log.getZoneSubTitle() : null))
                            .assignedTrade(log != null ? log.getTradeName() : null)
                            .build();
                })
                .collect(Collectors.toList());
    }

    // ─────────────────────────────────────────────────
    // GET /mobile/worker/accidents
    // ─────────────────────────────────────────────────

    public List<MobileWorkerDto.AccidentRes> getAccidents(Long workerIdx) {
        return workerDetailService.getAccidents(workerIdx).stream()
                .map(a -> MobileWorkerDto.AccidentRes.builder()
                        .idx(a.getIdx())
                        .occurredAt(a.getOccurredAt() != null ? a.getOccurredAt().toString() : null)
                        .accidentType(a.getAccidentType())
                        .zoneMain(a.getZoneMain())
                        .zoneSub(a.getZoneSub())
                        .zoneDisplay(a.getZoneDisplay())
                        .resolution(a.getResolution())
                        .build())
                .collect(Collectors.toList());
    }

    // ─────────────────────────────────────────────────
    // GET /mobile/worker/docs
    // ─────────────────────────────────────────────────

    public List<MobileWorkerDto.DocRes> getDocs(Long workerIdx) {
        return workerDetailService.getDocuments(workerIdx).stream()
                .map(d -> MobileWorkerDto.DocRes.builder()
                        .idx(d.getIdx())
                        .title(d.getTitle())
                        .fileUrl(d.getFileUrl())
                        .storedFileName(d.getStoredFileName())
                        .build())
                .collect(Collectors.toList());
    }

    // ─────────────────────────────────────────────────
    // GET /mobile/worker/deployments
    // ─────────────────────────────────────────────────

    public List<MobileWorkerDto.DeploymentRes> getDeployments(Long workerIdx) {
        return workerDetailService.getDeployments(workerIdx).stream()
                .map(d -> MobileWorkerDto.DeploymentRes.builder()
                        .idx(d.getIdx())
                        .assignedAt(d.getAssignedAt() != null ? d.getAssignedAt().toString() : null)
                        .confirmedAt(d.getConfirmedAt() != null
                                ? d.getConfirmedAt().toString().substring(0, 16).replace('T', ' ')
                                : null)
                        .zoneMain(d.getZoneMain())
                        .zoneSub(d.getZoneSub())
                        .zoneDisplay(d.getZoneDisplay())
                        .tradeName(d.getTradeName())
                        .siteCode(d.getSiteCode())
                        .build())
                .collect(Collectors.toList());
    }

    // ─────────────────────────────────────────────────
    // 내부 헬퍼
    // ─────────────────────────────────────────────────

    private MobileWorkerDto.PlacementRes resolvePlacement(Long workerIdx, LocalDate date) {
        // 1순위: staffing_log (확정 배치 스냅샷)
        List<StaffingLog> logs = staffingLogRepository
                .findAllByWorkerIdxAndWorkDateBetween(workerIdx, date, date);
        if (!logs.isEmpty()) {
            StaffingLog log = logs.get(0);
            return MobileWorkerDto.PlacementRes.builder()
                    .zoneMain(log.getZoneMainTitle())
                    .zoneSub(log.getZoneSubTitle())
                    .zoneDisplay(formatZoneLine(log.getZoneMainTitle(), log.getZoneSubTitle()))
                    .assignedTrade(log.getTradeName())
                    .assignmentConfirmed(true)
                    .build();
        }

        // 2순위: staffing_assignment + ZoneSub 조인 (미확정 배치)
        List<StaffingAssignment> assignments = staffingAssignmentRepository
                .findAllWithZonesByWorkerIdxAndWorkDate(workerIdx, date);
        if (!assignments.isEmpty()) {
            StaffingAssignment sa = assignments.get(0);
            String zm = sa.getZoneSub().getZoneMain().getTitle();
            String zs = sa.getZoneSub().getTitle();
            return MobileWorkerDto.PlacementRes.builder()
                    .zoneMain(zm)
                    .zoneSub(zs)
                    .zoneDisplay(formatZoneLine(zm, zs))
                    .location(sa.getZoneSub().getLocation())
                    .assignedTrade(sa.getZoneSub().getTradeName())
                    .workTime(sa.getZoneSub().getWorkTime())
                    .assignmentConfirmed(false)
                    .build();
        }

        return null;
    }

    private Worker findWorker(Long workerIdx) {
        return workerRepository.findById(workerIdx)
                .orElseThrow(() -> new BaseException(WORKER_NOT_FOUND));
    }

    private void ensureExists(Long workerIdx) {
        if (!workerRepository.existsById(workerIdx)) {
            throw new BaseException(WORKER_NOT_FOUND);
        }
    }

    private LocalTime parseOrNow(String value) {
        if (value == null || value.isBlank()) {
            return LocalTime.now(KST).withNano(0);
        }
        try {
            return LocalTime.parse(value.length() == 5 ? value + ":00" : value);
        } catch (Exception e) {
            return LocalTime.now(KST).withNano(0);
        }
    }

    private String formatTime(LocalTime time) {
        return time != null ? time.toString().substring(0, 5) : null;
    }

    private String formatZoneLine(String main, String sub) {
        boolean hm = main != null && !main.isBlank();
        boolean hs = sub != null && !sub.isBlank();
        if (hm && hs) return main.trim() + " · " + sub.trim();
        if (hm) return main.trim();
        if (hs) return sub.trim();
        return null;
    }

}
