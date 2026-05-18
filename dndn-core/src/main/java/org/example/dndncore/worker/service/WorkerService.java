package org.example.dndncore.worker.service;

import lombok.RequiredArgsConstructor;
import org.example.dndncore.common.exception.BaseException;
import org.example.dndncore.project.repository.ProjectRepository;
import org.example.dndncore.worker.config.ManagementAttendanceProperties;
import org.example.dndncore.worker.fixture.WorkerFixtureGenerator;
import org.example.dndncore.worker.fixture.WorkerScenarioFixtureRow;
import org.example.dndncore.worker.model.dto.WorkerDto;
import org.example.dndncore.worker.model.entity.*;
import org.example.dndncore.worker.model.enums.AttendanceEventType;
import org.example.dndncore.worker.model.enums.AttendanceStatus;
import org.example.dndncore.worker.model.enums.EmploymentKind;
import org.example.dndncore.worker.repository.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalTime;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

import static java.util.Objects.requireNonNullElse;
import static org.example.dndncore.common.model.BaseResponseStatus.WORKER_ATTENDANCE_NOT_FOUND;
import static org.example.dndncore.common.model.BaseResponseStatus.WORKER_CLOCK_IN_REQUIRED;
import static org.example.dndncore.common.model.BaseResponseStatus.WORKER_NOT_FOUND;
import static org.example.dndncore.common.model.BaseResponseStatus.WORKER_SITE_MISMATCH;
import static org.example.dndncore.common.model.BaseResponseStatus.WORKER_SYNC_MISSING_DATE;
import static org.example.dndncore.common.model.BaseResponseStatus.WORKER_SYNC_MISSING_SITE_CODE;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class WorkerService {
    private static final String SAFETY_EDUCATION_DOCUMENT_KEYWORD = "기초안전보건교육";
    private static final Pattern SITE_CODE_PATTERN = Pattern.compile("^\\s*\\[([^\\]]+)]");

    private final WorkerRepository workerRepository;
    private final AttendanceRecordRepository attendanceRepository;
    private final AttendanceLogRepository attendanceLogRepository;
    private final WorkerDocumentRepository documentRepository;
    private final SafetyAccidentRepository accidentRepository;
    private final WorkerFixtureGenerator workerFixtureGenerator;
    private final ManagementAttendanceProperties attendanceProps;
    private final FatigueCalculationService fatigueCalculationService;
    private final ProjectRepository projectRepository;

    // MANAGEMENT_001 전체 현장 일괄 동기화 (스케줄러 및 수동 트리거 공용)
    @Transactional
    public WorkerDto.BulkSyncRes syncAllSites(LocalDate rosterDate) {
        List<String> siteCodes = projectRepository.findAll().stream()
                .map(p -> SITE_CODE_PATTERN.matcher(p.getName()))
                .filter(Matcher::find)
                .map(m -> m.group(1))
                .distinct()
                .toList();

        List<WorkerDto.SiteSyncResult> results = new ArrayList<>();
        for (String siteCode : siteCodes) {
            try {
                WorkerDto.SyncRes detail = syncWorkforce(siteCode, rosterDate);
                results.add(WorkerDto.SiteSyncResult.builder()
                        .siteCode(siteCode)
                        .success(true)
                        .detail(detail)
                        .build());
            } catch (Exception e) {
                results.add(WorkerDto.SiteSyncResult.builder()
                        .siteCode(siteCode)
                        .success(false)
                        .errorMessage(e.getMessage())
                        .build());
            }
        }

        return WorkerDto.BulkSyncRes.builder()
                .syncDate(rosterDate)
                .siteCount(siteCodes.size())
                .results(results)
                .build();
    }

    // MANAGEMENT_001 인력 데이터 불러오기.
    @Transactional
    public WorkerDto.SyncRes syncWorkforce(String siteCode, LocalDate rosterDate) {
        if (siteCode == null || siteCode.isBlank()) {
            throw new BaseException(WORKER_SYNC_MISSING_SITE_CODE);
        }
        if (rosterDate == null) {
            throw new BaseException(WORKER_SYNC_MISSING_DATE);
        }
        List<WorkerScenarioFixtureRow> payload = workerFixtureGenerator.generate(siteCode);
        if (payload.isEmpty()) {
            return WorkerDto.SyncRes.builder()
                    .created(0).updated(0).total(0)
                    .documentsSynced(0).accidentsSynced(0).attendanceRecordsSynced(0)
                    .build();
        }
        int created = 0, updated = 0;
        SyncDetailAccumulator detail = new SyncDetailAccumulator();

        Set<String> externalCodes = payload.stream()
                .map(WorkerScenarioFixtureRow::getExternalCode)
                .filter(Objects::nonNull)
                .collect(Collectors.toSet());
        Map<String, Worker> existingByCode = workerRepository.findAllByExternalCodeIn(externalCodes)
                .stream()
                .collect(Collectors.toMap(Worker::getExternalCode, w -> w, (a, b) -> a));

        for (WorkerScenarioFixtureRow item : payload) {
            Worker worker;
            Worker existing = item.getExternalCode() != null ? existingByCode.get(item.getExternalCode()) : null;
            if (existing != null) {
                existing.updateFromSync(item.toWorkerEntity());
                worker = existing;
                updated++;
            } else {
                worker = workerRepository.save(item.toWorkerEntity());
                created++;
            }

            mergeScenarioDetailsFromFixture(worker, item, detail);
            normalizeRosterDayPending(worker, rosterDate, detail);
            fatigueCalculationService.recalculateAndPersist(worker.getIdx(), rosterDate);
        }

        return WorkerDto.SyncRes.builder()
                .created(created)
                .updated(updated)
                .total(payload.size())
                .documentsSynced(detail.documents)
                .accidentsSynced(detail.accidents)
                .attendanceRecordsSynced(detail.attendanceRecords)
                .build();
    }

    // sync 시 해당 근무일 행을 PENDING(미출근)으로 생성.
    // 실제 출근 시각·상태는 게이트 인식(recordGateClockIn) 시점에 갱신된다.
    // `employment_kind` 는 동기화 직전 해당 일 행이 있으면 유지하고, 없으면 {@link Worker#getEmploymentKind()} 마스터 값.
    private void normalizeRosterDayPending(Worker worker, LocalDate rosterDate, SyncDetailAccumulator acc) {
        Long wid = worker.getIdx();
        Optional<AttendanceRecord> prev = attendanceRepository.findByWorkerIdxAndWorkDate(wid, rosterDate);
        boolean hadRow = prev.isPresent();
        EmploymentKind preservedEk = prev.map(AttendanceRecord::getEmploymentKind)
                .orElseGet(() -> requireNonNullElse(worker.getEmploymentKind(), EmploymentKind.REGULAR));
        prev.ifPresent(attendanceRepository::delete);
        attendanceRepository.flush();

        attendanceRepository.save(AttendanceRecord.builder()
                .worker(worker)
                .workDate(rosterDate)
                .clockIn(null)
                .clockOut(null)
                .manDays(null)
                .attendanceStatus(AttendanceStatus.PENDING)
                .employmentKind(preservedEk)
                .siteCode(worker.getSiteCode())
                .build());

        // 당일 출근 로그 초기화 — 실제 게이트 인식 시 CLOCK_IN 이벤트가 기록된다
        attendanceLogRepository.deleteAllByWorkerIdxAndWorkDate(wid, rosterDate);

        if (!hadRow) {
            acc.attendanceRecords++;
        }
    }

    /** MANAGEMENT_010 게이트 출근 — 추후 WebSocket 동일 페이로드로 대체 가능. */
    @Transactional
    public WorkerDto.GateAttendanceRes recordGateClockIn(WorkerDto.GateClockInReq req) {
        LocalDate date = req.getWorkDate() != null ? req.getWorkDate() : LocalDate.now();
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
        return toGateRes(saved);
    }

    /** MANAGEMENT_011 게이트 퇴근 — 규정 퇴근 시각 이전이면 {@code EARLY_LEAVE}, 정시 또는 그 이후면 {@code LEAVE}. */
    @Transactional
    public WorkerDto.GateAttendanceRes recordGateClockOut(WorkerDto.GateClockOutReq req) {
        LocalDate date = req.getWorkDate() != null ? req.getWorkDate() : LocalDate.now();
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

    /**
     * 픽스처에 나온 행만 반영하고 카운터를 올린다.
     * 근태·서류는 자연 키 단위로 삭제 후 재삽입(불변 빌더 엔티티 가정), 이력류는 동일 키면 스킵.
     */
    private void mergeScenarioDetailsFromFixture(Worker worker, WorkerScenarioFixtureRow row, SyncDetailAccumulator acc) {
        Long wid = worker.getIdx();

        if (row.getDocuments() != null) {
            for (WorkerScenarioFixtureRow.DocumentFixtureRow r : row.getDocuments()) {
                documentRepository.findByWorkerIdxAndTitle(wid, r.getTitle()).ifPresent(documentRepository::delete);
                documentRepository.flush();
                documentRepository.save(WorkerDocument.builder()
                        .worker(worker)
                        .title(r.getTitle())
                        .fileUrl(r.getFileUrl())
                        .storedFileName(r.getStoredFileName())
                        .build());
                acc.documents++;
            }
        }
        if (row.getAccidents() != null) {
            for (WorkerScenarioFixtureRow.AccidentFixtureRow r : row.getAccidents()) {
                String zm = requireNonNullElse(r.getZoneMain(), "").trim();
                String zs = requireNonNullElse(r.getZoneSub(), "").trim();
                if (accidentRepository.existsByWorkerIdxAndOccurredAtAndAccidentTypeAndZoneMainAndZoneSub(
                        wid,
                        r.getOccurredAt(),
                        requireNonNullElse(r.getAccidentType(), ""),
                        zm.isEmpty() ? null : zm,
                        zs.isEmpty() ? null : zs)) {
                    continue;
                }
                accidentRepository.save(SafetyAccident.builder()
                        .worker(worker)
                        .occurredAt(r.getOccurredAt())
                        .accidentType(r.getAccidentType())
                        .zoneMain(zm.isEmpty() ? null : zm)
                        .zoneSub(zs.isEmpty() ? null : zs)
                        .resolution(r.getResolution())
                        .build());
                acc.accidents++;
            }
        }
        // 과거 근태 이력은 attendance_log 에만 기록 — attendance_record 는 당일 로스터 전용
        if (row.getAttendanceRecords() != null) {
            for (WorkerScenarioFixtureRow.AttendanceFixtureRow r : row.getAttendanceRecords()) {
                attendanceLogRepository.deleteAllByWorkerIdxAndWorkDate(wid, r.getWorkDate());
                if (r.getClockIn() != null) {
                    attendanceLogRepository.save(AttendanceLog.builder()
                            .workerIdx(wid)
                            .workDate(r.getWorkDate())
                            .eventType(AttendanceEventType.CLOCK_IN)
                            .recognizedAt(r.getClockIn())
                            .build());
                    acc.attendanceRecords++;
                }
                if (r.getClockOut() != null) {
                    attendanceLogRepository.save(AttendanceLog.builder()
                            .workerIdx(wid)
                            .workDate(r.getWorkDate())
                            .eventType(AttendanceEventType.CLOCK_OUT)
                            .recognizedAt(r.getClockOut())
                            .build());
                }
            }
        }
    }

    private static final class SyncDetailAccumulator {
        int documents;
        int accidents;
        int attendanceRecords;
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

    /** MANAGEMENT_003 작업자 목록 조회 — 조회일 AttendanceRecord 기준, 현장 분리 */
    public WorkerDto.ListRes getList(String siteCode, LocalDate date) {
        LocalDate target = date == null ? LocalDate.now() : date;

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

        List<Worker> workers = records.stream()
                .map(AttendanceRecord::getWorker)
                .filter(Objects::nonNull)
                .distinct()
                .sorted(Comparator.comparing(Worker::getName, Comparator.nullsLast(String::compareTo)))
                .toList();

        Set<Long> safetyEducationCompletedWorkerIds = findSafetyEducationCompletedWorkerIds(attendanceByWorkerIdx.keySet());
        List<WorkerDto.WorkerRes> rows = workers.stream()
                .map(w -> WorkerDto.WorkerRes.from(
                        w,
                        attendanceByWorkerIdx.get(w.getIdx()),
                        safetyEducationCompletedWorkerIds.contains(w.getIdx())))
                .collect(Collectors.toList());

        WorkerDto.StateCountRes kpi = aggregateAttendance(rows);
        return WorkerDto.ListRes.builder()
                .globalKpi(kpi)
                .listKpi(kpi)
                .rows(rows)
                .build();
    }

    // MANAGEMENT_002 근무자 검색 — 조회일 ATT 명단 범위 안에서 출근 상태/협력사명/이름 필터 적용, 현장 분리
    public WorkerDto.ListRes search(WorkerDto.SearchReq req) {
        LocalDate target = req.getDate() == null ? LocalDate.now() : req.getDate();
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

    private WorkerDto.StateCountRes aggregateAttendance(List<WorkerDto.WorkerRes> rows) {
        int pending = 0, present = 0, late = 0, leave = 0, early = 0, absent = 0;
        for (WorkerDto.WorkerRes r : rows) {
            switch (r.getAttendanceStatus()) {
                case PENDING -> pending++;
                case PRESENT -> present++;
                case LATE -> late++;
                case LEAVE -> leave++;
                case EARLY_LEAVE -> early++;
                case ABSENT -> absent++;
            }
        }
        return WorkerDto.StateCountRes.builder()
                .pending(pending)
                .present(present).late(late).leave(leave).earlyLeave(early).absent(absent)
                .total(rows.size())
                .build();
    }
}
