package org.example.dndndocumentmanagement.model.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;
import java.time.LocalDate;
import java.time.LocalDateTime;

@Entity
@Table(
        name = "document_index",
        indexes = {
                @Index(name = "idx_document_project_type_date", columnList = "projectId,docTypeCode,docDate"),
                @Index(name = "idx_document_project_upload_date", columnList = "projectId,uploadDate"),
                @Index(name = "idx_document_project_type_upload_date", columnList = "projectId,docTypeCode,uploadDate"),
                @Index(name = "idx_document_source", columnList = "sourceType,sourceId", unique = true),
                @Index(name = "idx_document_doc_code", columnList = "docCode")
        }
)
public class DocumentIndex {

    @Id
    @Column(length = 80)
    private String id;

    @Column(nullable = false)
    private Long projectId;

    @Column(nullable = false, length = 40)
    private String sourceType;

    @Column(nullable = false)
    private Long sourceId;

    @Column(length = 60)
    private String docCode;

    @Column(nullable = false, length = 40)
    private String docTypeCode;

    @Column(length = 255)
    private String fileName;

    @Column(length = 30)
    private String fileExt;

    @Column(length = 1000)
    private String fileUrl;

    @Column(length = 30)
    private String origin;

    @Column(length = 100)
    private String partnerName;

    private LocalDate uploadDate;

    private LocalDate docDate;

    @Column(length = 100)
    private String uploader;

    @Column(length = 30)
    private String version;

    @Column(length = 50)
    private String fileSize;

    @Column(length = 40)
    private String statusCode;

    @Column(length = 100)
    private String tradeName;

    private boolean downloadable;

    @Column(columnDefinition = "TEXT")
    private String contentText;

    @Column(nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @Column(nullable = false)
    private LocalDateTime updatedAt;

    protected DocumentIndex() {
    }

    public DocumentIndex(String id, String sourceType, Long sourceId) {
        this.id = id;
        this.sourceType = sourceType;
        this.sourceId = sourceId;
    }

    @PrePersist
    void onCreate() {
        LocalDateTime now = LocalDateTime.now();
        this.createdAt = now;
        this.updatedAt = now;
    }

    @PreUpdate
    void onUpdate() {
        this.updatedAt = LocalDateTime.now();
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

    public String getContentText() {
        return contentText;
    }

    public void update(
            Long projectId,
            String docCode,
            String docTypeCode,
            String fileName,
            String fileExt,
            String fileUrl,
            String origin,
            String partnerName,
            LocalDate uploadDate,
            LocalDate docDate,
            String uploader,
            String version,
            String fileSize,
            String statusCode,
            String tradeName,
            boolean downloadable,
            String contentText
    ) {
        this.projectId = projectId;
        this.docCode = docCode;
        this.docTypeCode = docTypeCode;
        this.fileName = fileName;
        this.fileExt = fileExt;
        this.fileUrl = fileUrl;
        this.origin = origin;
        this.partnerName = partnerName;
        this.uploadDate = uploadDate;
        this.docDate = docDate;
        this.uploader = uploader;
        this.version = version;
        this.fileSize = fileSize;
        this.statusCode = statusCode;
        this.tradeName = tradeName;
        this.downloadable = downloadable;
        this.contentText = contentText;
    }
}
