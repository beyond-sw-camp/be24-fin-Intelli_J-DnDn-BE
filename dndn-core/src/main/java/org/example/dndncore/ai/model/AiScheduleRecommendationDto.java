package org.example.dndncore.ai.model;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.*;

import java.time.LocalDateTime;
import java.util.Collections;
import java.util.List;
import java.util.Map;

@Schema(description = "AI 스케줄 추천 관련 DTO")
public class AiScheduleRecommendationDto {

    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

    @Schema(description = "AI 스케줄 추천 생성 요청 DTO")
    @Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
    public static class CreateReq {
        @Schema(description = "프로젝트 식별자", example = "1")
        private Long projectId; // feat : 프로젝트 식별자
        @Schema(description = "월간 작업 계획 식별자", example = "10")
        private Long monthlyWorkPlanId; // feat : 월간 작업 계획 식별자
    }

    @Schema(description = "AI 스케줄 추천 완료 처리 요청 DTO")
    @Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
    public static class CompleteReq {
        @Schema(description = "분석 결과 데이터")
        private Map<String, Object> result; // feat : 분석 결과 데이터
        @Schema(description = "스케줄 변경 요약 정보")
        private Map<String, Object> changeSummary; // feat : 스케줄 변경 요약 정보
        @Schema(description = "상세 변경 내역 목록")
        private List<Map<String, Object>> detailChanges; // feat : 상세 변경 내역 목록
    }

    @Schema(description = "AI 스케줄 추천 실패 처리 요청 DTO")
    @Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
    public static class FailReq {
        @Schema(description = "에러 메시지", example = "AI 서버 응답 지연")
        private String errorMessage; // feat : 에러 메시지
    }

    @Schema(description = "AI 스케줄 추천 응답 DTO")
    @Getter @NoArgsConstructor @AllArgsConstructor @Builder
    public static class Res {
        @Schema(description = "추천 식별자", example = "100")
        private Long id; // feat : 추천 식별자
        @Schema(description = "프로젝트 식별자", example = "1")
        private Long projectId; // feat : 프로젝트 식별자
        @Schema(description = "월간 작업 계획 식별자", example = "10")
        private Long monthlyWorkPlanId; // feat : 월간 작업 계획 식별자
        @Schema(description = "월간 작업 계획명", example = "2026년 5월 계획")
        private String monthlyWorkPlanName; // feat : 월간 작업 계획명
        @Schema(description = "진행 상태", example = "COMPLETED")
        private String status; // feat : 진행 상태
        @Schema(description = "요청 컨텍스트 정보")
        private Map<String, Object> context; // feat : 요청 컨텍스트 정보
        @Schema(description = "최종 분석 결과")
        private Map<String, Object> result; // feat : 최종 분석 결과
        @Schema(description = "에러 발생 시 메시지")
        private String errorMessage; // feat : 에러 발생 시 메시지
        @Schema(description = "생성 일시")
        private LocalDateTime createdAt; // feat : 생성 일시
        @Schema(description = "수정 일시")
        private LocalDateTime updatedAt; // feat : 수정 일시

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