package org.example.dndncore.document_management;

import org.example.dndncore.project.model.entity.MasterSchedule;
import org.example.dndncore.project.model.enums.DocType;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface DocumentManagementRepository extends JpaRepository<MasterSchedule, Long> {
    // 페이징 + 정렬 지원
    Page<MasterSchedule> findAllByProjectIdx(Long projectIdx, Pageable pageable);

    // 추가: docType 필터 (단일)
    Page<MasterSchedule> findAllByProjectIdxAndDocType(Long projectIdx, DocType docType, Pageable pageable);

    // 추가: docType 필터 (여러 개 제외)
    Page<MasterSchedule> findAllByProjectIdxAndDocTypeNotIn(Long projectIdx, List<DocType> excludeTypes, Pageable pageable);

    Optional<MasterSchedule> findFirstByProjectIdxAndDocTypeOrderByCreatedAtDesc(
            Long projectIdx, DocType docType);

    boolean existsByProjectIdxAndDocType(Long projectIdx, DocType docType);
}