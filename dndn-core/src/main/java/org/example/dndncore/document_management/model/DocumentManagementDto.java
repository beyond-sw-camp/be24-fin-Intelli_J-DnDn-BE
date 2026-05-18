package org.example.dndncore.document_management.model;

import lombok.Builder;
import lombok.Getter;
import lombok.Setter;
import org.example.dndncore.common.model.BaseResponse;
import org.example.dndncore.project.model.entity.MasterSchedule;
import org.example.dndncore.project.model.entity.Project;
import org.example.dndncore.project.model.enums.DocType;
import org.springframework.core.io.Resource;
import org.springframework.data.domain.Page;
import org.springframework.web.bind.annotation.GetMapping;
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
        public Long project_id;

        // 문서 종류 (MASTER, MILESTONE, WEIGHT, TRADE_PLAN)
        public DocType docType;

        // 문서 이름
        public String fileName;

        // ★ 추가: 파일 저장 경로 (프론트에서 파일 존재 여부 판별용)
        public String fileUrl;

        // 협력사 여부
        public Boolean isPartner;

        // 소속 명칭 (본사 or 협력사 이름)
        public String affiliationName;

        // 작성자 이름
        public String name;

        // 업로드 일자
        public LocalDateTime createAt;

        public static ReadRes from(MasterSchedule entity) {
            return ReadRes.builder()
                    .idx(entity.getIdx())
                    .project_id(entity.getProject().getIdx())
                    .docType(entity.getDocType())
                    .fileName(entity.getFileName())
                    .fileUrl(entity.getFileUrl())       // ★ 추가
                    .createAt(entity.getCreatedAt())
                    .isPartner(entity.isPartner)
                    .affiliationName(entity.getAffiliationName())
                    .name(entity.getName())
                    .build();
        }
    }

    @Setter
    @Getter
    public static class UploadReq {
        // 현장 프로젝트 번호
        public Long projectId;
        // 문서 파일
        public MultipartFile file;
        // 문서 종류 (마스터 공정표, 마일스톤 공정표, 보할 공정표, 공종별 세북 걔획서)
        public DocType docType;
        // 협력사 여부
        public Boolean isPartner;
        // 소속 명칭 (본사 or 협력사 이름)
        public String affiliationName;
        // 작성자 이름
        public String name;

        public MasterSchedule toEntity(Project project, String fileUrl) {
            return MasterSchedule.builder()
                    .project(project)
                    .docType(docType)
                    .fileUrl(fileUrl)
                    .fileName(file.getOriginalFilename())
                    .isPartner(isPartner)
                    .affiliationName(affiliationName)
                    .name(name)
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
