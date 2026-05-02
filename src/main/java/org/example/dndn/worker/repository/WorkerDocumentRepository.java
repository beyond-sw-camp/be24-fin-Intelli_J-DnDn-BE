package org.example.dndn.worker.repository;

import org.example.dndn.worker.model.entity.WorkerDocument;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface WorkerDocumentRepository extends JpaRepository<WorkerDocument, Long> {
    // MANAGEMENT_005 안전 및 서류 현황
    List<WorkerDocument> findAllByWorkerIdx(Long workerIdx);
}