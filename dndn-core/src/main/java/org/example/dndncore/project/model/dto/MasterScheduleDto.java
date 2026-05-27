package org.example.dndncore.project.model.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.*;
import org.example.dndncore.project.model.entity.MasterSchedule;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

public class MasterScheduleDto {

    private static final DateTimeFormatter DT_FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm");

    @Getter @Setter @AllArgsConstructor @NoArgsConstructor @Builder
    @Schema(description = "마스터 공정표 요청")
    public static class Req {
        @Schema(description = "프로젝트 ID", example = "1")
        private Long projectId;
        @Schema(description = "문서 타입", example = "MASTER")
        private String docType;
        @Schema(description = "파일 URL")
        private String fileUrl;
        @Schema(description = "파일명", example = "master-schedule.xlsx")
        private String fileName;
    }

    @Getter @NoArgsConstructor @AllArgsConstructor @Builder
    @Schema(description = "마스터 공정표 응답")
    public static class Res {
        @Schema(description = "공정표 ID", example = "1")
        private Long idx;
        @Schema(description = "프로젝트 ID", example = "1")
        private Long projectId;
        @Schema(description = "프로젝트명", example = "OO아파트 신축공사")
        private String projectName;
        @Schema(description = "문서 타입 라벨", example = "마스터 공정표")
        private String docType;
        @Schema(description = "파일 URL")
        private String fileUrl;
        @Schema(description = "파일명", example = "master-schedule.xlsx")
        private String fileName;
        @Schema(description = "업로드 시각", example = "2026-05-27 10:00")
        private String uploadedAt;

        public static Res from(MasterSchedule entity) {
            String uploadedAt = "";
            LocalDateTime created = entity.getCreatedAt();
            if (created != null) {
                uploadedAt = created.format(DT_FORMATTER);
            }

            return Res.builder()
                    .idx(entity.getIdx())
                    .projectId(entity.getProject() != null ? entity.getProject().getIdx() : null)
                    .projectName(entity.getProject() != null ? entity.getProject().getName() : null)
                    .docType(entity.getDocType() != null ? entity.getDocType().getLabel() : null)
                    .fileUrl(entity.getFileUrl())
                    .fileName(entity.getFileName())
                    .uploadedAt(uploadedAt)
                    .build();
        }
    }
}