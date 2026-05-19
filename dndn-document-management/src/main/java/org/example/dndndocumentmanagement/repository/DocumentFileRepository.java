package org.example.dndndocumentmanagement.repository;

public interface DocumentFileRepository {

    String getPreviewUrl(Long sourceId);

    String getDownloadUrl(Long sourceId);
}
