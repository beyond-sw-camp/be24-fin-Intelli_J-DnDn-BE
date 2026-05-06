package org.example.dndn.document_management.model;

import lombok.Builder;
import lombok.Getter;
import org.example.dndn.common.model.BaseResponse;
import org.example.dndn.project.model.entity.MasterSchedule;
import org.example.dndn.project.model.enums.DocType;
import org.springframework.web.bind.annotation.GetMapping;

import java.time.LocalDateTime;
import java.util.Date;

public class DocumentManagementDto {

    @Getter
    @Builder
    public static class ReadRes{
        private Long idx;
        private Long project_id;
        private DocType docType;
        private String fileUrl;
        private String fileName;
        private LocalDateTime createAt;

        public static ReadRes from(MasterSchedule entity) {
            return ReadRes.builder()
                    .idx(entity.getIdx())
                    .project_id(entity.getProject().getIdx())
                    .docType(entity.getDocType())
                    .fileUrl(entity.getFileUrl())
                    .fileName(entity.getFileName())
                    .createAt(entity.getCreatedAt())
                    .build();
        }
    }
}
