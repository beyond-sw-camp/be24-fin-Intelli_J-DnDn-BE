package org.example.dndn.project.model.dto;

import lombok.*;
import org.example.dndn.project.model.entity.MasterSchedule;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

public class MasterScheduleDto {

    private static final DateTimeFormatter DT_FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm");

    @Getter @Setter @AllArgsConstructor @NoArgsConstructor @Builder
    public static class Req {
        private Long projectId;
        private String docType;     // "마스터 공정표" | "마일스톤 공정표" | "보할 공정표" | "공종별 시공계획서"
        private String fileUrl;
        private String fileName;
    }

    @Getter @NoArgsConstructor @AllArgsConstructor @Builder
    public static class Res {
        private Long idx;
        private Long projectId;
        private String projectName;
        private String docType;     // 한글 라벨
        private String fileUrl;
        private String fileName;
        private String uploadedAt;  // 표시용

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