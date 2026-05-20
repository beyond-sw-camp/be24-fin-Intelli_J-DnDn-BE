package org.example.dndndocumentmanagement.repository;

import org.example.dndndocumentmanagement.dto.DocumentPage;
import org.example.dndndocumentmanagement.dto.DocumentSearchCondition;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

@Component
@Profile("elastic")
public class EsDocumentSearchRepository implements DocumentSearchRepository {

    @Override
    public DocumentPage search(DocumentSearchCondition condition) {
        return DocumentPage.empty(condition.page(), condition.size());
    }
}
