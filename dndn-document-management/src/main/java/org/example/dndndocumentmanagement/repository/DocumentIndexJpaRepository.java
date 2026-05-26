package org.example.dndndocumentmanagement.repository;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import org.example.dndndocumentmanagement.model.entity.DocumentIndex;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

public interface DocumentIndexJpaRepository
        extends JpaRepository<DocumentIndex, String>, JpaSpecificationExecutor<DocumentIndex> {

    Optional<DocumentIndex> findBySourceTypeAndSourceId(String sourceType, Long sourceId);

    List<DocumentIndex> findAllBySourceTypeAndSourceIdIn(String sourceType, Collection<Long> sourceIds);

    void deleteBySourceTypeAndSourceId(String sourceType, Long sourceId);
}
