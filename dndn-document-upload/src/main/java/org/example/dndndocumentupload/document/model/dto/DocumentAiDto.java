package org.example.dndndocumentupload.document.model.dto;

import lombok.Getter;
import lombok.Setter;
import org.example.dndndocumentupload.document.model.entity.MasterSchedule;
import org.example.dndndocumentupload.document.model.DocType;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;

public class DocumentAiDto {
    public record KafkaMessage<T>(Long id, List<TradeProcessDto.Req> body) {}

    @Setter
    @Getter
    public static class UploadReq {
        // 현장 프로젝트 번호
        public Long projectIdx;
        // 문서 파일
        public MultipartFile file;
        // 문서 종류 (마스터 공정표, 마일스톤 공정표, 보할 공정표, 공종별 세북 걔획서)
        public DocType docType;
        // 협력사 여부
        public Boolean isPartner;
        // 소속 명칭 (본사 or 협력사 이름)
        public String affiliationName;
        // 업로드자 ID
        public Long uploaderIdx;
        // 업로드자 이름
        public String uploaderName;

        public MasterSchedule toEntity(String fileUrl) {
            return MasterSchedule.builder()
                    .projectIdx(projectIdx)
                    .docType(docType)
                    .fileUrl(fileUrl)
                    .fileName(file.getOriginalFilename())
                    .isPartner(isPartner)
                    .affiliationName(affiliationName)
                    .uploaderIdx(uploaderIdx)
                    .uploaderName(uploaderName )
                    .build();
        }
    }
}
