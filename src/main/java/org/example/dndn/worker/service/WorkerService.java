package org.example.dndn.worker.service;

import jakarta.transaction.Transactional;
import lombok.RequiredArgsConstructor;
import org.example.dndn.common.exception.BaseException;
import org.example.dndn.worker.config.ManagementAttendanceProperties;
import org.example.dndn.worker.fixture.WorkerScenarioFixtureLoader;
import org.example.dndn.worker.fixture.WorkerScenarioFixtureRow;
import org.example.dndn.worker.model.dto.WorkerDto;
import org.example.dndn.worker.model.entity.*;
import org.example.dndn.worker.model.enums.AttendanceStatus;
import org.example.dndn.worker.repository.*;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;

import static java.util.Objects.requireNonNullElse;
import static org.example.dndn.common.model.BaseResponseStatus.FAIL;

@Service
@RequiredArgsConstructor
public class WorkerService {
    private final WorkerRepository workerRepository;
    private final AttendanceRecordRepository attendanceRepository;
    private final WorkerDocumentRepository documentRepository;
    private final WorkerZoneHistoryRepository zoneHistoryRepository;
    private final WorkerSanctionRepository sanctionRepository;
    private final SafetyAccidentRepository accidentRepository;
    private final WorkerScenarioFixtureLoader workerScenarioFixtureLoader;
    private final ManagementAttendanceProperties attendanceProps;

    // MANAGEMENT_001 인력 데이터 불러오기.
    @Transactional
    public WorkerDto.SyncRes syncWorkforce(String siteCode, LocalDate rosterDate) {
        if (siteCode == null || siteCode.isBlank()) {
            throw new BaseException(FAIL);
            // throw new BaseException("SYNC_SITE_REQUIRED", "siteCode 가 필요합니다.");
        }
        if (rosterDate == null) {
            throw new BaseException(FAIL);
            // throw new BaseException("SYNC_DATE_REQUIRED", "date 가 필요합니다.");
        }
        List<WorkerScenarioFixtureRow> payload = workerScenarioFixtureLoader.loadWorkersFiltered(siteCode);
        int created = 0, updated = 0;
        SyncDetailAccumulator detail = new SyncDetailAccumulator();

        for (WorkerScenarioFixtureRow item : payload) {
            Worker worker;
            Optional<Worker> existing = workerRepository.findByExternalCode(item.getExternalCode());
            if (existing.isPresent()) {
                worker = existing.get();
                worker.updateFromSync(item.toWorkerEntity());
                updated++;
            } else {
                worker = workerRepository.save(item.toWorkerEntity());
                created++;
            }
            workerRepository.flush();

            mergeScenarioDetailsFromFixture(worker, item, detail);
            normalizeRosterDayPending(worker, rosterDate, detail);
        }

        return WorkerDto.SyncRes.builder()
                .created(created)
                .updated(updated)
                .total(payload.size())
                .documentsSynced(detail.documents)
                .zoneHistoriesSynced(detail.zoneHistories)
                .sanctionsSynced(detail.sanctions)
                .accidentsSynced(detail.accidents)
                .attendanceRecordsSynced(detail.attendanceRecords)
                .build();
    }

    // 명단 반영 직후 해당 근무일 행을 출근 전(`PENDING`)으로 고정. 인사 JSON 에 당일 근태가 있어도 덮어쓴다.
    private void normalizeRosterDayPending(Worker worker, LocalDate rosterDate, SyncDetailAccumulator acc) {
        Long wid = worker.getIdx();
        Optional<AttendanceRecord> prev = attendanceRepository.findByWorkerIdxAndWorkDate(wid, rosterDate);
        boolean hadRow = prev.isPresent();
        prev.ifPresent(attendanceRepository::delete);
        attendanceRepository.flush();
        attendanceRepository.save(AttendanceRecord.builder()
                .worker(worker)
                .workDate(rosterDate)
                .clockIn(null)
                .clockOut(null)
                .manDays(null)
                .attendanceStatus(AttendanceStatus.PENDING)
                .zone(null)
                .closed(false)
                .build());
        if (!hadRow) {
            acc.attendanceRecords++;
        }
    }

