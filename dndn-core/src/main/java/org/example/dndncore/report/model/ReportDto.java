package org.example.dndncore.report.model;

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
    public static class TomorrowEqDto {
        private String type;
        private Integer count;
    }

    @Getter
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class Req {
        @NotNull(message = "workPlanId is required")
        private Long workPlanId;

        @NotNull(message = "actualProgress is required")
        private Double actualProgress;

        private Double todayProgress;

        private Long monthlyWorkPlanId;
        private Double progressIncrementPct;
        private Double monthlyProgressPct;

        @NotNull(message = "actualWorkerCount is required")
        private Integer actualWorkerCount;

        private String location;

        @NotBlank(message = "issue is required")
        private String issue;

        @NotNull(message = "reportDate is required")
        private LocalDate reportDate;

        private String todayWork;
        private String tomorrowPlan;

        private Long tomorrowWorkPlanId;
        private Integer tomorrowWorkerCount;
        private List<TomorrowEqDto> tomorrowEquipments;
    }

    @Getter
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class Res {
        private Long idx;
        private Long workPlanId;
        private String process;
        private Double actualProgress;
        private Double todayProgress;
        private Long monthlyWorkPlanId;
        private Double progressIncrementPct;
        private Double monthlyProgressPct;
        private Integer actualWorkerCount;
        private String location;
        private String issue;
        private LocalDate reportDate;
        private String todayWork;
        private String tomorrowPlan;
    }
}
