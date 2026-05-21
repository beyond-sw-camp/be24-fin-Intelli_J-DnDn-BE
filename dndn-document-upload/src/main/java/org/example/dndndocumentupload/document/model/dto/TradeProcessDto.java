package org.example.dndndocumentupload.document.model.dto;

import lombok.*;

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
}