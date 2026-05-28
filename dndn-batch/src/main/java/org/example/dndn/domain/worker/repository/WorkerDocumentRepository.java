package org.example.dndn.domain.worker.repository;

import org.example.dndn.domain.worker.model.entity.WorkerDocument;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface WorkerDocumentRepository extends JpaRepository<WorkerDocument, Long> {

    Optional<WorkerDocument> findByWorkerIdxAndTitle(Long workerIdx, String title);

    // 픽스처 대상 서류만 선택 삭제 (수동 등록 서류 보존) — syncChunk 내 청크 단위 사용
    @Modifying
    @Query("DELETE FROM WorkerDocument d WHERE d.workerIdx IN :workerIdxes AND d.title IN :titles")
    int deleteAllByWorkerIdxInAndTitleIn(
            @Param("workerIdxes") List<Long> workerIdxes,
            @Param("titles") List<String> titles);

    // rosterCleanupStep 전용 — 픽스처 서류 제목 전체를 한 번에 삭제 (직렬 단일 트랜잭션)
    @Modifying
    @Query("DELETE FROM WorkerDocument d WHERE d.title IN :titles")
    int deleteAllByTitleIn(@Param("titles") List<String> titles);
}
