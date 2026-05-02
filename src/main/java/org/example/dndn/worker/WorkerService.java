package org.example.dndn.worker;

import jakarta.transaction.Transactional;
import lombok.RequiredArgsConstructor;
import org.example.dndn.worker.fixture.WorkerScenarioFixtureLoader;
import org.example.dndn.worker.fixture.WorkerScenarioFixtureRow;
import org.example.dndn.worker.model.dto.WorkerDto;
import org.example.dndn.worker.model.entity.Worker;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Optional;

@Service
@RequiredArgsConstructor
public class WorkerService {
    private final WorkerRepository workerRepository;
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
}
