package org.example.dndndocumentmanagement.consumer;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.List;
import org.example.dndndocumentmanagement.dto.event.DailyReportChangedEvent;
import org.example.dndndocumentmanagement.dto.event.DocumentUploadedEvent;
import org.example.dndndocumentmanagement.dto.event.WorkOrderChangedEvent;
import org.example.dndndocumentmanagement.service.DocumentProjectionService;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

@Component
public class DocumentEventConsumer {

    private final ObjectMapper objectMapper;
    private final DocumentProjectionService documentProjectionService;

    public DocumentEventConsumer(
            ObjectMapper objectMapper,
            DocumentProjectionService documentProjectionService
    ) {
        this.objectMapper = objectMapper;
        this.documentProjectionService = documentProjectionService;
    }

    @KafkaListener(topics = "${document.kafka.topics.document-uploaded:document.uploaded.v1}")
    public void handleDocumentUploaded(List<String> payloads) {
        documentProjectionService.applyDocumentUploads(readAll(payloads, DocumentUploadedEvent.class));
    }

    @KafkaListener(topics = "${document.kafka.topics.work-order-changed:work-order.changed.v1}")
    public void handleWorkOrderChanged(List<String> payloads) {
        documentProjectionService.applyWorkOrderChanges(readAll(payloads, WorkOrderChangedEvent.class));
    }

    @KafkaListener(topics = "${document.kafka.topics.daily-report-changed:daily-report.changed.v1}")
    public void handleDailyReportChanged(List<String> payloads) {
        documentProjectionService.applyDailyReportChanges(readAll(payloads, DailyReportChangedEvent.class));
    }

    private <T> List<T> readAll(List<String> payloads, Class<T> type) {
        return payloads.stream()
                .map(payload -> read(payload, type))
                .toList();
    }

    private <T> T read(String payload, Class<T> type) {
        try {
            return objectMapper.readValue(payload, type);
        } catch (JsonProcessingException e) {
            throw new IllegalArgumentException("Invalid Kafka payload for " + type.getSimpleName(), e);
        }
    }
}
