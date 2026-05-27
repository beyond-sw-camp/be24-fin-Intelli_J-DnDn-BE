package org.example.dndncore.ai.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.*;

import java.time.LocalDate;
import java.util.List;

@Schema(description = "작업 계획 AI 추출 결과 DTO")
public class WorkPlanAiDto {

    @Schema(description = "추출 결과 매퍼")
    @Getter
    @Setter
    @NoArgsConstructor
    @AllArgsConstructor
    @Builder
    public static class ExtractionResult {
        @Schema(description = "작업 항목 리스트")
        private List<Item> items; // feat : 작업 항목 리스트
    }

    @Schema(description = "단일 작업 항목 DTO")
    @Getter
    @Setter
    @NoArgsConstructor
    @AllArgsConstructor
    @Builder
    public static class Item {
        @Schema(description = "상위 공종 이름", example = "토공사")
        private String tradeName; // feat : 상위 공종 이름
        @Schema(description = "상세 공정 이름", example = "터파기 및 토사반출")
        private String tradeProcessName; // feat : 상세 공정 이름
        @Schema(description = "구체적 작업명", example = "터파기 1차 굴착 및 토사반출")
        private String name; // feat : 구체적 작업명
        @Schema(description = "작업 수행 위치", example = "지하 굴착부 1구간")
        private String location; // feat : 작업 수행 위치
        @Schema(description = "작업 시작 일자", example = "2026-05-01")
        private LocalDate startDate; // feat : 작업 시작 일자
        @Schema(description = "작업 종료 일자", example = "2026-05-14")
        private LocalDate endDate; // feat : 작업 종료 일자
        @Schema(description = "특이사항 메모", example = "우천 시 연기")
        private String note; // feat : 특이사항 메모
    }
}