package org.example.dndndocumentmanagement.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.function.Function;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import org.example.dndndocumentmanagement.dto.event.DailyReportChangedEvent;
import org.example.dndndocumentmanagement.dto.event.DocumentUploadedEvent;
import org.example.dndndocumentmanagement.dto.event.WorkOrderChangedEvent;
import org.example.dndndocumentmanagement.model.entity.DocumentIndex;
import org.example.dndndocumentmanagement.model.entity.DocumentPreviewPayload;
import org.example.dndndocumentmanagement.repository.DocumentIndexJpaRepository;
import org.example.dndndocumentmanagement.repository.DocumentPreviewPayloadJpaRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class DocumentProjectionService {

    private static final String SOURCE_WORK_ORDER = "WORK_ORDER";
    private static final String SOURCE_DAILY_REPORT = "DAILY_REPORT";
    private static final String SOURCE_TRADE_PLAN = "TRADE_PLAN";

    private final DocumentIndexJpaRepository documentIndexRepository;
    private final DocumentPreviewPayloadJpaRepository previewPayloadRepository;
    private final ObjectMapper objectMapper;

    public DocumentProjectionService(
            DocumentIndexJpaRepository documentIndexRepository,
            DocumentPreviewPayloadJpaRepository previewPayloadRepository,
            ObjectMapper objectMapper
    ) {
        this.documentIndexRepository = documentIndexRepository;
        this.previewPayloadRepository = previewPayloadRepository;
        this.objectMapper = objectMapper;
    }

    @Transactional
    public void apply(DocumentUploadedEvent event) {
        applyDocumentUploads(List.of(event));
    }

    @Transactional
    public void applyDocumentUploads(List<DocumentUploadedEvent> events) {
        if (events == null || events.isEmpty()) {
            return;
        }

        Map<SourceLookup, DocumentIndex> existingDocuments = existingDocuments(events.stream()
                .filter(event -> event != null && !isDeleted(event.eventType()))
                .map(event -> new SourceLookup(blankToDefault(event.docTypeCode(), SOURCE_TRADE_PLAN), required(event.sourceId(), "sourceId")))
                .toList());
        Map<String, DocumentIndex> documentsToSave = new LinkedHashMap<>();
        Map<String, PreviewWrite> previewsToSave = new LinkedHashMap<>();

        for (DocumentUploadedEvent event : events) {
            if (event == null) {
                continue;
            }
            String sourceType = blankToDefault(event.docTypeCode(), SOURCE_TRADE_PLAN);
            String documentId = documentId(sourceType, event.sourceId());
            if (isDeleted(event.eventType())) {
                delete(sourceType, event.sourceId());
                documentsToSave.remove(documentId);
                previewsToSave.remove(documentId);
                continue;
            }

            SourceLookup lookup = new SourceLookup(sourceType, event.sourceId());
            DocumentIndex document = documentsToSave.getOrDefault(
                    documentId,
                    existingDocuments.getOrDefault(lookup, new DocumentIndex(documentId, sourceType, event.sourceId()))
            );
            updateDocumentUploaded(document, documentId, sourceType, event);
            documentsToSave.put(documentId, document);
            putPreview(previewsToSave, documentId, sourceType, event.previewPayload());
        }

        saveBatch(documentsToSave.values(), previewsToSave);
    }

    private void updateDocumentUploaded(
            DocumentIndex document,
            String documentId,
            String sourceType,
            DocumentUploadedEvent event
    ) {
        LocalDate date = firstNonNull(event.docDate(), event.uploadDate(), dateFrom(event.occurredAt()));

        document.update(
                required(event.projectId(), "projectId"),
                blankToDefault(event.docCode(), documentId),
                sourceType,
                event.fileName(),
                blankToDefault(event.fileExt(), extension(event.fileName())),
                event.fileUrl(),
                blankToDefault(event.origin(), "partner"),
                event.partnerName(),
                firstNonNull(event.uploadDate(), date),
                date,
                event.uploader(),
                blankToDefault(event.version(), "v1.0"),
                event.fileSize(),
                blankToDefault(event.statusCode(), "APPROVED"),
                event.tradeName(),
                event.downloadable() == null || event.downloadable(),
                event.contentText()
        );
    }

    @Transactional
    public void apply(WorkOrderChangedEvent event) {
        applyWorkOrderChanges(List.of(event));
    }

    @Transactional
    public void applyWorkOrderChanges(List<WorkOrderChangedEvent> events) {
        if (events == null || events.isEmpty()) {
            return;
        }

        Map<SourceLookup, DocumentIndex> existingDocuments = existingDocuments(events.stream()
                .filter(event -> event != null && !isDeleted(event.eventType()))
                .map(event -> new SourceLookup(SOURCE_WORK_ORDER, required(event.sourceId(), "sourceId")))
                .toList());
        Map<String, DocumentIndex> documentsToSave = new LinkedHashMap<>();
        Map<String, PreviewWrite> previewsToSave = new LinkedHashMap<>();

        for (WorkOrderChangedEvent event : events) {
            if (event == null) {
                continue;
            }
            String documentId = documentId(SOURCE_WORK_ORDER, event.sourceId());
            if (isDeleted(event.eventType())) {
                delete(SOURCE_WORK_ORDER, event.sourceId());
                documentsToSave.remove(documentId);
                previewsToSave.remove(documentId);
                continue;
            }
            if (!isApproved(event.statusCode())) {
                continue;
            }

            SourceLookup lookup = new SourceLookup(SOURCE_WORK_ORDER, event.sourceId());
            DocumentIndex document = documentsToSave.getOrDefault(
                    documentId,
                    existingDocuments.getOrDefault(lookup, new DocumentIndex(documentId, SOURCE_WORK_ORDER, event.sourceId()))
            );
            updateWorkOrder(document, event);
            documentsToSave.put(documentId, document);
            putPreview(previewsToSave, documentId, SOURCE_WORK_ORDER, previewPayload(event));
        }

        saveBatch(documentsToSave.values(), previewsToSave);
    }

    private void updateWorkOrder(DocumentIndex document, WorkOrderChangedEvent event) {
        String documentId = documentId(SOURCE_WORK_ORDER, event.sourceId());
        LocalDate docDate = firstNonNull(event.dueDate(), dateFrom(event.occurredAt()));

        document.update(
                required(event.projectId(), "projectId"),
                blankToDefault(event.docCode(), "WO-" + event.sourceId()),
                SOURCE_WORK_ORDER,
                blankToDefault(event.title(), "[" + blankToDefault(event.tradeName(), "WORK") + "] work order"),
                "",
                "",
                "hq",
                null,
                dateFrom(event.occurredAt()),
                docDate,
                blankToDefault(event.uploader(), "site manager"),
                "v1.0",
                "",
                blankToDefault(event.statusCode(), "OPEN"),
                event.tradeName(),
                false,
                joinText(event.title(), event.tradeName(), event.location(), event.workDetail(), event.workTime(), event.safetyContent())
        );
    }

    @Transactional
    public void apply(DailyReportChangedEvent event) {
        applyDailyReportChanges(List.of(event));
    }

    @Transactional
    public void applyDailyReportChanges(List<DailyReportChangedEvent> events) {
        if (events == null || events.isEmpty()) {
            return;
        }

        Map<SourceLookup, DocumentIndex> existingDocuments = existingDocuments(events.stream()
                .filter(event -> event != null && !isDeleted(event.eventType()))
                .map(event -> new SourceLookup(SOURCE_DAILY_REPORT, required(event.sourceId(), "sourceId")))
                .toList());
        Map<String, DocumentIndex> documentsToSave = new LinkedHashMap<>();
        Map<String, PreviewWrite> previewsToSave = new LinkedHashMap<>();

        for (DailyReportChangedEvent event : events) {
            if (event == null) {
                continue;
            }
            String documentId = documentId(SOURCE_DAILY_REPORT, event.sourceId());
            if (isDeleted(event.eventType())) {
                delete(SOURCE_DAILY_REPORT, event.sourceId());
                documentsToSave.remove(documentId);
                previewsToSave.remove(documentId);
                continue;
            }

            SourceLookup lookup = new SourceLookup(SOURCE_DAILY_REPORT, event.sourceId());
            DocumentIndex document = documentsToSave.getOrDefault(
                    documentId,
                    existingDocuments.getOrDefault(lookup, new DocumentIndex(documentId, SOURCE_DAILY_REPORT, event.sourceId()))
            );
            updateDailyReport(document, event);
            documentsToSave.put(documentId, document);
            putPreview(previewsToSave, documentId, SOURCE_DAILY_REPORT, previewPayload(event));
        }

        saveBatch(documentsToSave.values(), previewsToSave);
    }

    private void updateDailyReport(DocumentIndex document, DailyReportChangedEvent event) {
        String documentId = documentId(SOURCE_DAILY_REPORT, event.sourceId());
        LocalDate reportDate = firstNonNull(event.reportDate(), dateFrom(event.occurredAt()));
        String fileName = "[" + blankToDefault(event.tradeName(), "WORK") + "] " + reportDate + " daily report";

        document.update(
                required(event.projectId(), "projectId"),
                blankToDefault(event.docCode(), "RP-" + event.sourceId()),
                SOURCE_DAILY_REPORT,
                fileName,
                "",
                "",
                "hq",
                null,
                dateFrom(event.occurredAt()),
                reportDate,
                blankToDefault(event.uploader(), "site manager"),
                "v1.0",
                "",
                "APPROVED",
                event.tradeName(),
                false,
                joinText(event.tradeName(), event.location(), event.issue(), event.todayWork(), event.tomorrowPlan())
        );
    }

    private void delete(String sourceType, Long sourceId) {
        required(sourceId, "sourceId");
        documentIndexRepository.findBySourceTypeAndSourceId(sourceType, sourceId)
                .ifPresent(document -> {
                    previewPayloadRepository.deleteById(document.getId());
                    documentIndexRepository.delete(document);
                });
    }

    private Map<SourceLookup, DocumentIndex> existingDocuments(Collection<SourceLookup> lookups) {
        if (lookups == null || lookups.isEmpty()) {
            return Map.of();
        }

        Map<SourceLookup, DocumentIndex> existingDocuments = new LinkedHashMap<>();
        lookups.stream()
                .filter(lookup -> lookup.sourceType() != null && lookup.sourceId() != null)
                .collect(Collectors.groupingBy(
                        SourceLookup::sourceType,
                        LinkedHashMap::new,
                        Collectors.mapping(SourceLookup::sourceId, Collectors.toSet())
                ))
                .forEach((sourceType, sourceIds) ->
                        documentIndexRepository.findAllBySourceTypeAndSourceIdIn(sourceType, sourceIds)
                                .forEach(document -> existingDocuments.put(
                                        new SourceLookup(document.getSourceType(), document.getSourceId()),
                                        document
                                ))
                );
        return existingDocuments;
    }

    private void putPreview(
            Map<String, PreviewWrite> previewsToSave,
            String documentId,
            String sourceType,
            Map<String, Object> payload
    ) {
        if (payload == null || payload.isEmpty()) {
            return;
        }
        previewsToSave.put(documentId, new PreviewWrite(sourceType, payload));
    }

    private void saveBatch(Collection<DocumentIndex> documents, Map<String, PreviewWrite> previews) {
        if (documents != null && !documents.isEmpty()) {
            documentIndexRepository.saveAll(documents);
        }
        savePreviewPayloads(previews);
    }

    private void savePreviewPayloads(Map<String, PreviewWrite> previews) {
        if (previews == null || previews.isEmpty()) {
            return;
        }

        Map<String, DocumentPreviewPayload> existingPayloads = previewPayloadRepository.findAllById(previews.keySet())
                .stream()
                .collect(Collectors.toMap(
                        DocumentPreviewPayload::getDocumentId,
                        Function.identity(),
                        (left, right) -> left,
                        LinkedHashMap::new
                ));

        List<DocumentPreviewPayload> payloadsToSave = new ArrayList<>();
        previews.forEach((documentId, preview) -> {
            DocumentPreviewPayload entity = existingPayloads.getOrDefault(
                    documentId,
                    new DocumentPreviewPayload(documentId, preview.sourceType())
            );
            entity.update(toJson(preview.payload()));
            payloadsToSave.add(entity);
        });
        previewPayloadRepository.saveAll(payloadsToSave);
    }

    private Map<String, Object> previewPayload(WorkOrderChangedEvent event) {
        if (event.previewPayload() != null && !event.previewPayload().isEmpty()) {
            return event.previewPayload();
        }
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("idx", event.sourceId());
        payload.put("tradeType", event.tradeName());
        payload.put("title", event.title());
        payload.put("workDetail", event.workDetail());
        payload.put("workTime", event.workTime());
        payload.put("safetyContent", event.safetyContent());
        payload.put("dueDate", event.dueDate());
        payload.put("workerCount", event.workerCount());
        payload.put("statusCode", blankToDefault(event.statusCode(), "OPEN"));
        payload.put("location", event.location());
        payload.put("equipments", java.util.List.of());
        return payload;
    }

    private Map<String, Object> previewPayload(DailyReportChangedEvent event) {
        if (event.previewPayload() != null && !event.previewPayload().isEmpty()) {
            return event.previewPayload();
        }
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("idx", event.sourceId());
        payload.put("process", event.tradeName());
        payload.put("tradeType", event.tradeName());
        payload.put("reportDate", event.reportDate());
        payload.put("actualProgress", event.actualProgress());
        payload.put("todayProgress", event.todayProgress());
        payload.put("actualWorkerCount", event.actualWorkerCount());
        payload.put("location", event.location());
        payload.put("issue", event.issue());
        payload.put("todayWork", event.todayWork());
        payload.put("tomorrowPlan", event.tomorrowPlan());
        return payload;
    }

    private String toJson(Map<String, Object> payload) {
        try {
            return objectMapper.writeValueAsString(payload);
        } catch (JsonProcessingException e) {
            throw new IllegalArgumentException("Cannot serialize document preview payload.", e);
        }
    }

    private String documentId(String sourceType, Long sourceId) {
        required(sourceId, "sourceId");
        return switch (sourceType) {
            case SOURCE_WORK_ORDER -> "WO-" + sourceId;
            case SOURCE_DAILY_REPORT -> "RP-" + sourceId;
            case SOURCE_TRADE_PLAN -> "MS-" + sourceId;
            default -> sourceType + "-" + sourceId;
        };
    }

    private boolean isDeleted(String eventType) {
        return eventType != null && eventType.toUpperCase().contains("DELETED");
    }

    private boolean isApproved(String statusCode) {
        return "APPROVED".equalsIgnoreCase(blankToDefault(statusCode, ""));
    }

    private LocalDate dateFrom(java.time.LocalDateTime value) {
        return value == null ? LocalDate.now() : value.toLocalDate();
    }

    @SafeVarargs
    private final <T> T firstNonNull(T... values) {
        for (T value : values) {
            if (value != null) {
                return value;
            }
        }
        return null;
    }

    private String extension(String fileName) {
        if (fileName == null || !fileName.contains(".")) {
            return "";
        }
        return fileName.substring(fileName.lastIndexOf('.') + 1).toLowerCase();
    }

    private String joinText(String... values) {
        return Stream.of(values)
                .filter(Objects::nonNull)
                .map(String::trim)
                .filter(value -> !value.isBlank())
                .collect(Collectors.joining(" "));
    }

    private String blankToDefault(String value, String defaultValue) {
        return value == null || value.isBlank() ? defaultValue : value.trim();
    }

    private <T> T required(T value, String fieldName) {
        if (value == null) {
            throw new IllegalArgumentException(fieldName + " is required.");
        }
        return value;
    }

    private record SourceLookup(String sourceType, Long sourceId) {
    }

    private record PreviewWrite(String sourceType, Map<String, Object> payload) {
    }
}
