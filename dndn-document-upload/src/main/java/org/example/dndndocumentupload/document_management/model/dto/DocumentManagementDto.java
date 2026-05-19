package org.example.dndndocumentupload.document_management.model.dto;

import lombok.Builder;
import lombok.Getter;
import lombok.Setter;
import org.example.dndndocumentupload.document_management.model.entity.MasterSchedule;
import org.example.dndndocumentupload.document_management.model.enums.DocType;
import org.springframework.core.io.Resource;
import org.springframework.data.domain.Page;
import org.springframework.web.multipart.MultipartFile;

import java.time.LocalDateTime;
import java.util.List;

public class DocumentManagementDto {

    @Getter
    @Builder
    public static class PageRes {
        private List<ReadRes> content;       // 실제 데이터 10개
        private int currentPage;             // 현재 페이지 (0부터 시작)
        private int totalPages;              // 총 페이지 수
        private long totalElements;          // 총 데이터 개수
        private int size;                    // 페이지당 개수
        private boolean isFirst;             // 첫 페이지 여부
        private boolean isLast;              // 마지막 페이지 여부

        public static PageRes from(Page<MasterSchedule> page) {
            return PageRes.builder()
                    .content(page.getContent().stream()
                            .map(ReadRes::from)
                            .toList())
                    .currentPage(page.getNumber())
                    .totalPages(page.getTotalPages())
                    .totalElements(page.getTotalElements())
                    .size(page.getSize())
                    .isFirst(page.isFirst())
                    .isLast(page.isLast())
                    .build();
        }
    }

    @Getter
    @Builder
    public static class ReadRes {
        public Long idx;

        // 현장 번호
        public Long projectIdx;

        // 문서 종류 (MASTER, MILESTONE, WEIGHT, TRADE_PLAN, WORK_PLAN, DAILY_REPORT)
        public DocType docType;

        // 문서 이름
        public String fileName;

        // 협력사 여부
        public Boolean isPartner;

        // 소속 명칭 (본사 or 협력사 이름)
        public String affiliationName;

        // 작성자 이름
        public String uploaderName;

        // 공종 이름
        public String tradeName;

        // 업로드 일자
        public LocalDateTime createAt;

        public static ReadRes from(MasterSchedule entity) {
            return ReadRes.builder()
                    .idx(entity.getIdx())
                    .projectIdx(entity.getProjectIdx())
                    .docType(entity.getDocType())
                    .fileName(entity.getFileName())
                    .createAt(entity.getCreatedAt())
                    .isPartner(entity.isPartner)
                    .affiliationName(entity.getAffiliationName())
                    .uploaderName(entity.getUploaderName())
                    .tradeName(entity.getTradeName())
                    .build();
        }
    }

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

    @Getter
    @Builder
    public static class DownloadRes {
        private Resource resource;
        private String fileName;
    }
}
