package org.example.dndncore.document_management;

import java.nio.file.Files;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.example.dndncore.common.model.BaseResponse;
import org.example.dndncore.document_management.model.DocumentManagementDto;
import org.example.dndncore.project.model.enums.DocType;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.io.Resource;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.List;

@Slf4j
@RequestMapping("/document-management")
@RequiredArgsConstructor
@RestController
public class DocumentManagementController {

    private final DocumentManagementService documentManagementService;

    @Autowired(required = false)
    private LocalStorageService localStorageService;

    @GetMapping("/{project_id}")
    public BaseResponse read(
            @PathVariable(value = "project_id") Long project_id,
            @RequestParam(value = "docType", required = false) DocType docType,
            @PageableDefault(size = 10, sort = "createdAt", direction = Sort.Direction.DESC) Pageable pageable
    ) {
        DocumentManagementDto.PageRes res = documentManagementService.read(project_id, docType, pageable);
        return BaseResponse.success(res);
    }

    @GetMapping("/{project_id}/uploaded")
    public BaseResponse readUploadedDocuments(
            @PathVariable(value = "project_id") Long project_id,
            @RequestParam(value = "docType", required = false, defaultValue = "ALL") String docType,
            @RequestParam(value = "q", required = false) String q,
            @RequestParam(value = "startDate", required = false)
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate startDate,
            @RequestParam(value = "endDate", required = false)
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate endDate,
            @RequestParam(value = "partnerName", required = false) String partnerName,
            @RequestParam(value = "sortField", required = false, defaultValue = "uploadDate") String sortField,
            @RequestParam(value = "sortDir", required = false, defaultValue = "desc") String sortDir,
            @PageableDefault(size = 10) Pageable pageable
    ) {
        DocumentManagementDto.UploadedDocumentPageRes res = documentManagementService.readUploadedDocuments(
                project_id,
                docType,
                q,
                startDate,
                endDate,
                partnerName,
                sortField,
                sortDir,
                pageable
        );
        return BaseResponse.success(res);
    }

    @GetMapping("/{project_id}/pinned")
    public BaseResponse readPinned(@PathVariable(value = "project_id") Long project_id) {
        List<DocumentManagementDto.ReadRes> res = documentManagementService.readPinnedSchedules(project_id);
        return BaseResponse.success(res);
    }

    @PostMapping(value = "/upload", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public BaseResponse upload(@ModelAttribute DocumentManagementDto.UploadReq dto) {
        documentManagementService.upload(dto);
        return BaseResponse.success("성공");
    }

    @GetMapping("/download/{idx}")
    public BaseResponse download(@PathVariable Long idx) {
        String url = documentManagementService.download(idx, false);
        return BaseResponse.success(url);
    }

    @GetMapping("/preview/{idx}")
    public BaseResponse preview(@PathVariable Long idx) {
        String url = documentManagementService.download(idx, true);
        return BaseResponse.success(url);
    }

    /**
     * 로컬 저장 모드일 때만 동작하는 파일 서빙 엔드포인트.
     */
    @GetMapping("/local-files/{encodedKey}")
    public ResponseEntity<Resource> serveLocalFile(
            @PathVariable String encodedKey,
            @RequestParam String fileName,
            @RequestParam(defaultValue = "false") boolean preview
    ) throws java.io.IOException {
        if (localStorageService == null) {
            return ResponseEntity.notFound().build();
        }

        Resource resource = localStorageService.loadAsResource(encodedKey);

        String encodedFileName = URLEncoder.encode(fileName, StandardCharsets.UTF_8)
                .replaceAll("\\+", "%20");

        String disposition = preview
                ? "inline; filename*=UTF-8''" + encodedFileName
                : "attachment; filename*=UTF-8''" + encodedFileName;

        // 파일 확장자에 맞는 Content-Type 자동 감지
        String contentType = Files.probeContentType(resource.getFile().toPath());
        if (contentType == null) {
            contentType = "application/octet-stream";
        }

        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, disposition)
                .header("Content-Type", contentType)
                .body(resource);
    }
}
