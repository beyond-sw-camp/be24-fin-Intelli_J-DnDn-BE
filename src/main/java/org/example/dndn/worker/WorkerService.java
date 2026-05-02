package org.example.dndn.worker;

import jakarta.transaction.Transactional;
import lombok.RequiredArgsConstructor;
import org.example.dndn.worker.fixture.WorkerScenarioFixtureLoader;
import org.example.dndn.worker.fixture.WorkerScenarioFixtureRow;
import org.example.dndn.worker.model.dto.WorkerDto;
import org.example.dndn.worker.model.entity.AttendanceRecord;
import org.example.dndn.worker.model.entity.Worker;
import org.example.dndn.worker.model.enums.AttendanceStatus;
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

    @Transactional
    public WorkerDto.SyncRes syncWorkforce(String siteCode) {
        List<WorkerScenarioFixtureRow> payload = workerScenarioFixtureLoader.loadWorkers();
        int created = 0;
        int updated = 0;
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

        int present = 0, late = 0, early = 0, absent = 0;
        for (WorkerDto.WorkerRes r : rows) {
            switch (r.getAttendanceStatus()) {
                case PRESENT -> present++;
                case LATE -> late++;
                case EARLY_LEAVE -> early++;
                case ABSENT -> absent++;
            }
        }
        WorkerDto.StateCountRes kpi = WorkerDto.StateCountRes.builder()
                .present(present).late(late).earlyLeave(early).absent(absent)
                .total(rows.size())
                .build();

        return WorkerDto.ListRes.builder()
                .kpi(kpi)
                .rows(rows)
                .build();
    }
}
