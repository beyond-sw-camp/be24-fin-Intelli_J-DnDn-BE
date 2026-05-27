package org.example.dndncore.report.model;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.*;

import java.time.LocalDate;
import java.util.List;

public class ReportDto {

    @Getter
    @Setter
    @NoArgsConstructor
    @AllArgsConstructor
    @Schema(description = "내일 투입 장비 정보")
    public static class TomorrowEqDto {
        @Schema(description = "장비 종류", example = "크레인")
        private String type;
        @Schema(description = "장비 수량", example = "2")
        private Integer count;
    }

    @Getter
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    @Schema(description = "공사일보 제출 요청")
    public static class Req {
        @NotNull(message = "workPlanId is required")
        @Schema(description = "작업 계획 ID", example = "101", requiredMode = Schema.RequiredMode.REQUIRED)
        private Long workPlanId;

        @NotNull(message = "actualProgress is required")
        @Schema(description = "누적 실제 진척률", example = "45.5", requiredMode = Schema.RequiredMode.REQUIRED)
        private Double actualProgress;

        @Schema(description = "당일 진척률", example = "2.3")
        private Double todayProgress;

        @Schema(description = "월간 작업 계획 ID", example = "21")
        private Long monthlyWorkPlanId;
        @Schema(description = "진척 증가율", example = "0.8")
        private Double progressIncrementPct;
        @Schema(description = "월간 진척률", example = "61.2")
        private Double monthlyProgressPct;

        @NotNull(message = "actualWorkerCount is required")
        @Schema(description = "실제 투입 인원 수", example = "18", requiredMode = Schema.RequiredMode.REQUIRED)
        private Integer actualWorkerCount;

        @Schema(description = "작업 위치", example = "A동 3층")
        private String location;

        @NotBlank(message = "issue is required")
        @Schema(description = "특이사항", example = "콘크리트 타설 전 거푸집 점검 완료", requiredMode = Schema.RequiredMode.REQUIRED)
        private String issue;

        @NotNull(message = "reportDate is required")
        @Schema(description = "공사일보 기준 일자", example = "2026-05-27", requiredMode = Schema.RequiredMode.REQUIRED)
        private LocalDate reportDate;

        @Schema(description = "당일 작업 내용", example = "철근 배근 및 결속 작업 진행")
        private String todayWork;
        @Schema(description = "익일 작업 계획", example = "거푸집 설치 및 동바리 보강")
        private String tomorrowPlan;

        @Schema(description = "익일 작업 계획 ID", example = "102")
        private Long tomorrowWorkPlanId;
        @Schema(description = "익일 투입 인원 수", example = "20")
        private Integer tomorrowWorkerCount;
        @Schema(description = "익일 투입 장비 목록")
        private List<TomorrowEqDto> tomorrowEquipments;
    }

    @Getter
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    @Schema(description = "공사일보 조회 응답")
    public static class Res {
        @Schema(description = "공사일보 ID", example = "1")
        private Long idx;
        @Schema(description = "작업 계획 ID", example = "101")
        private Long workPlanId;
        @Schema(description = "공정명", example = "철근 배근")
        private String process;
        @Schema(description = "누적 실제 진척률", example = "45.5")
        private Double actualProgress;
        @Schema(description = "당일 진척률", example = "2.3")
        private Double todayProgress;
        @Schema(description = "월간 작업 계획 ID", example = "21")
        private Long monthlyWorkPlanId;
        @Schema(description = "진척 증가율", example = "0.8")
        private Double progressIncrementPct;
        @Schema(description = "월간 진척률", example = "61.2")
        private Double monthlyProgressPct;
        @Schema(description = "실제 투입 인원 수", example = "18")
        private Integer actualWorkerCount;
        @Schema(description = "작업 위치", example = "A동 3층")
        private String location;
        @Schema(description = "특이사항", example = "콘크리트 타설 전 거푸집 점검 완료")
        private String issue;
        @Schema(description = "공사일보 기준 일자", example = "2026-05-27")
        private LocalDate reportDate;
        @Schema(description = "당일 작업 내용", example = "철근 배근 및 결속 작업 진행")
        private String todayWork;
        @Schema(description = "익일 작업 계획", example = "거푸집 설치 및 동바리 보강")
        private String tomorrowPlan;
    }
}
