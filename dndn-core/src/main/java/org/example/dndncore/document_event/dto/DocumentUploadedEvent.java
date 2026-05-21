package org.example.dndncore.document_event.dto;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.Map;

public record DocumentUploadedEvent(
        String eventId,
        String eventType,
        LocalDateTime occurredAt,
        Long projectId,
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
        Boolean downloadable,
        String contentText,
        Map<String, Object> previewPayload
) {
}
