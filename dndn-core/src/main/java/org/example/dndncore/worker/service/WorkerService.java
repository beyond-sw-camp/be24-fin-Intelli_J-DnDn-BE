package org.example.dndncore.worker.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.example.dndncore.common.exception.BaseException;
import org.example.dndncore.worker.config.ManagementAttendanceProperties;
import org.example.dndncore.worker.model.dto.WorkerDto;
import org.example.dndncore.worker.model.entity.*;
import org.example.dndncore.worker.model.enums.AttendanceEventType;
import org.example.dndncore.worker.model.enums.AttendanceStatus;
import org.example.dndncore.worker.repository.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZoneId;
import java.util.*;
import java.util.stream.Collectors;

import static org.example.dndncore.common.model.BaseResponseStatus.WORKER_ATTENDANCE_NOT_FOUND;
import static org.example.dndncore.common.model.BaseResponseStatus.WORKER_CLOCK_IN_REQUIRED;
import static org.example.dndncore.common.model.BaseResponseStatus.WORKER_NOT_FOUND;
import static org.example.dndncore.common.model.BaseResponseStatus.WORKER_SITE_MISMATCH;

@Slf4j
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class WorkerService {
    private static final ZoneId ROSTER_ZONE = ZoneId.of("Asia/Seoul");
    private static final String SAFETY_EDUCATION_DOCUMENT_KEYWORD = "기초안전보건교육";

    private final WorkerRepository workerRepository;
    private final AttendanceRecordRepository attendanceRepository;
    private final AttendanceLogRepository attendanceLogRepository;
    private final WorkerDocumentRepository documentRepository;
    private final ManagementAttendanceProperties attendanceProps;
    private final FatigueCalculationService fatigueCalculationService;

    /** MANAGEMENT_010 게이트 출근 — 추후 WebSocket 동일 페이로드로 대체 가능. */
    @Transactional
    public WorkerDto.GateAttendanceRes recordGateClockIn(WorkerDto.GateClockInReq req) {
        LocalDate date = req.getWorkDate() != null ? req.getWorkDate() : LocalDate.now(ROSTER_ZONE);
        Worker worker = workerRepository.findById(req.getWorkerIdx())
                .orElseThrow(() -> new BaseException(WORKER_NOT_FOUND));
        validateSiteCode(req.getSiteCode(), worker);
        AttendanceRecord old = attendanceRepository.findByWorkerIdxAndWorkDate(req.getWorkerIdx(), date)
                .orElseThrow(() -> new BaseException(WORKER_ATTENDANCE_NOT_FOUND));
        LocalTime deadline = attendanceProps.getOfficialStart().plusMinutes(attendanceProps.getLateGraceMinutes());
        AttendanceStatus next = req.getRecognizedAt().isAfter(deadline) ? AttendanceStatus.LATE : AttendanceStatus.PRESENT;

        attendanceRepository.delete(old);
        attendanceRepository.flush();
        AttendanceRecord saved = attendanceRepository.save(AttendanceRecord.builder()
                .worker(worker)
                .workDate(date)
                .clockIn(req.getRecognizedAt())
                .clockOut(old.getClockOut())
                .manDays(old.getManDays())
                .attendanceStatus(next)
                .employmentKind(old.getEmploymentKind())
                .siteCode(worker.getSiteCode())
                .build());
        attendanceLogRepository.save(AttendanceLog.builder()
                .workerIdx(worker.getIdx())
                .workDate(date)
                .siteCode(worker.getSiteCode())
                .eventType(AttendanceEventType.CLOCK_IN)
                .recognizedAt(req.getRecognizedAt())
                .build());
        attendanceLogRepository.flush();
        fatigueCalculationService.recalculateAndPersist(worker.getIdx(), date);
        return toGateRes(saved);
    }

    /** MANAGEMENT_011 게이트 퇴근 — 규정 퇴근 시각 이전이면 {@code EARLY_LEAVE}, 정시 또는 그 이후면 {@code LEAVE}. */
    @Transactional
    public WorkerDto.GateAttendanceRes recordGateClockOut(WorkerDto.GateClockOutReq req) {
        LocalDate date = req.getWorkDate() != null ? req.getWorkDate() : LocalDate.now(ROSTER_ZONE);
        Worker worker = workerRepository.findById(req.getWorkerIdx())
                .orElseThrow(() -> new BaseException(WORKER_NOT_FOUND));
        validateSiteCode(req.getSiteCode(), worker);
        AttendanceRecord old = attendanceRepository.findByWorkerIdxAndWorkDate(req.getWorkerIdx(), date)
                .orElseThrow(() -> new BaseException(WORKER_ATTENDANCE_NOT_FOUND));
        if (old.getClockIn() == null) {
            throw new BaseException(WORKER_CLOCK_IN_REQUIRED);
        }
        AttendanceStatus next = req.getRecognizedAt().isBefore(attendanceProps.getOfficialEnd())
                ? AttendanceStatus.EARLY_LEAVE
                : AttendanceStatus.LEAVE;

        attendanceRepository.delete(old);
        attendanceRepository.flush();
        AttendanceRecord saved = attendanceRepository.save(AttendanceRecord.builder()
                .worker(worker)
                .workDate(date)
                .clockIn(old.getClockIn())
                .clockOut(req.getRecognizedAt())
                .manDays(old.getManDays())
                .attendanceStatus(next)
                .employmentKind(old.getEmploymentKind())
                .siteCode(worker.getSiteCode())
                .build());
        attendanceLogRepository.save(AttendanceLog.builder()
                .workerIdx(worker.getIdx())
                .workDate(date)
                .siteCode(worker.getSiteCode())
                .eventType(AttendanceEventType.CLOCK_OUT)
                .recognizedAt(req.getRecognizedAt())
                .build());
        return toGateRes(saved);
    }

    /** siteCode 가 요청에 포함된 경우 worker 소속 현장과 일치하는지 검증한다. */
    private static void validateSiteCode(String reqSiteCode, Worker worker) {
        if (reqSiteCode == null || reqSiteCode.isBlank()) return;
        if (!reqSiteCode.trim().equals(worker.getSiteCode())) {
            throw new BaseException(WORKER_SITE_MISMATCH);
        }
    }

    private static WorkerDto.GateAttendanceRes toGateRes(AttendanceRecord a) {
        return WorkerDto.GateAttendanceRes.builder()
                .workerIdx(a.getWorker().getIdx())
                .workDate(a.getWorkDate())
                .clockIn(a.getClockIn())
                .clockOut(a.getClockOut())
                .attendanceStatus(a.getAttendanceStatus())
                .build();
    }

    private static WorkerDto.StateCountRes emptyAttendanceKpi() {
        return WorkerDto.StateCountRes.builder()
                .pending(0)
                .present(0)
                .late(0)
                .leave(0)
                .earlyLeave(0)
                .absent(0)
                .total(0)
                .build();
    }

    /**
     * MANAGEMENT_003 작업자 목록 조회 — 페이징 + 서버사이드 공종·이름 필터.
     *
     * <ul>
     *   <li>{@code globalKpi} — 현장+날짜 전체 근무자 집계 (필터 무관)</li>
     *   <li>{@code listKpi}  — 공종·이름 필터 적용 후 전체 집계 (페이징 전)</li>
     *   <li>{@code rows}     — 현재 페이지 ({@code size}개)</li>
     *   <li>{@code availableTrades} — 드롭다운용 공종 전체 목록</li>
     * </ul>
     */
    public WorkerDto.ListRes getList(String siteCode, LocalDate date,
                                     String tradeName, String searchName,
                                     int page, int size) {
        LocalDate target = resolveTodayRosterDate(date);
        int safeSize = size < 1 ? 20 : Math.min(size, 200);
        int safePage = Math.max(0, page);

        List<AttendanceRecord> records = (siteCode != null && !siteCode.isBlank())
                ? attendanceRepository.findAllByWorkDateAndSiteCode(target, siteCode.trim())
                : attendanceRepository.findAllByWorkDate(target);
        Map<Long, AttendanceRecord> attendanceByWorkerIdx = records.stream()
                .collect(Collectors.toMap(a -> a.getWorker().getIdx(), a -> a, (a, b) -> a));

        if (attendanceByWorkerIdx.isEmpty()) {
            WorkerDto.StateCountRes zero = emptyAttendanceKpi();
            return WorkerDto.ListRes.builder()
                    .globalKpi(zero).listKpi(zero)
                    .rows(List.of())
                    .totalElements(0).totalPages(0).page(0).size(safeSize)
                    .availableTrades(List.of())
                    .build();
        }

        List<Worker> workers = records.stream()
                .map(AttendanceRecord::getWorker)
                .filter(Objects::nonNull)
                .distinct()
                .sorted(Comparator.comparing(Worker::getName, Comparator.nullsLast(String::compareTo)))
                .toList();

        Set<Long> safetyIds = findSafetyEducationCompletedWorkerIds(attendanceByWorkerIdx.keySet());

        // 전체 WorkerRes 빌드 — globalKpi 및 availableTrades 산출 기준
        List<WorkerDto.WorkerRes> allRows = workers.stream()
                .map(w -> WorkerDto.WorkerRes.from(w, attendanceByWorkerIdx.get(w.getIdx()),
                        safetyIds.contains(w.getIdx())))
                .collect(Collectors.toList());

        WorkerDto.StateCountRes globalKpi = aggregateAttendance(allRows);

        // 드롭다운 공종 목록 (null·공백 제외, 정렬)
        List<String> availableTrades = allRows.stream()
                .map(WorkerDto.WorkerRes::getTrade)
                .filter(t -> t != null && !t.isBlank())
                .distinct()
                .sorted(Comparator.naturalOrder())
                .collect(Collectors.toList());

        // 공종·이름 필터 적용 (페이징 전 전체 집계용)
        String tradeFilter = (tradeName != null && !tradeName.isBlank()) ? tradeName.trim() : null;
        String nameFilter  = (searchName != null && !searchName.isBlank()) ? searchName.trim().toLowerCase() : null;

        List<WorkerDto.WorkerRes> filteredRows = allRows.stream()
                .filter(r -> tradeFilter == null || tradeFilter.equals(r.getTrade()))
                .filter(r -> nameFilter == null || (r.getName() != null && r.getName().toLowerCase().contains(nameFilter)))
                .collect(Collectors.toList());

        WorkerDto.StateCountRes listKpi = aggregateAttendance(filteredRows);
        long totalElements = filteredRows.size();
        int totalPages = totalElements == 0 ? 0 : (int) Math.ceil((double) totalElements / safeSize);
        int clampedPage = Math.min(safePage, Math.max(0, totalPages - 1));

        List<WorkerDto.WorkerRes> pageRows = filteredRows.stream()
                .skip((long) clampedPage * safeSize)
                .limit(safeSize)
                .collect(Collectors.toList());

        return WorkerDto.ListRes.builder()
                .globalKpi(globalKpi)
                .listKpi(listKpi)
                .rows(pageRows)
                .totalElements(totalElements)
                .totalPages(totalPages)
                .page(clampedPage)
                .size(safeSize)
                .availableTrades(availableTrades)
                .build();
    }

    // MANAGEMENT_002 근무자 검색 — 조회일 ATT 명단 범위 안에서 출근 상태/협력사명/이름 필터 적용, 현장 분리
    public WorkerDto.ListRes search(WorkerDto.SearchReq req) {
        LocalDate target = resolveTodayRosterDate(req.getDate());
        AttendanceStatus statusFilter = req.getAttendanceStatus();
        String siteCode = req.getSiteCode();

        List<AttendanceRecord> records = (siteCode != null && !siteCode.isBlank())
                ? attendanceRepository.findAllByWorkDateAndSiteCode(target, siteCode.trim())
                : attendanceRepository.findAllByWorkDate(target);
        Map<Long, AttendanceRecord> attendanceByWorkerIdx = records.stream()
                .collect(Collectors.toMap(a -> a.getWorker().getIdx(), a -> a, (a, b) -> a));

        if (attendanceByWorkerIdx.isEmpty()) {
            WorkerDto.StateCountRes zero = emptyAttendanceKpi();
            return WorkerDto.ListRes.builder()
                    .globalKpi(zero)
                    .listKpi(zero)
                    .rows(List.of())
                    .build();
        }

        Set<Long> rosterIds = attendanceByWorkerIdx.keySet();
        List<Worker> rosterWorkers = records.stream()
                .map(AttendanceRecord::getWorker)
                .filter(Objects::nonNull)
                .distinct()
                .sorted(Comparator.comparing(Worker::getName, Comparator.nullsLast(String::compareTo)))
                .toList();

        Set<Long> safetyEducationCompletedWorkerIds = findSafetyEducationCompletedWorkerIds(rosterIds);
        List<WorkerDto.WorkerRes> allRows = rosterWorkers.stream()
                .map(w -> WorkerDto.WorkerRes.from(
                        w,
                        attendanceByWorkerIdx.get(w.getIdx()),
                        safetyEducationCompletedWorkerIds.contains(w.getIdx())))
                .collect(Collectors.toList());
        WorkerDto.StateCountRes globalKpi = aggregateAttendance(allRows);

        List<WorkerDto.WorkerRes> rows = workerRepository
                .search(req.getSearchName(), siteCode)
                .stream()
                .filter(w -> rosterIds.contains(w.getIdx()))
                .map(w -> WorkerDto.WorkerRes.from(
                        w,
                        attendanceByWorkerIdx.get(w.getIdx()),
                        safetyEducationCompletedWorkerIds.contains(w.getIdx())))
                .filter(item -> statusFilter == null || item.getAttendanceStatus() == statusFilter)
                .sorted(Comparator.comparing(WorkerDto.WorkerRes::getName, Comparator.nullsLast(String::compareTo)))
                .collect(Collectors.toList());
        WorkerDto.StateCountRes listKpi = aggregateAttendance(rows);

        return WorkerDto.ListRes.builder()
                .globalKpi(globalKpi)
                .listKpi(listKpi)
                .rows(rows)
                .build();
    }

    private Set<Long> findSafetyEducationCompletedWorkerIds(Collection<Long> workerIds) {
        if (workerIds == null || workerIds.isEmpty()) {
            return Set.of();
        }

        return documentRepository.findAllByWorkerIdxInAndTitleContaining(
                        new ArrayList<>(workerIds),
                        SAFETY_EDUCATION_DOCUMENT_KEYWORD)
                .stream()
                .map(WorkerDocument::getWorkerIdx)
                .collect(Collectors.toSet());
    }

    /**
     * 프론트엔드 {@code deriveAttendanceTag} 로직과 동일하게 집계.
     * PRESENT/LATE + clockOut → leave(퇴근), LEAVE 상태도 leave로 집계.
     * PRESENT + clockIn > 07:00 → late(지각).
     */
    private WorkerDto.StateCountRes aggregateAttendance(List<WorkerDto.WorkerRes> rows) {
        int pending = 0, present = 0, late = 0, leave = 0, early = 0, absent = 0;
        final java.time.LocalTime LATE_CUTOFF = java.time.LocalTime.of(7, 0);
        for (WorkerDto.WorkerRes r : rows) {
            AttendanceStatus s = r.getAttendanceStatus();
            boolean hasOut = r.getClockOut() != null;
            if (s == AttendanceStatus.PENDING)     { pending++; continue; }
            if (s == AttendanceStatus.ABSENT)      { absent++;  continue; }
            if (s == AttendanceStatus.EARLY_LEAVE) { early++;   continue; }
            if (hasOut) { leave++; continue; }     // PRESENT/LATE/LEAVE + clockOut → 퇴근
            if (s == AttendanceStatus.LEAVE)       { leave++;   continue; }
            if (s == AttendanceStatus.LATE)        { late++;    continue; }
            // PRESENT 이면서 clockOut 없음
            if (r.getClockIn() == null)            { absent++;  continue; }
            if (r.getClockIn().isAfter(LATE_CUTOFF)) { late++; continue; }
            present++;
        }
        return WorkerDto.StateCountRes.builder()
                .pending(pending)
                .present(present).late(late).leave(leave).earlyLeave(early).absent(absent)
                .total(rows.size())
                .build();
    }

    /**
     * attendance_record는 당일 로스터 스냅샷만 유지한다. 과거·미래 조회일은 오늘로 고정한다.
     */
    private static LocalDate resolveTodayRosterDate(LocalDate requested) {
        LocalDate today = LocalDate.now(ROSTER_ZONE);
        if (requested != null && !requested.equals(today)) {
            log.warn("[근태] 요청일 {} — attendance_record는 당일({})만 조회·동기화합니다",
                    requested, today);
        }
        return today;
    }
}
