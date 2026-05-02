package org.example.dndn.worker.repository;

import org.example.dndn.worker.model.entity.WorkerZoneHistory;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface WorkerZoneHistoryRepository extends JpaRepository<WorkerZoneHistory, Long> {
    List<WorkerZoneHistory> findAllByWorkerIdxOrderByAssignedAtDesc(Long workerIdx);
}
