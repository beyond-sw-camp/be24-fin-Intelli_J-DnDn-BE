package org.example.dndncore.project.model.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.*;
import org.example.dndncore.project.model.entity.Project;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;

public class ProjectDto {

    private static final DateTimeFormatter DATE_FORMATTER = DateTimeFormatter.ofPattern("yyyy.MM.dd");

    @Getter @Setter @AllArgsConstructor @NoArgsConstructor @Builder
    @Schema(description = "프로젝트 요청")
    public static class Req {
        @Schema(description = "프로젝트명", example = "OO아파트 신축공사")
        private String name;
        @Schema(description = "현장 위치", example = "서울시 강남구")
        private String location;
        @Schema(description = "공사 시작일", example = "2026-01-01")
        private LocalDate startDate;
        @Schema(description = "공사 종료일", example = "2026-12-31")
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
    @Schema(description = "프로젝트 응답")
    public static class Res {
        @Schema(description = "프로젝트 ID", example = "1")
        private Long idx;
        @Schema(description = "프로젝트명", example = "OO아파트 신축공사")
        private String name;
        @Schema(description = "현장 위치", example = "서울시 강남구")
        private String location;
        @Schema(description = "공사 시작일", example = "2026-01-01")
        private LocalDate startDate;
        @Schema(description = "공사 종료일", example = "2026-12-31")
        private LocalDate endDate;
        @Schema(description = "표시용 기간", example = "2026.01.01 ~ 2026.12.31")
        private String period;
        @Schema(description = "활성 상태", example = "true")
        private boolean active;

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
                    .active(entity.isActive())
                    .build();
        }
    }
}