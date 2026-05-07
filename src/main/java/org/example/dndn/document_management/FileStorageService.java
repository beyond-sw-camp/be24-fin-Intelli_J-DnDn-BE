package org.example.dndn.document_management;

import jakarta.annotation.PostConstruct;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.UUID;

@Slf4j
@Service
public class FileStorageService {

    @Value("${file.upload-dir}")
    private String uploadDir;

    private Path rootLocation;

    @PostConstruct
    public void init() {
        try {
            this.rootLocation = Paths.get(uploadDir).toAbsolutePath().normalize();
            Files.createDirectories(rootLocation);
            log.info("File upload directory: {}", rootLocation);
        } catch (IOException e) {
            throw new RuntimeException("업로드 디렉토리 생성 실패", e);
        }
    }

    /**
     * 파일을 로컬에 저장하고 저장된 경로(절대경로 문자열)를 반환
     */
    public String store(MultipartFile file, Long projectId) {
        if (file == null || file.isEmpty()) {
            throw new IllegalArgumentException("업로드 파일이 비어있습니다.");
        }

        try {
            // 프로젝트별 + 날짜별 하위 폴더 (선택)
            String datePath = LocalDate.now().format(DateTimeFormatter.ofPattern("yyyy/MM/dd"));
            Path targetDir = rootLocation
                    .resolve("project-" + projectId)
                    .resolve(datePath);
            Files.createDirectories(targetDir);

            // 원본 파일명에서 확장자 추출
            String originalName = file.getOriginalFilename();
            String ext = "";
            if (originalName != null && originalName.contains(".")) {
                ext = originalName.substring(originalName.lastIndexOf("."));
            }

            // UUID로 파일명 충돌 방지
            String storedName = UUID.randomUUID() + ext;
            Path target = targetDir.resolve(storedName).normalize();

            // 디렉토리 탈출 공격 방지
            if (!target.startsWith(rootLocation)) {
                throw new SecurityException("잘못된 파일 경로입니다.");
            }

            file.transferTo(target.toFile());
            return target.toString();   // DB에 저장할 경로

        } catch (IOException e) {
            throw new RuntimeException("파일 저장 실패: " + file.getOriginalFilename(), e);
        }
    }

    public void delete(String fileUrl) {
        try {
            Files.deleteIfExists(Paths.get(fileUrl));
        } catch (IOException e) {
            log.warn("파일 삭제 실패: {}", fileUrl, e);
        }
    }
}