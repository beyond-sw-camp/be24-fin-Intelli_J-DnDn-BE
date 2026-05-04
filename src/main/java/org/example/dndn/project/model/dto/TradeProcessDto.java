package org.example.dndn.project.model.dto;

import lombok.*;
import org.example.dndn.project.model.entity.TradeProcess;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;

public class TradeProcessDto {

    private static final DateTimeFormatter DATE_FORMATTER = DateTimeFormatter.ofPattern("yyyy.MM.dd");

    @Getter @Setter @AllArgsConstructor @NoArgsConstructor @Builder
    public static class Req {
        private Long masterScheduleId;
        private String tradeName;
        private String processName;
        private String partnerCompany;
        private LocalDate plannedStart;
        private LocalDate plannedEnd;
        private Float weightPct;
        private Boolean isMilestone;
    }

    @Getter @NoArgsConstructor @AllArgsConstructor @Builder
    public static class Res {
        private Long idx;
        private Long masterScheduleId;
        private Long projectId;
        private String tradeName;
        private String processName;
        private String partnerCompany;
        private LocalDate plannedStart;
        private LocalDate plannedEnd;
        private String period;
        private Float weightPct;
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