package org.example.dndncore.project.model.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.*;
import org.example.dndncore.project.model.entity.TradeProcess;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;

public class TradeProcessDto {

    private static final DateTimeFormatter DATE_FORMATTER = DateTimeFormatter.ofPattern("yyyy.MM.dd");

    @Getter @Setter @AllArgsConstructor @NoArgsConstructor @Builder
    @Schema(description = "공정 요청")
    public static class Req {
        @Schema(description = "마스터 공정표 ID", example = "1")
        private Long masterScheduleId;
        @Schema(description = "공종명", example = "골조")
        private String tradeName;
        @Schema(description = "공정명", example = "기초 철근 배근")
        private String processName;
        @Schema(description = "협력사명", example = "OO건설")
        private String partnerCompany;
        @Schema(description = "계획 시작일", example = "2026-05-01")
        private LocalDate plannedStart;
        @Schema(description = "계획 종료일", example = "2026-05-20")
        private LocalDate plannedEnd;
        @Schema(description = "보할율", example = "12.5")
        private Float weightPct;
        @Schema(description = "마일스톤 여부", example = "false")
        private Boolean isMilestone;
    }

    @Getter @NoArgsConstructor @AllArgsConstructor @Builder
    @Schema(description = "공정 응답")
    public static class Res {
        @Schema(description = "공정 ID", example = "1")
        private Long idx;
        @Schema(description = "마스터 공정표 ID", example = "1")
        private Long masterScheduleId;
        @Schema(description = "프로젝트 ID", example = "1")
        private Long projectId;
        @Schema(description = "공종명", example = "골조")
        private String tradeName;
        @Schema(description = "공정명", example = "기초 철근 배근")
        private String processName;
        @Schema(description = "협력사명", example = "OO건설")
        private String partnerCompany;
        @Schema(description = "계획 시작일", example = "2026-05-01")
        private LocalDate plannedStart;
        @Schema(description = "계획 종료일", example = "2026-05-20")
        private LocalDate plannedEnd;
        @Schema(description = "표시용 기간", example = "2026.05.01 ~ 2026.05.20")
        private String period;
        @Schema(description = "보할율", example = "12.5")
        private Float weightPct;
        @Schema(description = "마일스톤 여부", example = "false")
        private Boolean isMilestone;

        public static Res from(TradeProcess entity) {
            String period = "";
            if (entity.getPlannedStart() != null && entity.getPlannedEnd() != null) {
                period = entity.getPlannedStart().format(DATE_FORMATTER)
                        + " ~ " + entity.getPlannedEnd().format(DATE_FORMATTER);
            }

            Long projectId = null;
            if (entity.getMasterSchedule() != null && entity.getMasterSchedule().getProject() != null) {
                projectId = entity.getMasterSchedule().getProject().getIdx();
            }

            return Res.builder()
                    .idx(entity.getIdx())
                    .masterScheduleId(entity.getMasterSchedule() != null
                            ? entity.getMasterSchedule().getIdx() : null)
                    .projectId(projectId)
                    .tradeName(entity.getTradeName())
                    .processName(entity.getProcessName())
                    .partnerCompany(entity.getPartnerCompany())
                    .plannedStart(entity.getPlannedStart())
                    .plannedEnd(entity.getPlannedEnd())
                    .period(period)
                    .weightPct(entity.getWeightPct())
                    .isMilestone(entity.getIsMilestone())
                    .build();
        }
    }
}