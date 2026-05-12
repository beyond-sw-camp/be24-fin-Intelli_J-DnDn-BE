package org.example.dndn.document_management;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.example.dndn.common.model.BaseResponse;
import org.example.dndn.document_management.model.DocumentManagementDto;
import org.example.dndn.project.model.enums.DocType;
import org.springframework.core.io.Resource;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.List;

@Slf4j
@RequestMapping("/document-management")
@RequiredArgsConstructor
@RestController
public class DocumentManagementController {
    private final DocumentManagementService documentManagementService;

    // ★ 변경: docType 쿼리 파라미터 추가 (선택사항)
    //   예: GET /document-management/1?page=0&size=10&docType=TRADE_PLAN
    //   docType 안 보내면 전체 조회 (기존 동작 유지)
    @GetMapping("/{project_id}")
    public BaseResponse read(
            @PathVariable(value = "project_id") Long project_id,
            @RequestParam(value = "docType", required = false) DocType docType,
            @PageableDefault(size = 10, sort = "createdAt", direction = Sort.Direction.DESC) Pageable pageable
    ) {
        DocumentManagementDto.PageRes res = documentManagementService.read(project_id, docType, pageable);
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

    // 파일 다운로드 기능
    // 일반 API와 달리 파일 바이너리를 그대로 스트림으로 내려주기 위해
    // BaseResponse가 아닌 ResponseEntity<Resource>를 반환
    @GetMapping("/download/{idx}")
    public ResponseEntity<Resource> download(@PathVariable Long idx) {
        DocumentManagementDto.DownloadRes res = documentManagementService.download(idx);

        // 한글 파일명 깨지지 않도록 인코딩
        String encodedFileName = URLEncoder.encode(res.getFileName(), StandardCharsets.UTF_8)
                .replaceAll("\\+", "%20");

        return ResponseEntity.ok()
                .contentType(MediaType.APPLICATION_OCTET_STREAM)
                .header(HttpHeaders.CONTENT_DISPOSITION,
                        "attachment; filename*=UTF-8''" + encodedFileName)
                .body(res.getResource());
    }
}