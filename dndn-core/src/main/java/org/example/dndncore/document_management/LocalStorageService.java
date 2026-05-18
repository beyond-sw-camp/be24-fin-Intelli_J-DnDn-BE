package org.example.dndncore.document_management;

import jakarta.annotation.PostConstruct;
import lombok.extern.slf4j.Slf4j;
import org.example.dndncore.project.model.enums.DocType;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.core.io.Resource;
import org.springframework.core.io.UrlResource;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.net.MalformedURLException;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Base64;
import java.util.UUID;

/**
 * 로컬 디스크 기반 파일 저장 구현체
 */
@Slf4j
@Service
@ConditionalOnProperty(name = "storage.type", havingValue = "local")
public class LocalStorageService implements StorageService {

    @Value("${file.upload-dir}")
    private String uploadDir;

    @Value("${storage.local.base-url}")
    private String baseUrl;

    private Path rootLocation;

    @PostConstruct
    public void init() {
        try {
            this.rootLocation = Paths.get(uploadDir).toAbsolutePath().normalize();
            Files.createDirectories(rootLocation);
            log.info("Local storage root: {}", rootLocation);
        } catch (IOException e) {
            throw new RuntimeException("업로드 디렉토리 생성 실패", e);
        }
    }

    @Override
    public String store(MultipartFile file, Long projectId, DocType docType) {
        if (file == null || file.isEmpty()) {
            throw new IllegalArgumentException("업로드 파일이 비어있습니다.");
        }

        try {
            // S3와 동일한 폴더 구조: project-{id}/{docType}/
            Path targetDir = rootLocation
                    .resolve("project-" + projectId)
                    .resolve(docType.name());
            Files.createDirectories(targetDir);

            String originalName = file.getOriginalFilename();
            String ext = "";
            if (originalName != null && originalName.contains(".")) {
                ext = originalName.substring(originalName.lastIndexOf("."));
            }

            String storedName = UUID.randomUUID() + ext;
            Path target = targetDir.resolve(storedName).normalize();

            // 디렉토리 탈출 공격 방지
            if (!target.startsWith(rootLocation)) {
                throw new SecurityException("잘못된 파일 경로입니다.");
            }

            file.transferTo(target.toFile());

            // DB에 저장할 key: rootLocation 기준 상대경로
            // 예: "project-1/MASTER/uuid.pdf"
            String relativeKey = rootLocation.relativize(target).toString()
                    .replace("\\", "/");  // 윈도우 대비

            log.info("로컬 저장 성공: {}", target);
            return relativeKey;

        } catch (IOException e) {
            throw new RuntimeException("파일 저장 실패: " + file.getOriginalFilename(), e);
        }
    }

    @Override
    public String getDownloadUrl(String fileKey, String fileName, boolean isPreview) {
        // fileKey를 URL-safe하게 인코딩해서 path variable로 넘김
        // (슬래시 포함되어 있어서 Base64로 감싸 처리)
        String encodedKey = Base64.getUrlEncoder().withoutPadding()
                .encodeToString(fileKey.getBytes(StandardCharsets.UTF_8));

        String encodedFileName = URLEncoder.encode(fileName, StandardCharsets.UTF_8)
                .replaceAll("\\+", "%20");

        // 미리보기/다운로드 여부와 파일명을 쿼리 파라미터로 전달
        return String.format("%s/document-management/local-files/%s?fileName=%s&preview=%s",
                baseUrl, encodedKey, encodedFileName, isPreview);
    }

    @Override
    public void delete(String fileKey) {
        try {
            Path target = rootLocation.resolve(fileKey).normalize();
            if (!target.startsWith(rootLocation)) {
                log.warn("잘못된 삭제 경로 무시: {}", fileKey);
                return;
            }
            Files.deleteIfExists(target);
        } catch (IOException e) {
            log.warn("파일 삭제 실패: {}", fileKey, e);
        }
    }

    public Resource loadAsResource(String encodedKey) {
        try {
            String fileKey = new String(
                    Base64.getUrlDecoder().decode(encodedKey),
                    StandardCharsets.UTF_8);

            Path target = rootLocation.resolve(fileKey).normalize();
            if (!target.startsWith(rootLocation)) {
                throw new SecurityException("잘못된 파일 경로입니다.");
            }

            Resource resource = new UrlResource(target.toUri());
            if (!resource.exists() || !resource.isReadable()) {
                throw new RuntimeException("파일을 읽을 수 없습니다: " + fileKey);
            }
            return resource;

        } catch (MalformedURLException e) {
            throw new RuntimeException("파일 경로 오류", e);
        }
    }
}