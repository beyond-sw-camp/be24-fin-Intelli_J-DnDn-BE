package org.example.dndndocumentmanagement.service;

import org.example.dndndocumentmanagement.dto.DocumentPage;
import org.example.dndndocumentmanagement.dto.DocumentSearchCondition;
import org.example.dndndocumentmanagement.repository.DocumentSearchRepository;
import org.springframework.stereotype.Service;

@Service
public class DocumentQueryService {

    private final DocumentSearchRepository documentSearchRepository;

    public DocumentQueryService(DocumentSearchRepository documentSearchRepository) {
        this.documentSearchRepository = documentSearchRepository;
    }

    public DocumentPage search(DocumentSearchCondition condition) {
        return documentSearchRepository.search(condition.normalized());
    }
}
