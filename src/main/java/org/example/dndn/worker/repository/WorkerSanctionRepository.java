package org.example.dndn.worker.repository;

import org.example.dndn.worker.model.entity.WorkerSanction;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.LocalDate;
import java.util.List;

public interface WorkerSanctionRepository extends JpaRepository<WorkerSanction, Long> {
    // MANAGEMENT_008 제재/주의 이력 — active=true 우선 정렬
    List<WorkerSanction> findAllByWorkerIdxOrderByActiveDescOccurredAtDesc(Long workerIdx);
    // 같은 날·유형·사유면 동일 이벤트로 보고 스킵 (더 세밀히 하려면 외부 이벤트 ID 컬럼 권장)
    boolean existsByWorkerIdxAndOccurredAtAndTypeAndReason(
            Long workerIdx, LocalDate occurredAt, String type, String reason);
}
