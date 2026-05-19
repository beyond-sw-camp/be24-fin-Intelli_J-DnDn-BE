package org.example.dndndocumentmanagement.dto;

import java.time.LocalDate;
import java.util.Map;
import org.example.dndndocumentmanagement.model.DocumentType;

public record DocumentSummary(
        String id,
        DocumentType sourceType,
        Long sourceId,
        String docCode,
        String docTypeCode,
        String fileName,
        String fileExt,
        String fileUrl,
        String origin,
        String partnerName,
        LocalDate uploadDate,
        LocalDate docDate,
        String uploader,
        String version,
        String fileSize,
        String statusCode,
        String tradeName,
        boolean downloadable,
        Map<String, Object> raw
) {
}
