package org.example.dndn.project.model.dto;

import lombok.*;
import org.example.dndn.project.model.entity.Project;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;

public class ProjectDto {

    private static final DateTimeFormatter DATE_FORMATTER = DateTimeFormatter.ofPattern("yyyy.MM.dd");

    @Getter @Setter @AllArgsConstructor @NoArgsConstructor @Builder
    public static class Req {
        private String name;
        private String location;
        private LocalDate startDate;
        private LocalDate endDate;

        public Project toEntity() {
            return Project.builder()
                    .name(this.name)
                    .location(this.location)
                    .startDate(this.startDate)
                    .endDate(this.endDate)
                    .build();
        }
    }

    @Getter @NoArgsConstructor @AllArgsConstructor @Builder
    public static class Res {
        private Long idx;
        private String name;
        private String location;
        private LocalDate startDate;
        private LocalDate endDate;
        private String period;

        public static Res from(Project entity) {
            String period = "";
            if (entity.getStartDate() != null && entity.getEndDate() != null) {
                period = entity.getStartDate().format(DATE_FORMATTER)
                        + " ~ " + entity.getEndDate().format(DATE_FORMATTER);
            }

            return Res.builder()
                    .idx(entity.getIdx())
                    .name(entity.getName())
                    .location(entity.getLocation())
                    .startDate(entity.getStartDate())
                    .endDate(entity.getEndDate())
                    .period(period)
                    .build();
        }
    }
}