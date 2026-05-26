package org.example.dndndocumentmanagement.dto;

import java.util.Map;
import org.example.dndndocumentmanagement.model.DocumentType;

public record DocumentPreviewData(
        String documentId,
        DocumentType documentType,
        Map<String, Object> payload
) {

    public static DocumentPreviewData empty(String documentId) {
        return new DocumentPreviewData(documentId, DocumentType.ALL, Map.of());
    }
}
