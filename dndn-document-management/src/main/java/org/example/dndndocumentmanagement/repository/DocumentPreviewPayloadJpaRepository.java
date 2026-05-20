package org.example.dndndocumentmanagement.repository;

import org.example.dndndocumentmanagement.model.entity.DocumentPreviewPayload;
import org.springframework.data.jpa.repository.JpaRepository;

public interface DocumentPreviewPayloadJpaRepository extends JpaRepository<DocumentPreviewPayload, String> {
}