    /** MANAGEMENT_010 게이트 출근 — 추후 WebSocket 동일 페이로드로 대체 가능. */
    @Transactional
    public WorkerDto.GateAttendanceRes recordGateClockIn(WorkerDto.GateClockInReq req) {
        LocalDate date = req.getWorkDate() != null ? req.getWorkDate() : LocalDate.now();
        Worker worker = workerRepository.findById(req.getWorkerIdx())
                .orElseThrow(() -> new BaseException(FAIL));
                // .orElseThrow(() -> new BaseException("WORKER_NOT_FOUND", "작업자를 찾을 수 없습니다."));
        AttendanceRecord old = attendanceRepository.findByWorkerIdxAndWorkDate(req.getWorkerIdx(), date)
                .orElseThrow(() -> new BaseException(FAIL));
                // .orElseThrow(() -> new BaseException("ATT_NOT_FOUND", "해당 일자 명단에 없습니다."));
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
                .zone(old.getZone())
                .closed(old.isClosed())
                .build());
        return toGateRes(saved);
    }

    /** MANAGEMENT_011 게이트 퇴근 — 규정 퇴근 시각 이전이면 {@code EARLY_LEAVE}. */
    @Transactional
    public WorkerDto.GateAttendanceRes recordGateClockOut(WorkerDto.GateClockOutReq req) {
        LocalDate date = req.getWorkDate() != null ? req.getWorkDate() : LocalDate.now();
        Worker worker = workerRepository.findById(req.getWorkerIdx())
                .orElseThrow(() -> new BaseException(FAIL));
                // .orElseThrow(() -> new BaseException("WORKER_NOT_FOUND", "작업자를 찾을 수 없습니다."));
        AttendanceRecord old = attendanceRepository.findByWorkerIdxAndWorkDate(req.getWorkerIdx(), date)
                .orElseThrow(() -> new BaseException(FAIL));
                // .orElseThrow(() -> new BaseException("ATT_NOT_FOUND", "해당 일자 명단에 없습니다."));
        if (old.getClockIn() == null) {
            throw new BaseException(FAIL);
            // throw new BaseException("ATT_CLOCK_IN_MISSING", "출근 기록 없이 퇴근할 수 없습니다.");
        }
        AttendanceStatus next = old.getAttendanceStatus();
        if (req.getRecognizedAt().isBefore(attendanceProps.getOfficialEnd())) {
            next = AttendanceStatus.EARLY_LEAVE;
        }

        attendanceRepository.delete(old);
        attendanceRepository.flush();
        AttendanceRecord saved = attendanceRepository.save(AttendanceRecord.builder()
                .worker(worker)
                .workDate(date)
                .clockIn(old.getClockIn())
                .clockOut(req.getRecognizedAt())
                .manDays(old.getManDays())
                .attendanceStatus(next)
                .zone(old.getZone())
                .closed(old.isClosed())
                .build());
        return toGateRes(saved);
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
        if (row.getZoneHistory() != null) {
            for (WorkerScenarioFixtureRow.ZoneHistoryFixtureRow r : row.getZoneHistory()) {
                if (zoneHistoryRepository.existsByWorkerIdxAndAssignedAtAndZone(wid, r.getAssignedAt(), r.getZone())) {
                    continue;
                }
                zoneHistoryRepository.save(WorkerZoneHistory.builder()
                        .worker(worker)
                        .assignedAt(r.getAssignedAt())
                        .zone(r.getZone())
                        .workType(r.getWorkType())
                        .partnerCompanySnapshot(r.getPartnerCompanySnapshot())
                        .build());
                acc.zoneHistories++;
            }
        }
        if (row.getSanctions() != null) {
            for (WorkerScenarioFixtureRow.SanctionFixtureRow r : row.getSanctions()) {
                if (sanctionRepository.existsByWorkerIdxAndOccurredAtAndTypeAndReason(
                        wid,
                        r.getOccurredAt(),
                        requireNonNullElse(r.getType(), ""),
                        requireNonNullElse(r.getReason(), ""))) {
                    continue;
                }
                sanctionRepository.save(WorkerSanction.builder()
                        .worker(worker)
                        .occurredAt(r.getOccurredAt())
                        .type(r.getType())
                        .reason(r.getReason())
                        .action(r.getAction())
                        .active(r.isActive())
                        .build());
                acc.sanctions++;
            }
        }
        if (row.getAccidents() != null) {
            for (WorkerScenarioFixtureRow.AccidentFixtureRow r : row.getAccidents()) {
                if (accidentRepository.existsByWorkerIdxAndOccurredAtAndAccidentTypeAndZone(
                        wid,
                        r.getOccurredAt(),
                        requireNonNullElse(r.getAccidentType(), ""),
                        requireNonNullElse(r.getZone(), ""))) {
                    continue;
                }
                accidentRepository.save(SafetyAccident.builder()
                        .worker(worker)
                        .occurredAt(r.getOccurredAt())
                        .accidentType(r.getAccidentType())
                        .zone(r.getZone())
                        .resolution(r.getResolution())
                        .severe(r.isSevere())
                        .build());
                acc.accidents++;
            }
        }
        if (row.getAttendanceRecords() != null) {
            for (WorkerScenarioFixtureRow.AttendanceFixtureRow r : row.getAttendanceRecords()) {
                attendanceRepository.findByWorkerIdxAndWorkDate(wid, r.getWorkDate()).ifPresent(attendanceRepository::delete);
                attendanceRepository.flush();
                attendanceRepository.save(AttendanceRecord.builder()
                        .worker(worker)
                        .workDate(r.getWorkDate())
                        .clockIn(r.getClockIn())
                        .clockOut(r.getClockOut())
                        .manDays(r.getManDays())
                        .attendanceStatus(r.getAttendanceStatus())
                        .zone(r.getZone())
                        .closed(r.isClosed())
                        .build());
                acc.attendanceRecords++;
            }
        }
    }

    private static final class SyncDetailAccumulator {
        int documents;
        int zoneHistories;
        int sanctions;
        int accidents;
        int attendanceRecords;
    }

    // MANAGEMENT_002 근무자 검색 — 출근 상태/협력사명/이름 필터 적용
    public WorkerDto.ListRes search(WorkerDto.SearchReq req) {
        LocalDate target = req.getDate() == null ? LocalDate.now() : req.getDate();
        AttendanceStatus statusFilter = req.getAttendanceStatus();

        Map<Long, AttendanceRecord> attendanceByWorkerIdx = attendanceRepository
                .findAllByWorkDate(target)
                .stream()
                .collect(Collectors.toMap(a -> a.getWorker().getIdx(), a -> a, (a, b) -> a));

        List<WorkerDto.WorkerRes> allRows = workerRepository.findAllByOrderByNameAsc().stream()
                .map(w -> WorkerDto.WorkerRes.from(w, attendanceByWorkerIdx.get(w.getIdx())))
                .collect(Collectors.toList());
        WorkerDto.StateCountRes globalKpi = aggregateAttendance(allRows);

        List<WorkerDto.WorkerRes> rows = workerRepository
                .search(req.getPartnerCompany(), req.getSearchName())
                .stream()
                .map(w -> WorkerDto.WorkerRes.from(w, attendanceByWorkerIdx.get(w.getIdx())))
                .filter(item -> statusFilter == null || item.getAttendanceStatus() == statusFilter)
                .collect(Collectors.toList());
        WorkerDto.StateCountRes listKpi = aggregateAttendance(rows);

        return WorkerDto.ListRes.builder()
                .globalKpi(globalKpi)
                .listKpi(listKpi)
                .rows(rows)
                .build();
    }

    /** MANAGEMENT_003 작업자 목록 조회 — 필터 없이 전체 */
    public WorkerDto.ListRes getList(LocalDate date) {
        LocalDate target = date == null ? LocalDate.now() : date;

        List<Worker> workers = workerRepository.findAllByOrderByNameAsc();

        Map<Long, AttendanceRecord> attendanceByWorkerIdx = attendanceRepository
                .findAllByWorkDate(target)
                .stream()
                .collect(Collectors.toMap(a -> a.getWorker().getIdx(), a -> a, (a, b) -> a));

        List<WorkerDto.WorkerRes> rows = workers.stream()
                .map(w -> WorkerDto.WorkerRes.from(w, attendanceByWorkerIdx.get(w.getIdx())))
                .collect(Collectors.toList());

        WorkerDto.StateCountRes globalKpi = aggregateAttendance(rows);
        WorkerDto.StateCountRes listKpi = aggregateAttendance(rows);

        return WorkerDto.ListRes.builder()
                .globalKpi(globalKpi)
                .listKpi(listKpi)
                .rows(rows)
                .build();
    }

    private WorkerDto.StateCountRes aggregateAttendance(List<WorkerDto.WorkerRes> rows) {
        int present = 0, late = 0, early = 0, absent = 0;
        for (WorkerDto.WorkerRes r : rows) {
            switch (r.getAttendanceStatus()) {
                case PRESENT -> present++;
                case LATE -> late++;
                case EARLY_LEAVE -> early++;
                case ABSENT -> absent++;
            }
        }
        return WorkerDto.StateCountRes.builder()
                .present(present).late(late).earlyLeave(early).absent(absent)
                .total(rows.size())
                .build();
    }
}
