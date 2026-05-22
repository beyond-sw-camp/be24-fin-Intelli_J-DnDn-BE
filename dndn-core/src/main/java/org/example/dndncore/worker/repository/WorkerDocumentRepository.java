package org.example.dndncore.worker.repository;

import org.example.dndncore.worker.model.entity.WorkerDocument;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface WorkerDocumentRepository extends JpaRepository<WorkerDocument, Long> {
    // MANAGEMENT_005 안전 및 서류 현황
    List<WorkerDocument> findAllByWorkerIdx(Long workerIdx);
    Optional<WorkerDocument> findByWorkerIdxAndTitle(Long workerIdx, String title);
    List<WorkerDocument> findAllByWorkerIdxInAndTitleContaining(List<Long> workerIdxes, String titleKeyword);

    // 배치 벌크 동기화: 여러 근로자의 특정 제목 서류 일괄 삭제 (픽스처 항목만 대상 — 수동 등록 서류 보존)
    @Modifying
    @Query("DELETE FROM WorkerDocument wd WHERE wd.workerIdx IN :workerIdxes AND wd.title IN :titles")
    int deleteAllByWorkerIdxInAndTitleIn(
            @Param("workerIdxes") List<Long> workerIdxes,
            @Param("titles") List<String> titles);
}