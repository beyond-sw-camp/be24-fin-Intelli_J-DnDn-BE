package org.example.dndncore.document_event.dto;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.Map;

public record DailyReportChangedEvent(
        String eventId,
        String eventType,
        LocalDateTime occurredAt,
        Long projectId,
        Long sourceId,
        String docCode,
        String tradeName,
        LocalDate reportDate,
        String uploader,
        Double actualProgress,
        Double todayProgress,
        Integer actualWorkerCount,
        String location,
        String issue,
        String todayWork,
        String tomorrowPlan,
        Map<String, Object> previewPayload
) {
}
