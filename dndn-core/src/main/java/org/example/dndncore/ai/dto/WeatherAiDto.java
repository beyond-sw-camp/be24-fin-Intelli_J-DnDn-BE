package org.example.dndncore.ai.dto;

import lombok.*;
import java.time.LocalDate;
import java.util.List;

    // 기상관제 AI 분석 DTO
public class WeatherAiDto {

    // AI 분석 요청 데이터
    @Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
    public static class AnalysisRequest {
        private Double temperature;
        private Integer humidity;
        private Double windSpeed;
        private Integer precipitationProbability;
        private Integer pm10;
        private Integer pm25;
        private List<WorkTaskInfo> workTasks;
        private LocalDate analysisDate;
    }

    // 작업 정보
    @Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
    public static class WorkTaskInfo {
        private String title;
        private String workDetail;
        private String workLocation;
        private String tradeType;
        private List<EquipmentInfo> equipments;
    }

    // 장비 정보
    @Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
    public static class EquipmentInfo {
        private String name;
        private String type;
        private Integer count;
    }

    // AI 분석 결과
    @Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
    public static class AnalysisResult {
        private List<RiskItem> risks;
        private List<ActionItem> actions;
        private String overallSafety;
        private String note;
    }

    // 위험 항목
    @Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
    public static class RiskItem {
        private String target;
        private String level;
        private String reason;
        private String recommendation;
        private List<String> affectedWorks;
    }

    // 즉시 조치 항목
    @Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
    public static class ActionItem {
        private String action;
        private String priority;
        private String reason;
        private String responsibleRole;
        private String estimatedTime;
    }
}
