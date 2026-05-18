package org.example.dndncore.ai.model;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.*;

import java.time.LocalDateTime;
import java.util.Collections;
import java.util.List;
import java.util.Map;

public class AiScheduleRecommendationDto {

    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

    @Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
    public static class CreateReq {
        private Long projectId;
        private Long monthlyWorkPlanId;
    }

    @Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
    public static class CompleteReq {
        private Map<String, Object> result;
        private Map<String, Object> changeSummary;
        private List<Map<String, Object>> detailChanges;
    }

    @Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
    public static class FailReq {
        private String errorMessage;
    }

    @Getter @NoArgsConstructor @AllArgsConstructor @Builder
    public static class Res {
        private Long id;
        private Long projectId;
        private Long monthlyWorkPlanId;
        private String monthlyWorkPlanName;
        private String status;
        private Map<String, Object> context;
        private Map<String, Object> result;
        private String errorMessage;
        private LocalDateTime createdAt;
        private LocalDateTime updatedAt;

        public static Res from(AiScheduleRecommendation entity) {
            return Res.builder()
                    .id(entity.getIdx())
                    .projectId(entity.getProject() != null ? entity.getProject().getIdx() : null)
                    .monthlyWorkPlanId(entity.getMonthlyWorkPlan() != null
                            ? entity.getMonthlyWorkPlan().getIdx()
                            : null)
                    .monthlyWorkPlanName(entity.getMonthlyWorkPlan() != null
                            ? entity.getMonthlyWorkPlan().getName()
                            : "")
                    .status(entity.getStatus() != null ? entity.getStatus().name() : "")
                    .context(readObject(entity.getContextJson()))
                    .result(readObject(entity.getResultJson()))
                    .errorMessage(entity.getErrorMessage())
                    .createdAt(entity.getCreatedAt())
                    .updatedAt(entity.getUpdatedAt())
                    .build();
        }

        private static Map<String, Object> readObject(String json) {
            if (json == null || json.isBlank()) return Collections.emptyMap();

            try {
                return OBJECT_MAPPER.readValue(json, new TypeReference<>() {});
            } catch (Exception e) {
                return Collections.emptyMap();
            }
        }
    }
}
