package org.example.dndn.worker.repository;

import org.example.dndn.worker.model.entity.WorkerZoneHistory;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.LocalDate;
import java.util.List;

public interface WorkerZoneHistoryRepository extends JpaRepository<WorkerZoneHistory, Long> {
    // MANAGEMENT_007 구역 배치 이력
    List<WorkerZoneHistory> findAllByWorkerIdxOrderByAssignedAtDesc(Long workerIdx);

    // 재동기화 시 동일 이력 행 중복 삽입 방지
    boolean existsByWorkerIdxAndAssignedAtAndZone(Long workerIdx, LocalDate assignedAt, String zone);
}
