package org.example.dndncore.document_management;

import org.example.dndncore.project.model.enums.DocType;
import org.springframework.web.multipart.MultipartFile;

/**
 * 파일 저장소 추상화 인터페이스.
 * S3, 로컬 저장소를 선택하여 저장할 수 있다.
 */
public interface StorageService {

    String store(MultipartFile file, Long projectId, DocType docType);

    String getDownloadUrl(String fileKey, String fileName, boolean isPreview);

    void delete(String fileKey);
}