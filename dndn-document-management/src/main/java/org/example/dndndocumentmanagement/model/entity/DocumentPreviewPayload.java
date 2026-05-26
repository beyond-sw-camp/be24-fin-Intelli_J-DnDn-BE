package org.example.dndndocumentmanagement.model.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;
import java.time.LocalDateTime;

@Entity
@Table(name = "document_preview_payload")
public class DocumentPreviewPayload {

    @Id
    @Column(length = 80)
    private String documentId;

    @Column(nullable = false, length = 40)
    private String sourceType;

    @Column(columnDefinition = "TEXT")
    private String payloadJson;

    @Column(nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @Column(nullable = false)
    private LocalDateTime updatedAt;

    protected DocumentPreviewPayload() {
    }

    public DocumentPreviewPayload(String documentId, String sourceType) {
        this.documentId = documentId;
        this.sourceType = sourceType;
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

    public String getDocumentId() {
        return documentId;
    }

    public String getSourceType() {
        return sourceType;
    }

    public String getPayloadJson() {
        return payloadJson;
    }

    public void update(String payloadJson) {
        this.payloadJson = payloadJson;
    }
}
