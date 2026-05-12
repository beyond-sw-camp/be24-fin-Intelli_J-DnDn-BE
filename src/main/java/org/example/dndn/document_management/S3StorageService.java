package org.example.dndn.document_management;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.example.dndn.common.exception.BaseException;
import org.example.dndn.common.model.BaseResponseStatus;
import org.example.dndn.project.model.enums.DocType;
import org.springframework.beans.factory.annotation.Value;
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
import software.amazon.awssdk.services.s3.presigner.model.PresignedGetObjectRequest;

import java.io.IOException;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class S3StorageService {

    private final S3Client s3Client;
    private final S3Presigner s3Presigner;

    @Value("${cloud.aws.s3.bucket}")
    private String bucket;

    @Value("${cloud.aws.s3.presigned-url-expiration}")
    private long presignedUrlExpiration;

    /**
     * S3에 파일 업로드
     * 경로 구조: project-{projectId}/{docType}/{uuid}.{ext}
     * @return S3 object key (DB 저장용)
     */
    public String store(MultipartFile file, Long projectId, DocType docType) {
        if (file == null || file.isEmpty()) {
            throw new IllegalArgumentException("업로드 파일이 비어있습니다.");
        }

        // 확장자 추출
        String originalName = file.getOriginalFilename();
        String ext = "";
        if (originalName != null && originalName.contains(".")) {
            ext = originalName.substring(originalName.lastIndexOf("."));
        }

        // S3 object key 생성: project-{id}/{docType}/{uuid}.ext
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
            return objectKey;   // DB에 S3 key 저장

        } catch (IOException | S3Exception e) {
            log.error("S3 업로드 실패: {}", originalName, e);
            throw new RuntimeException("파일 저장 실패: " + originalName, e);
        }
    }

    /**
     * Presigned URL 발급 (다운로드용)
     * 기본 유효 시간은 application.yml에서 설정
     */
    public String generatePresignedUrl(String objectKey, String fileName, boolean isPreview) {
        try {
            String encodedFileName = URLEncoder.encode(fileName, StandardCharsets.UTF_8)
                    .replaceAll("\\+", "%20");

            // ★ 미리보기면 inline, 다운로드면 attachment
            String disposition = isPreview
                    ? "inline; filename*=UTF-8''" + encodedFileName
                    : "attachment; filename*=UTF-8''" + encodedFileName;

            GetObjectRequest getRequest = GetObjectRequest.builder()
                    .bucket(bucket)
                    .key(objectKey)
                    .responseContentDisposition(disposition)
                    .build();

            GetObjectPresignRequest presignRequest = GetObjectPresignRequest.builder()
                    .signatureDuration(Duration.ofSeconds(presignedUrlExpiration))
                    .getObjectRequest(getRequest)
                    .build();

            return s3Presigner.presignGetObject(presignRequest).url().toString();

        } catch (S3Exception e) {
            log.error("Presigned URL 생성 실패: {}", objectKey, e);
            throw BaseException.from(BaseResponseStatus.DOCUMENT_FILE_READ_FAIL);
        }
    }

    /**
     * S3에서 파일 삭제
     */
    public void delete(String objectKey) {
        try {
            DeleteObjectRequest deleteRequest = DeleteObjectRequest.builder()
                    .bucket(bucket)
                    .key(objectKey)
                    .build();
            s3Client.deleteObject(deleteRequest);
            log.info("S3 파일 삭제 성공: {}", objectKey);
        } catch (S3Exception e) {
            log.warn("S3 파일 삭제 실패: {}", objectKey, e);
        }
    }
}