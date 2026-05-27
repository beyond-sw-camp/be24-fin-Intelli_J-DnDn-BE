package org.example.dndncore.ai.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.*;
import java.time.LocalDate;
import java.util.List;

@Schema(description = "기상관제 AI 분석 DTO")
public class WeatherAiDto {

    @Schema(description = "AI 분석 요청 데이터")
    @Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
    public static class AnalysisRequest {
        @Schema(description = "기온", example = "25.5")
        private Double temperature; // feat : 온도
        @Schema(description = "습도", example = "60")
        private Integer humidity; // feat : 습도
        @Schema(description = "풍속", example = "3.2")
        private Double windSpeed; // feat : 풍속
        @Schema(description = "강수 확률", example = "20")
        private Integer precipitationProbability; // feat : 강수 확률
        @Schema(description = "미세먼지 농도", example = "45")
        private Integer pm10; // feat : 미세먼지 농도
        @Schema(description = "초미세먼지 농도", example = "15")
        private Integer pm25; // feat : 초미세먼지 농도
        @Schema(description = "작업 목록")
        private List<WorkTaskInfo> workTasks; // feat : 작업 목록
        @Schema(description = "분석 기준일", example = "2026-05-27")
        private LocalDate analysisDate; // feat : 분석 기준일
    }

    @Schema(description = "작업 정보")
    @Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
    public static class WorkTaskInfo {
        @Schema(description = "작업명", example = "크레인 인양")
        private String title; // feat : 작업명
        @Schema(description = "작업 상세 설명", example = "자재 옥상 인양 작업")
        private String workDetail; // feat : 작업 상세 설명
        @Schema(description = "작업 장소", example = "A구역")
        private String workLocation; // feat : 작업 장소
        @Schema(description = "공종", example = "양중공사")
        private String tradeType; // feat : 공종
        @Schema(description = "필요 장비 목록")
        private List<EquipmentInfo> equipments; // feat : 필요 장비 목록
    }

    @Schema(description = "장비 정보")
    @Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
    public static class EquipmentInfo {
        @Schema(description = "장비 이름", example = "타워크레인")
        private String name; // feat : 장비 이름
        @Schema(description = "장비 분류", example = "양중장비")
        private String type; // feat : 장비 분류
        @Schema(description = "투입 수량", example = "1")
        private Integer count; // feat : 투입 수량
    }

    @Schema(description = "AI 분석 결과")
    @Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
    public static class AnalysisResult {
        @Schema(description = "도출된 위험 요소")
        private List<RiskItem> risks; // feat : 도출된 위험 요소
        @Schema(description = "권장 조치 사항")
        private List<ActionItem> actions; // feat : 권장 조치 사항
        @Schema(description = "종합 안전 등급", example = "주의")
        private String overallSafety; // feat : 종합 안전 등급
        @Schema(description = "특이사항 및 비고", example = "오후 강풍 예상")
        private String note; // feat : 특이사항 및 비고
    }

    @Schema(description = "위험 항목")
    @Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
    public static class RiskItem {
        @Schema(description = "위험 발생 대상", example = "타워크레인")
        private String target; // feat : 위험 발생 대상
        @Schema(description = "위험도 등급", example = "HIGH")
        private String level; // feat : 위험도 등급
        @Schema(description = "위험 판단 근거", example = "풍속 제한 초과 예상")
        private String reason; // feat : 위험 판단 근거
        @Schema(description = "안전 권고 사항", example = "작업 일시 중단")
        private String recommendation; // feat : 안전 권고 사항
        @Schema(description = "영향권 작업 리스트")
        private List<String> affectedWorks; // feat : 영향권 작업 리스트
    }

    @Schema(description = "즉시 조치 항목")
    @Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
    public static class ActionItem {
        @Schema(description = "실행할 조치", example = "크레인 선회 브레이크 해제")
        private String action; // feat : 실행할 조치
        @Schema(description = "조치 우선도", example = "URGENT")
        private String priority; // feat : 조치 우선도
        @Schema(description = "조치 필요 원인", example = "강풍 대비")
        private String reason; // feat : 조치 필요 원인
        @Schema(description = "담당 역할", example = "장비관리자")
        private String responsibleRole; // feat : 담당 역할
        @Schema(description = "예상 작업 시간", example = "30분")
        private String estimatedTime; // feat : 예상 작업 시간
    }
}