package org.example.dndncore.kafka;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.example.dndncore.kafka.dto.KafkaConsumerDto;
import org.example.dndncore.kafka.dto.KafkaProject;
import org.example.dndncore.kafka.dto.KafkaTradeProcess;
import org.example.dndncore.project.model.dto.TradeProcessDto;
import org.example.dndncore.project.model.entity.Project;
import org.example.dndncore.project.repository.ProjectRepository;
import org.example.dndncore.project.repository.ScheduleAiAnalysisRepository;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

import static java.awt.SystemColor.text;

@Slf4j
@RequiredArgsConstructor
@Service
public class DocumentAiKafkaListenerService {
    private final KafkaTradeProcessRepository kafkaTradeProcessRepository;
    private final ProjectRepository projectRepository;
    private final ObjectMapper objectMapper;

    /**
     * 문서 업로드 → AI 추출 결과(JSON) 수신 → kafka_trade_process 저장
     *
     * 메시지 예시:
     * {
     *   "body": {
     *     "uploaderIdx": 1,
     *     "masterScheduleIdx": 10,
     *     "projectIdx": 5,
     *     "schedules": [
     *       {
     *         "tradeName": "토공사",
     *         "processName": "기초 굴착",
     *         "weightPct": 1.5,
     *         "plannedStart": "2025-03-01",
     *         "plannedEnd": "2025-03-15",
     *         "isMilestone": false
     *       },
     *       ...
     *     ]
     *   }
     * }
     */
    @KafkaListener(topics = "document.uploaded.v3", groupId = "first-document-upload")
    @Transactional
    public void consume(String message) {
        log.info("[Kafka] document.uploaded.v3 수신: {}", message);

        try {
            JsonNode root = objectMapper.readTree(message);
            JsonNode body = root.get("body");
            if (body == null) {
                log.warn("[Kafka] body 가 비어있습니다. 메시지 무시.");
                return;
            }

            Long uploaderIdx       = asLong(body.get("uploaderIdx"));
            Long masterScheduleIdx = asLong(body.get("masterScheduleIdx"));
            Long projectIdx        = asLong(body.get("projectIdx"));
            JsonNode schedules     = body.get("schedules");

            log.info("[Kafka] uploaderIdx={}, masterScheduleIdx={}, projectIdx={}",
                    uploaderIdx, masterScheduleIdx, projectIdx);

            if (projectIdx == null) {
                throw new IllegalArgumentException("projectIdx 가 없습니다.");
            }
            if (schedules == null || !schedules.isArray() || schedules.isEmpty()) {
                log.warn("[Kafka] schedules 가 비어있어 저장할 항목이 없습니다.");
                return;
            }

            Project project = projectRepository.findById(projectIdx)
                    .orElseThrow(() -> new RuntimeException(
                            "Project 를 찾을 수 없습니다. projectIdx=" + projectIdx));

            List<KafkaTradeProcess> toSave = new ArrayList<>();
            for (JsonNode item : schedules) {
                String tradeName    = asText(item.get("tradeName"));
                String processName  = asText(item.get("processName"));

                // 공종명/공정명이 모두 없는 항목은 스킵
                if ((tradeName == null || tradeName.isBlank())
                        && (processName == null || processName.isBlank())) {
                    continue;
                }

                Float weightPct      = asFloat(item.get("weightPct"));
                LocalDate plannedStart = asDate(item.get("plannedStart"));
                System.out.println(plannedStart);
                LocalDate plannedEnd   = asDate(item.get("plannedEnd"));
                System.out.println(plannedEnd);
                Boolean isMilestone  = asBoolean(item.get("isMilestone"));

                KafkaTradeProcess tp = KafkaTradeProcess.builder()
                        .project(project)
                        .tradeName(tradeName != null ? tradeName : "")
                        .processName(processName != null ? processName : "")
                        .partnerCompany(asText(item.get("partnerCompany")))
                        .plannedStart(plannedStart)
                        .plannedEnd(plannedEnd)
                        .weightPct(weightPct)
                        .isMilestone(Boolean.TRUE.equals(isMilestone))
                        .build();

                toSave.add(tp);
            }

            if (toSave.isEmpty()) {
                log.warn("[Kafka] 저장 가능한 trade_process 가 없습니다.");
                return;
            }

            kafkaTradeProcessRepository.saveAll(toSave);
            log.info("[Kafka] kafka_trade_process 저장 완료. count={}, projectIdx={}",
                    toSave.size(), projectIdx);

        } catch (Exception e) {
            log.error("[Kafka] document.uploaded.v3 처리 실패", e);
            // 운영 환경에서는 DLQ 전송 또는 재처리 정책에 따라 throw 여부 결정.
            throw new RuntimeException("Kafka 메시지 처리 실패", e);
        }
    }

    /* ----------------------------- helpers ----------------------------- */

    private Long asLong(JsonNode node) {
        if (node == null || node.isNull()) return null;
        if (node.isNumber()) return node.asLong();
        try {
            String text = node.asText().trim();
            return text.isBlank() ? null : Long.parseLong(text);
        } catch (NumberFormatException e) {
            return null;
        }
    }

    private Float asFloat(JsonNode node) {
        if (node == null || node.isNull()) return null;
        if (node.isNumber()) return (float) node.asDouble();
        try {
            String text = node.asText().trim();
            return text.isBlank() ? null : Float.parseFloat(text);
        } catch (NumberFormatException e) {
            return null;
        }
    }

    private Boolean asBoolean(JsonNode node) {
        if (node == null || node.isNull()) return null;
        if (node.isBoolean()) return node.asBoolean();
        String text = node.asText().trim();
        if (text.isBlank()) return null;
        return Boolean.parseBoolean(text);
    }

    private String asText(JsonNode node) {
        if (node == null || node.isNull()) return null;
        String text = node.asText();
        return text == null ? null : text.trim();
    }

    private LocalDate asDate(JsonNode node) {
        // 1. 노드가 아예 없거나 null인 경우
        if (node == null || node.isNull()) return null;
        // 2. 배열 형태인 경우 [2024, 3, 1] 처리
        if (node.isArray()) {
            try {
                int year = node.get(0).asInt();
                int month = node.get(1).asInt();
                int day = node.get(2).asInt();
                return LocalDate.of(year, month, day);
            } catch (Exception e) {
                log.warn("[Kafka] 날짜 배열 파싱 실패: {}", node.toString());
                return null;
            }
        }
        String text = asText(node);
        if (text == null || text.isBlank()) return null;
        try {
            return LocalDate.parse(text);
        } catch (Exception e) {
            log.warn("[Kafka] 잘못된 날짜 형식 무시: {}", text);
            return null;
        }
    }
}
