package org.example.dndncore.worker.repository;

import org.example.dndncore.worker.model.entity.WorkerDocument;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;

public interface WorkerDocumentRepository extends JpaRepository<WorkerDocument, Long> {
    // MANAGEMENT_005 안전 및 서류 현황
    List<WorkerDocument> findAllByWorkerIdx(Long workerIdx);
    List<WorkerDocument> findAllByWorkerIdxInAndTitleContaining(List<Long> workerIdxes, String titleKeyword);
}