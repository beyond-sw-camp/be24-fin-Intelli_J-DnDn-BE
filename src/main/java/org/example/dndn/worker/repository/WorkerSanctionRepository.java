package org.example.dndn.worker.repository;

import org.example.dndn.worker.model.entity.WorkerSanction;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface WorkerSanctionRepository extends JpaRepository<WorkerSanction, Long> {
    // MANAGEMENT_008 제재/주의 이력 — active=true 우선 정렬
    List<WorkerSanction> findAllByWorkerIdxOrderByActiveDescOccurredAtDesc(Long workerIdx);
}
