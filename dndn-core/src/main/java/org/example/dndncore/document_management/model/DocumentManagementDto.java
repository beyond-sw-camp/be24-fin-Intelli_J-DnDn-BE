package org.example.dndncore.document_management.model;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Builder;
import lombok.Getter;
import lombok.Setter;
import org.example.dndncore.project.model.entity.MasterSchedule;
import org.example.dndncore.project.model.entity.Project;
import org.example.dndncore.project.model.enums.DocType;
import org.springframework.core.io.Resource;
import org.springframework.data.domain.Page;
import org.springframework.web.multipart.MultipartFile;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

public class DocumentManagementDto {

    @Getter
    @Builder
    @Schema(description = "문서 페이지 응답")
    public static class PageRes {
        @Schema(description = "문서 목록")
        private List<ReadRes> content;
        @Schema(description = "현재 페이지", example = "0")
        private int currentPage;
        @Schema(description = "총 페이지 수", example = "5")
        private int totalPages;
        @Schema(description = "총 데이터 수", example = "42")
        private long totalElements;
        @Schema(description = "페이지 크기", example = "10")
        private int size;
        @Schema(description = "첫 페이지 여부", example = "true")
        private boolean isFirst;
        @Schema(description = "마지막 페이지 여부", example = "false")
        private boolean isLast;

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
    @Schema(description = "문서 조회 응답")
    public static class ReadRes {
        @Schema(description = "문서 ID", example = "1")
        public Long idx;

        // feat : 현장 번호
        @Schema(description = "프로젝트 ID", example = "1")
        public Long project_id;

        // feat : 문서 종류
        @Schema(description = "문서 종류", example = "MASTER")
        public DocType docType;

        // feat : 문서 이름
        @Schema(description = "문서 파일명", example = "master-schedule.xlsx")
        public String fileName;

        // feat : 파일 저장 경로
        @Schema(description = "파일 URL", example = "https://example.com/file.xlsx")
        public String fileUrl;

        // feat : 협력사 여부
        @Schema(description = "협력사 문서 여부", example = "false")
        public Boolean isPartner;

        // feat : 소속 명칭
        @Schema(description = "소속 명칭", example = "본사")
        public String affiliationName;

        // feat : 작성자 이름
        @Schema(description = "작성자 이름", example = "홍길동")
        public String name;

        // feat : 업로드 일자
        @Schema(description = "생성 시각")
        public LocalDateTime createAt;

        public static ReadRes from(MasterSchedule entity) {
            return ReadRes.builder()
                    .idx(entity.getIdx())
                    .project_id(entity.getProject().getIdx())
                    .docType(entity.getDocType())
                    .fileName(entity.getFileName())
                    .fileUrl(entity.getFileUrl())
                    .createAt(entity.getCreatedAt())
                    .isPartner(entity.isPartner)
                    .affiliationName(entity.getAffiliationName())
                    .name(entity.getName())
                    .build();
        }
    }

    @Getter
    @Builder
    @Schema(description = "업로드 문서 페이지 응답")
    public static class UploadedDocumentPageRes {
        @Schema(description = "업로드 문서 목록")
        private List<UploadedDocumentRes> content;
        @Schema(description = "현재 페이지", example = "0")
        private int currentPage;
        @Schema(description = "총 페이지 수", example = "3")
        private int totalPages;
        @Schema(description = "총 데이터 수", example = "25")
        private long totalElements;
        @Schema(description = "페이지 크기", example = "10")
        private int size;
        @Schema(description = "첫 페이지 여부", example = "true")
        private boolean isFirst;
        @Schema(description = "마지막 페이지 여부", example = "false")
        private boolean isLast;
    }

    @Getter
    @Builder
    @Schema(description = "업로드 문서 응답")
    public static class UploadedDocumentRes {
        @Schema(description = "문서 식별자", example = "ms-1")
        private String id;
        @Schema(description = "원천 타입", example = "MASTER_SCHEDULE")
        private String sourceType;
        @Schema(description = "원천 ID", example = "1")
        private Long sourceId;
        @Schema(description = "문서 코드", example = "DOC-001")
        private String docCode;
        @Schema(description = "문서 타입 코드", example = "MASTER")
        private String docTypeCode;
        @Schema(description = "파일명", example = "master-schedule.xlsx")
        private String fileName;
        @Schema(description = "확장자", example = "xlsx")
        private String fileExt;
        @Schema(description = "파일 URL")
        private String fileUrl;
        @Schema(description = "문서 출처", example = "UPLOAD")
        private String origin;
        @Schema(description = "협력사명", example = "협력사A")
        private String partnerName;
        @Schema(description = "업로드 일자", example = "2026-05-27")
        private LocalDate uploadDate;
        @Schema(description = "문서 기준일", example = "2026-05-27")
        private LocalDate docDate;
        @Schema(description = "업로더", example = "홍길동")
        private String uploader;
        @Schema(description = "버전", example = "v1")
        private String version;
        @Schema(description = "파일 크기", example = "2MB")
        private String fileSize;
        @Schema(description = "상태 코드", example = "ACTIVE")
        private String statusCode;
        @Schema(description = "공종명", example = "토목")
        private String tradeName;
        @Schema(description = "다운로드 가능 여부", example = "true")
        private boolean downloadable;
        @Schema(description = "원본 데이터")
        private Map<String, Object> raw;
    }

    @Setter
    @Getter
    @Schema(description = "문서 업로드 요청")
    public static class UploadReq {
        // feat : 현장 프로젝트 번호
        @Schema(description = "프로젝트 ID", example = "1")
        public Long projectId;
        // feat : 업로드 파일
        @Schema(description = "업로드 파일")
        public MultipartFile file;
        // feat : 문서 종류
        @Schema(description = "문서 종류", example = "MASTER")
        public DocType docType;
        // feat : 협력사 여부
        @Schema(description = "협력사 문서 여부", example = "false")
        public Boolean isPartner;
        // feat : 소속 명칭
        @Schema(description = "소속 명칭", example = "본사")
        public String affiliationName;
        // feat : 작성자 이름
        @Schema(description = "작성자 이름", example = "홍길동")
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
    @Schema(description = "문서 다운로드 응답")
    public static class DownloadRes {
        @Schema(description = "다운로드 리소스")
        private Resource resource;
        @Schema(description = "파일명", example = "master-schedule.xlsx")
        private String fileName;
    }
}
