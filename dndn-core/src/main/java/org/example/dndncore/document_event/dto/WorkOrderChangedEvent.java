package org.example.dndncore.document_event.dto;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.Map;

public record WorkOrderChangedEvent(
        String eventId,
        String eventType,
        LocalDateTime occurredAt,
        Long projectId,
        Long sourceId,
        String docCode,
        String title,
        String tradeName,
        LocalDate dueDate,
        String uploader,
        String statusCode,
        String workDetail,
        String workTime,
        String safetyContent,
        Integer workerCount,
        String location,
        Map<String, Object> previewPayload
) {
}
