package org.example.dndncore.worker.service;

import lombok.RequiredArgsConstructor;
import org.example.dndncore.common.exception.BaseException;
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

    private final WorkerRepository workerRepository;
    private final AttendanceRecordRepository attendanceRepository;
    private final AttendanceLogRepository attendanceLogRepository;
    private final WorkerDocumentRepository documentRepository;
    private final SafetyAccidentRepository accidentRepository;
    private final WorkerFixtureGenerator workerFixtureGenerator;
    private final ManagementAttendanceProperties attendanceProps;
    private final FatigueCalculationService fatigueCalculationService;

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

        // Step 1: 기존 근로자 일괄 조회 (IN 쿼리 1번)
        Set<String> externalCodes = payload.stream()
                .map(WorkerScenarioFixtureRow::getExternalCode)
                .filter(Objects::nonNull)
                .collect(Collectors.toSet());
        Map<String, Worker> existingByCode = workerRepository.findAllByExternalCodeIn(externalCodes)
                .stream()
                .collect(Collectors.toMap(Worker::getExternalCode, w -> w, (a, b) -> a));

        // Step 2: 근로자 upsert — 병렬 리스트로 (worker, row) 쌍 유지
        List<Worker> workers = new ArrayList<>(payload.size());
        List<WorkerScenarioFixtureRow> rows = new ArrayList<>(payload.size());

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
            workers.add(worker);
            rows.add(item);
        }
        // @Modifying 벌크 쿼리 실행 전 worker 변경분을 DB에 반영
        workerRepository.flush();

        List<Long> workerIdxes = workers.stream().map(Worker::getIdx).toList();

        // Step 3: 벌크 근태 행 정규화 (per-worker 루프 5N 회 → 4회)
        bulkNormalizeRosterDayPending(workers, workerIdxes, rosterDate, detail);

        // Step 4: 벌크 서류 동기화 (per-doc DELETE/flush/INSERT → 2회)
        bulkMergeDocuments(workers, rows, workerIdxes, detail);

        // Step 5: 사고 이력 (skip-if-exists 로직상 개별 처리 유지)
        for (int i = 0; i < workers.size(); i++) {
            mergeAccidents(workers.get(i), rows.get(i), detail);
        }

        // Step 6: 벌크 근태 이력 로그 동기화 (날짜별 그룹화 → 고유 날짜 수만큼 쿼리)
        bulkMergeAttendanceHistory(workers, rows, detail);

        // Step 7: 벌크 피로도 재계산 (3 쿼리 고정)
        fatigueCalculationService.bulkRecalculateAndPersist(workers, rosterDate);

        return WorkerDto.SyncRes.builder()
                .created(created)
                .updated(updated)
                .total(payload.size())
                .documentsSynced(detail.documents)
                .accidentsSynced(detail.accidents)
                .attendanceRecordsSynced(detail.attendanceRecords)
                .build();
    }

    /**
     * 벌크 근태 행 정규화.
     *
     * <p>기존: 1명당 SELECT + DELETE + flush() + INSERT + DELETE = 5회 × N<br>
     * 개선: SELECT 1 + DELETE 2 + saveAll 1 = 4회 (N 무관) — flush() O(n²) 완전 제거</p>
     *
     * <p>{@code employment_kind}는 기존 행이 있으면 보존하고, 없으면 Worker 마스터 값 사용.</p>
     */
    private void bulkNormalizeRosterDayPending(
            List<Worker> workers, List<Long> workerIdxes, LocalDate rosterDate, SyncDetailAccumulator acc) {

        // 기존 행 일괄 조회 → employment_kind 보존용 맵 (IN 쿼리 1회)
        Map<Long, EmploymentKind> ekByWorkerIdx = attendanceRepository
                .findAllByWorkerIdxInAndWorkDate(workerIdxes, rosterDate)
                .stream()
                .collect(Collectors.toMap(
                        ar -> ar.getWorker().getIdx(),
                        AttendanceRecord::getEmploymentKind,
                        (a, b) -> a));

        // 기존 attendance_record 일괄 삭제 (벌크 DELETE, flush 불필요)
        attendanceRepository.deleteAllByWorkerIdxInAndWorkDate(workerIdxes, rosterDate);

        // 당일 attendance_log 일괄 삭제 (벌크 DELETE)
        attendanceLogRepository.deleteAllByWorkerIdxInAndWorkDate(workerIdxes, rosterDate);

        // 새 PENDING 행 일괄 저장
        List<AttendanceRecord> toSave = new ArrayList<>(workers.size());
        for (Worker worker : workers) {
            Long wid = worker.getIdx();
            boolean hadRow = ekByWorkerIdx.containsKey(wid);
            EmploymentKind preservedEk = ekByWorkerIdx.getOrDefault(
                    wid, requireNonNullElse(worker.getEmploymentKind(), EmploymentKind.REGULAR));

            toSave.add(AttendanceRecord.builder()
                    .worker(worker)
                    .workDate(rosterDate)
                    .clockIn(null).clockOut(null).manDays(null)
                    .attendanceStatus(AttendanceStatus.PENDING)
                    .employmentKind(preservedEk)
                    .siteCode(worker.getSiteCode())
                    .build());

            if (!hadRow) acc.attendanceRecords++;
        }
        attendanceRepository.saveAll(toSave);
    }

    /**
     * 벌크 서류 동기화.
     *
     * <p>기존: 1명당 N_doc × (SELECT + DELETE + flush() + INSERT)<br>
     * 개선: 전체 DELETE 1회 + saveAll 1회 — 픽스처에 없는 수동 등록 서류는 보존</p>
     */
    private void bulkMergeDocuments(
            List<Worker> workers, List<WorkerScenarioFixtureRow> rows,
            List<Long> workerIdxes, SyncDetailAccumulator acc) {

        List<String> allTitles = rows.stream()
                .filter(r -> r.getDocuments() != null)
                .flatMap(r -> r.getDocuments().stream())
                .map(WorkerScenarioFixtureRow.DocumentFixtureRow::getTitle)
                .filter(Objects::nonNull)
                .distinct()
                .toList();

        if (allTitles.isEmpty()) return;

        // 픽스처 대상 서류만 선택 삭제 (수동 등록 서류 보존)
        documentRepository.deleteAllByWorkerIdxInAndTitleIn(workerIdxes, allTitles);

        List<WorkerDocument> toSave = new ArrayList<>();
        for (int i = 0; i < workers.size(); i++) {
            Worker worker = workers.get(i);
            WorkerScenarioFixtureRow row = rows.get(i);
            if (row.getDocuments() == null) continue;
            for (WorkerScenarioFixtureRow.DocumentFixtureRow r : row.getDocuments()) {
                toSave.add(WorkerDocument.builder()
                        .worker(worker)
                        .title(r.getTitle())
                        .fileUrl(r.getFileUrl())
                        .storedFileName(r.getStoredFileName())
                        .build());
                acc.documents++;
            }
        }
        if (!toSave.isEmpty()) {
            documentRepository.saveAll(toSave);
        }
    }

    /** 사고 이력 — 동일 키 존재 시 스킵하는 누적 방식이므로 개별 처리 유지. */
    private void mergeAccidents(Worker worker, WorkerScenarioFixtureRow row, SyncDetailAccumulator acc) {
        if (row.getAccidents() == null) return;
        Long wid = worker.getIdx();
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

    /**
     * 벌크 근태 이력 로그 동기화.
     *
     * <p>기존: 1명당 N_date × (DELETE + INSERT×2) = 3N_date × N_workers<br>
     * 개선: 고유 날짜별 벌크 DELETE + saveAll 1회</p>
     *
     * <p>과거 근태 이력은 attendance_log 에만 기록 — attendance_record 는 당일 로스터 전용.</p>
     */
    private void bulkMergeAttendanceHistory(
            List<Worker> workers, List<WorkerScenarioFixtureRow> rows, SyncDetailAccumulator acc) {

        // (날짜 → 해당 날짜에 이력이 있는 workerIdx 목록) 맵 구성
        Map<LocalDate, List<Long>> workerIdxesByDate = new HashMap<>();
        List<AttendanceLog> logsToSave = new ArrayList<>();

        for (int i = 0; i < workers.size(); i++) {
            Worker worker = workers.get(i);
            WorkerScenarioFixtureRow row = rows.get(i);
            if (row.getAttendanceRecords() == null) continue;

            Long wid = worker.getIdx();
            for (WorkerScenarioFixtureRow.AttendanceFixtureRow r : row.getAttendanceRecords()) {
                workerIdxesByDate.computeIfAbsent(r.getWorkDate(), d -> new ArrayList<>()).add(wid);

                if (r.getClockIn() != null) {
                    logsToSave.add(AttendanceLog.builder()
                            .workerIdx(wid)
                            .workDate(r.getWorkDate())
                            .eventType(AttendanceEventType.CLOCK_IN)
                            .recognizedAt(r.getClockIn())
                            .build());
                    acc.attendanceRecords++;
                }
                if (r.getClockOut() != null) {
                    logsToSave.add(AttendanceLog.builder()
                            .workerIdx(wid)
                            .workDate(r.getWorkDate())
                            .eventType(AttendanceEventType.CLOCK_OUT)
                            .recognizedAt(r.getClockOut())
                            .build());
                }
            }
        }

        // 날짜별 벌크 DELETE (고유 날짜 수 = 통상 10~30회)
        for (Map.Entry<LocalDate, List<Long>> entry : workerIdxesByDate.entrySet()) {
            attendanceLogRepository.deleteAllByWorkerIdxInAndWorkDate(entry.getValue(), entry.getKey());
        }

        if (!logsToSave.isEmpty()) {
            attendanceLogRepository.saveAll(logsToSave);
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
