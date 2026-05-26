package org.example.dndndocumentmanagement.repository;

import org.example.dndndocumentmanagement.dto.DocumentPage;
import org.example.dndndocumentmanagement.dto.DocumentSearchCondition;

public interface DocumentSearchRepository {

    DocumentPage search(DocumentSearchCondition condition);
}
