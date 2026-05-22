package org.example.dndndocumentmanagement.model.entity;

import org.springframework.data.annotation.Id;
import org.springframework.data.elasticsearch.annotations.Document;
import org.springframework.data.elasticsearch.annotations.Field;
import org.springframework.data.elasticsearch.annotations.FieldType;

import java.time.LocalDate;
import java.time.LocalDateTime;

@Document(indexName = "construction_index")
public class EsDocumentIndex {

    @Id
    private String id; // feat : 문서 고유 ID

    @Field(name = "project_id", type = FieldType.Long)
    private Long projectId; // feat : 프로젝트 ID

    @Field(name = "source_type", type = FieldType.Text)
    private String sourceType; // feat : 문서 출처 타입

    @Field(name = "source_id", type = FieldType.Long)
    private Long sourceId; // feat : 문서 출처 ID

    @Field(name = "document_code", type = FieldType.Text)
    private String docCode; // feat : 문서 고유 코드

    @Field(name = "document_type", type = FieldType.Text)
    private String docTypeCode; // feat : 문서 유형 코드

    @Field(name = "file_name", type = FieldType.Text, analyzer = "construction_analyzer")
    private String fileName; // feat : 파일명

    @Field(name = "content_text", type = FieldType.Text, analyzer = "construction_analyzer")
    private String contentText; // feat : 문서 본문 추출 내용

    @Field(name = "is_deleted", type = FieldType.Long)
    private Long isDeleted; // feat : 삭제 여부

    @Field(name = "es_id", type = FieldType.Text)
    private String esId; // feat : 엘라스틱서치 매핑 ID

    @Field(name = "work_location", type = FieldType.Text)
    private String workLocation; // feat : 작업 위치

    @Field(name = "site_name", type = FieldType.Text, analyzer = "construction_analyzer")
    private String siteName; // feat : 현장명

    @Field(name = "@timestamp", type = FieldType.Date)
    private LocalDateTime timestamp; // feat : 로그스태시 적재 일시

    @Field(name = "fileext", type = FieldType.Keyword)
    private String fileExt; // feat : 파일 확장자

    @Field(name = "fileurl", type = FieldType.Keyword, index = false)
    private String fileUrl; // feat : 파일 다운로드 URL

    @Field(type = FieldType.Keyword)
    private String origin; // feat : 문서 원본 출처

    @Field(name = "partnername", type = FieldType.Text, analyzer = "construction_analyzer")
    private String partnerName; // feat : 협력사명

    @Field(name = "uploaddate", type = FieldType.Date)
    private LocalDate uploadDate; // feat : 업로드 일자

    @Field(name = "docdate", type = FieldType.Date)
    private LocalDate docDate; // feat : 문서 기준 일자

    @Field(type = FieldType.Keyword)
    private String uploader; // feat : 업로더 식별자

    @Field(type = FieldType.Keyword)
    private String version; // feat : 문서 버전 정보

    @Field(name = "filesize", type = FieldType.Keyword, index = false)
    private String fileSize; // feat : 파일 크기

    @Field(name = "statuscode", type = FieldType.Keyword)
    private String statusCode; // feat : 문서 상태 코드

    @Field(name = "tradename", type = FieldType.Text, analyzer = "construction_analyzer")
    private String tradeName; // feat : 공종명

    @Field(type = FieldType.Boolean)
    private boolean downloadable; // feat : 다운로드 가능 여부

    @Field(name = "created_at", type = FieldType.Date)
    private LocalDateTime createdAt; // feat : 데이터 생성 일시

    @Field(name = "updated_at", type = FieldType.Date)
    private LocalDateTime updatedAt; // feat : 데이터 수정 일시

    protected EsDocumentIndex() {
    }

    public String getId() {
        return id;
    }

    public Long getProjectId() {
        return projectId;
    }

    public String getSourceType() {
        return sourceType;
    }

    public Long getSourceId() {
        return sourceId;
    }

    public String getDocCode() {
        return docCode;
    }

    public String getDocTypeCode() {
        return docTypeCode;
    }

    public String getFileName() {
        return fileName;
    }

    public String getContentText() {
        return contentText;
    }

    public Long getIsDeleted() {
        return isDeleted;
    }

    public String getEsId() {
        return esId;
    }

    public String getWorkLocation() {
        return workLocation;
    }

    public String getSiteName() {
        return siteName;
    }

    public LocalDateTime getTimestamp() {
        return timestamp;
    }

    public String getFileExt() {
        return fileExt;
    }

    public String getFileUrl() {
        return fileUrl;
    }

    public String getOrigin() {
        return origin;
    }

    public String getPartnerName() {
        return partnerName;
    }

    public LocalDate getUploadDate() {
        return uploadDate;
    }

    public LocalDate getDocDate() {
        return docDate;
    }

    public String getUploader() {
        return uploader;
    }

    public String getVersion() {
        return version;
    }

    public String getFileSize() {
        return fileSize;
    }

    public String getStatusCode() {
        return statusCode;
    }

    public String getTradeName() {
        return tradeName;
    }

    public boolean isDownloadable() {
        return downloadable;
    }

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }

    public LocalDateTime getUpdatedAt() {
        return updatedAt;
    }
}
