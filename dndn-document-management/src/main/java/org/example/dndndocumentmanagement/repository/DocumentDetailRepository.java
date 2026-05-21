package org.example.dndndocumentmanagement.repository;

import java.util.Optional;
import org.example.dndndocumentmanagement.dto.DocumentDetail;

public interface DocumentDetailRepository {

    Optional<DocumentDetail> findDetail(String documentId);
}
