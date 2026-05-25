package org.example.dndncore.document_event;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.example.dndncore.auth.model.entity.SystemUser;
import org.example.dndncore.auth.repository.SystemUserRepository;
import org.example.dndncore.document_event.dto.DailyReportChangedEvent;
import org.example.dndncore.document_event.dto.DocumentUploadedEvent;
import org.example.dndncore.document_event.dto.WorkOrderChangedEvent;
import org.example.dndncore.project.model.entity.MasterSchedule;
import org.example.dndncore.project.model.entity.TradeProcess;
import org.example.dndncore.report.model.DailyReport;
import org.example.dndncore.workorder.model.WorkOrder;
import org.example.dndncore.workplan.model.entity.WorkPlan;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

@Slf4j
@Component
@RequiredArgsConstructor
public class DocumentEventProducer {

    private final KafkaTemplate<String, String> kafkaTemplate;
    private final ObjectMapper objectMapper;
    private final SystemUserRepository systemUserRepository;

    @Value("${document.kafka.topics.document-uploaded:document.uploaded.v1}")
    private String documentUploadedTopic;

    @Value("${document.kafka.topics.work-order-changed:work-order.changed.v1}")
    private String workOrderChangedTopic;

    @Value("${document.kafka.topics.daily-report-changed:daily-report.changed.v1}")
    private String dailyReportChangedTopic;

    public void publishDocumentUploaded(MasterSchedule document) {
        buildDocumentUploadedEvent(document)
                .ifPresent(event -> sendAfterCommit(documentUploadedTopic, event.docCode(), event));
    }

    public void publishWorkOrderChanged(String eventType, WorkOrder workOrder) {
        buildWorkOrderChangedEvent(eventType, workOrder)
                .ifPresent(event -> sendAfterCommit(workOrderChangedTopic, event.docCode(), event));
    }

    public void publishDailyReportChanged(String eventType, DailyReport report) {
        buildDailyReportChangedEvent(eventType, report)
                .ifPresent(event -> sendAfterCommit(dailyReportChangedTopic, event.docCode(), event));
    }

    private Optional<DocumentUploadedEvent> buildDocumentUploadedEvent(MasterSchedule document) {
        if (document == null || document.getIdx() == null) {
            return Optional.empty();
        }

        Long projectId = document.getProject() != null ? document.getProject().getIdx() : null;
        if (projectId == null) {
            log.warn("Skip document uploaded event because projectId is missing. sourceId={}", document.getIdx());
            return Optional.empty();
        }
        String docTypeCode = document.getDocType() != null ? document.getDocType().name() : "TRADE_PLAN";
        LocalDate createdDate = document.getCreatedAt() != null ? document.getCreatedAt().toLocalDate() : LocalDate.now();

        return Optional.of(new DocumentUploadedEvent(
                UUID.randomUUID().toString(),
                "DOCUMENT_UPLOADED",
                LocalDateTime.now(),
                projectId,
                document.getIdx(),
                docCode(docTypeCode, document.getIdx()),
                docTypeCode,
                document.getFileName(),
                extension(document.getFileName()),
                document.getFileUrl(),
                Boolean.TRUE.equals(document.isPartner) ? "partner" : "hq",
                Boolean.TRUE.equals(document.isPartner) ? document.getAffiliationName() : null,
                createdDate,
                createdDate,
                document.getName(),
                "v1.0",
                "",
                "APPROVED",
                null,
                true,
                document.getFileName(),
                Map.of()
        ));
    }

    private Optional<WorkOrderChangedEvent> buildWorkOrderChangedEvent(String eventType, WorkOrder workOrder) {
        if (workOrder == null || workOrder.getIdx() == null) {
            return Optional.empty();
        }
        if (workOrder.getSiteIdx() == null) {
            log.warn("Skip work order event because projectId is missing. sourceId={}", workOrder.getIdx());
            return Optional.empty();
        }

        String uploader = currentUserName();
        Map<String, Object> previewPayload = new LinkedHashMap<>();
        previewPayload.put("idx", workOrder.getIdx());
        previewPayload.put("tradeType", workOrder.getTradeType());
        previewPayload.put("title", workOrder.getTitle());
        previewPayload.put("instructionContent", workOrder.getInstructionContent());
        previewPayload.put("workDetail", workOrder.getWorkDetail());
        previewPayload.put("workTime", workOrder.getWorkTime());
        previewPayload.put("safetyContent", workOrder.getSafetyContent());
        previewPayload.put("dueDate", workOrder.getDueDate());
        previewPayload.put("workerCount", workOrder.getWorkerCount());
        previewPayload.put("statusCode", blankToDefault(workOrder.getStatusCode(), "OPEN"));
        previewPayload.put("uploader", uploader);
        previewPayload.put("equipments", workOrder.getEquipments() == null
                ? java.util.List.of()
                : workOrder.getEquipments().stream()
                .map(eq -> Map.of(
                        "equipmentName", blankToDefault(eq.getEquipmentName(), ""),
                        "equipmentCount", eq.getEquipmentCount() == null ? 0 : eq.getEquipmentCount()
                ))
                .toList());

        return Optional.of(new WorkOrderChangedEvent(
                UUID.randomUUID().toString(),
                eventType,
                LocalDateTime.now(),
                workOrder.getSiteIdx(),
                workOrder.getIdx(),
                "WO-" + workOrder.getIdx(),
                workOrder.getTitle(),
                workOrder.getTradeType(),
                workOrder.getDueDate(),
                uploader,
                blankToDefault(workOrder.getStatusCode(), "OPEN"),
                firstNonBlank(workOrder.getWorkDetail(), workOrder.getInstructionContent()),
                workOrder.getWorkTime(),
                workOrder.getSafetyContent(),
                workOrder.getWorkerCount(),
                null,
                previewPayload
        ));
    }

