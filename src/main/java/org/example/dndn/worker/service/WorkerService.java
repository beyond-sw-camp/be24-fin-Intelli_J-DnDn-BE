package org.example.dndn.worker.service;

import jakarta.transaction.Transactional;
import lombok.RequiredArgsConstructor;
import org.example.dndn.worker.fixture.WorkerScenarioFixtureLoader;
import org.example.dndn.worker.fixture.WorkerScenarioFixtureRow;
import org.example.dndn.worker.model.dto.WorkerDto;
import org.example.dndn.worker.model.entity.AttendanceRecord;
import org.example.dndn.worker.model.entity.Worker;
import org.example.dndn.worker.model.enums.AttendanceStatus;
import org.example.dndn.worker.repository.AttendanceRecordRepository;
import org.example.dndn.worker.repository.WorkerRepository;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class WorkerService {
    private final WorkerRepository workerRepository;
    private final AttendanceRecordRepository attendanceRepository;
    private final WorkerScenarioFixtureLoader workerScenarioFixtureLoader;

    // 인력 데이터 조회
    @Transactional
    public WorkerDto.SyncRes syncWorkforce(String siteCode) {
        List<WorkerScenarioFixtureRow> payload = workerScenarioFixtureLoader.loadWorkers();
        int created = 0, updated = 0;
        for(WorkerScenarioFixtureRow item : payload) {
            Optional<Worker> existing = workerRepository.findByExternalCode(item.getExternalCode());
            if (existing.isPresent()) {
                existing.get().updateFromSync(item.toWorkerEntity());
                updated++;
            } else {
                workerRepository.save(item.toWorkerEntity());
                created++;
            }
        }
        return WorkerDto.SyncRes.builder()
                .created(created)
                .updated(updated)
                .total(payload.size())
                .build();
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
