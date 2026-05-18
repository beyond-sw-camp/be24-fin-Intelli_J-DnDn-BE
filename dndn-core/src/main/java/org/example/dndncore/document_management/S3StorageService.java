package org.example.dndncore.document_management;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.example.dndncore.common.exception.BaseException;
import org.example.dndncore.common.model.BaseResponseStatus;
import org.example.dndncore.project.model.enums.DocType;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;
import software.amazon.awssdk.core.sync.RequestBody;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.DeleteObjectRequest;
import software.amazon.awssdk.services.s3.model.GetObjectRequest;
import software.amazon.awssdk.services.s3.model.PutObjectRequest;
import software.amazon.awssdk.services.s3.model.S3Exception;
import software.amazon.awssdk.services.s3.presigner.S3Presigner;
import software.amazon.awssdk.services.s3.presigner.model.GetObjectPresignRequest;

import java.io.IOException;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.UUID;

/**
 * S3 기반 파일 저장 구현체.
 * application.yml에서 storage.type=s3 일 때만 빈으로 등록된다.
 * (matchIfMissing=true: storage.type 설정 자체가 없으면 기본적으로 S3 사용)
 */
@Slf4j
@Service
@RequiredArgsConstructor
@ConditionalOnProperty(name = "storage.type", havingValue = "s3", matchIfMissing = true)
public class S3StorageService implements StorageService {

    private final S3Client s3Client;
    private final S3Presigner s3Presigner;

    @Value("${cloud.aws.s3.bucket}")
    private String bucket;

    @Value("${cloud.aws.s3.presigned-url-expiration}")
    private long presignedUrlExpiration;

    @Override
    public String store(MultipartFile file, Long projectId, DocType docType) {
        if (file == null || file.isEmpty()) {
            throw new IllegalArgumentException("업로드 파일이 비어있습니다.");
        }

        String originalName = file.getOriginalFilename();
        String ext = "";
        if (originalName != null && originalName.contains(".")) {
            ext = originalName.substring(originalName.lastIndexOf("."));
        }

        String storedName = UUID.randomUUID() + ext;
        String objectKey = String.format("project-%d/%s/%s",
                projectId, docType.name(), storedName);

        try {
            PutObjectRequest putRequest = PutObjectRequest.builder()
                    .bucket(bucket)
                    .key(objectKey)
                    .contentType(file.getContentType())
                    .contentLength(file.getSize())
                    .build();

            s3Client.putObject(putRequest,
                    RequestBody.fromInputStream(file.getInputStream(), file.getSize()));

            log.info("S3 업로드 성공: {}", objectKey);
            return objectKey;

        } catch (IOException | S3Exception e) {
            log.error("S3 업로드 실패: {}", originalName, e);
            throw new RuntimeException("파일 저장 실패: " + originalName, e);
        }
    }

    @Override
    public String getDownloadUrl(String fileKey, String fileName, boolean isPreview) {
        try {
            String encodedFileName = URLEncoder.encode(fileName, StandardCharsets.UTF_8)
                    .replaceAll("\\+", "%20");

            String disposition = isPreview
                    ? "inline; filename*=UTF-8''" + encodedFileName
                    : "attachment; filename*=UTF-8''" + encodedFileName;

            GetObjectRequest getRequest = GetObjectRequest.builder()
                    .bucket(bucket)
                    .key(fileKey)
                    .responseContentDisposition(disposition)
                    .build();

            GetObjectPresignRequest presignRequest = GetObjectPresignRequest.builder()
                    .signatureDuration(Duration.ofSeconds(presignedUrlExpiration))
                    .getObjectRequest(getRequest)
                    .build();

            return s3Presigner.presignGetObject(presignRequest).url().toString();

        } catch (S3Exception e) {
            log.error("Presigned URL 생성 실패: {}", fileKey, e);
            throw BaseException.from(BaseResponseStatus.DOCUMENT_FILE_READ_FAIL);
        }
    }

    @Override
    public void delete(String fileKey) {
        try {
            DeleteObjectRequest deleteRequest = DeleteObjectRequest.builder()
                    .bucket(bucket)
                    .key(fileKey)
                    .build();
            s3Client.deleteObject(deleteRequest);
            log.info("S3 파일 삭제 성공: {}", fileKey);
        } catch (S3Exception e) {
            log.warn("S3 파일 삭제 실패: {}", fileKey, e);
        }
    }
}