    private Optional<DailyReportChangedEvent> buildDailyReportChangedEvent(String eventType, DailyReport report) {
        if (report == null || report.getIdx() == null) {
            return Optional.empty();
        }

        WorkPlan workPlan = report.getWorkPlan();
        String tradeName = tradeName(workPlan);
        String location = firstNonBlank(report.getLocation(), workPlan != null ? workPlan.getLocation() : null);
        Long projectId = projectId(workPlan);
        if (projectId == null) {
            log.warn("Skip daily report event because projectId is missing. sourceId={}", report.getIdx());
            return Optional.empty();
        }

        String uploader = currentUserName();
        Map<String, Object> previewPayload = new LinkedHashMap<>();
        previewPayload.put("idx", report.getIdx());
        previewPayload.put("process", tradeName);
        previewPayload.put("tradeType", tradeName);
        previewPayload.put("reportDate", report.getReportDate());
        previewPayload.put("actualProgress", report.getActualProgress());
        previewPayload.put("todayProgress", report.getTodayProgress());
        previewPayload.put("actualWorkerCount", report.getActualWorkerCount());
        previewPayload.put("location", location);
        previewPayload.put("issue", report.getIssue());
        previewPayload.put("todayWork", report.getTodayWork());
        previewPayload.put("tomorrowPlan", report.getTomorrowPlan());
        previewPayload.put("uploader", uploader);

        return Optional.of(new DailyReportChangedEvent(
                UUID.randomUUID().toString(),
                eventType,
                LocalDateTime.now(),
                projectId,
                report.getIdx(),
                "RP-" + report.getIdx(),
                tradeName,
                report.getReportDate(),
                uploader,
                report.getActualProgress(),
                report.getTodayProgress(),
                report.getActualWorkerCount(),
                location,
                report.getIssue(),
                report.getTodayWork(),
                report.getTomorrowPlan(),
                previewPayload
        ));
    }

    private void sendAfterCommit(String topic, String key, Object event) {
        Runnable send = () -> sendNow(topic, key, event);
        if (!TransactionSynchronizationManager.isSynchronizationActive()) {
            send.run();
            return;
        }

        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override
            public void afterCommit() {
                send.run();
            }
        });
    }

    private void sendNow(String topic, String key, Object event) {
        try {
            kafkaTemplate.send(topic, key, objectMapper.writeValueAsString(event));
        } catch (JsonProcessingException e) {
            log.warn("Document event serialization failed. topic={}, key={}", topic, key, e);
        } catch (RuntimeException e) {
            log.warn("Document event publish failed. topic={}, key={}", topic, key, e);
        }
    }

    private String docCode(String docTypeCode, Long id) {
        String prefix = switch (docTypeCode) {
            case "MASTER" -> "MS";
            case "MILESTONE" -> "ML";
            case "WEIGHT" -> "WT";
            case "TRADE_PLAN" -> "TP";
            default -> "DOC";
        };
        return prefix + "-" + id;
    }

    private String extension(String fileName) {
        if (fileName == null || !fileName.contains(".")) {
            return "";
        }
        return fileName.substring(fileName.lastIndexOf('.') + 1).toLowerCase();
    }

    private String tradeName(WorkPlan workPlan) {
        TradeProcess tradeProcess = tradeProcess(workPlan);
        if (tradeProcess != null && tradeProcess.getTradeName() != null) {
            return tradeProcess.getTradeName();
        }
        if (workPlan != null && workPlan.getTrade() != null) {
            return workPlan.getTrade().getLabel();
        }
        return null;
    }

    private Long projectId(WorkPlan workPlan) {
        TradeProcess tradeProcess = tradeProcess(workPlan);
        if (tradeProcess == null
                || tradeProcess.getMasterSchedule() == null
                || tradeProcess.getMasterSchedule().getProject() == null) {
            return null;
        }
        return tradeProcess.getMasterSchedule().getProject().getIdx();
    }

    private TradeProcess tradeProcess(WorkPlan workPlan) {
        if (workPlan == null) {
            return null;
        }
        if (workPlan.getTradeProcess() != null) {
            return workPlan.getTradeProcess();
        }
        if (workPlan.getParentWorkPlan() != null) {
            return workPlan.getParentWorkPlan().getTradeProcess();
        }
        return null;
    }

    private String currentUserName() {
        var authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null || !authentication.isAuthenticated()) {
            return "system";
        }

        Object principal = authentication.getPrincipal();
        if (principal instanceof Long userIdx) {
            return systemUserRepository.findById(userIdx)
                    .map(SystemUser::getName)
                    .filter(name -> name != null && !name.isBlank())
                    .orElse("system");
        }

        String name = authentication.getName();
        return name == null || name.isBlank() ? "system" : name;
    }

    private String firstNonBlank(String... values) {
        if (values == null) {
            return null;
        }
        for (String value : values) {
            if (value != null && !value.isBlank()) {
                return value;
            }
        }
        return null;
    }

    private String blankToDefault(String value, String defaultValue) {
        return value == null || value.isBlank() ? defaultValue : value;
    }
}
