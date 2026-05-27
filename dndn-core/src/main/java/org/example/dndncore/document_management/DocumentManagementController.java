package org.example.dndncore.document_management;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.ArraySchema;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
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
@Tag(name = "Document Management", description = "문서 관리 API")
public class DocumentManagementController {

    private final DocumentManagementService documentManagementService;

    // feat : LocalStorageService는 local 저장 모드에서만 주입
    // feat : S3 모드에서는 null이며 local-files 엔드포인트를 사용하지 않음
    @Autowired(required = false)
    private LocalStorageService localStorageService;

    @GetMapping("/{project_id}")
    @Operation(summary = "문서 목록 조회", description = "프로젝트 기준 문서 목록을 조회합니다.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "조회 성공",
                    content = @Content(schema = @Schema(implementation = DocumentManagementDto.PageRes.class))),
            @ApiResponse(responseCode = "500", description = "서버 오류")
    })
    public BaseResponse read(
            @Parameter(description = "프로젝트 ID", required = true, example = "1")
            @PathVariable(value = "project_id") Long project_id,
            @Parameter(description = "문서 타입", example = "MASTER")
            @RequestParam(value = "docType", required = false) DocType docType,
            @PageableDefault(size = 10, sort = "createdAt", direction = Sort.Direction.DESC) Pageable pageable
    ) {
        DocumentManagementDto.PageRes res = documentManagementService.read(project_id, docType, pageable);
        return BaseResponse.success(res);
    }

    @GetMapping("/{project_id}/uploaded")
    @Operation(summary = "업로드 문서 목록 조회", description = "업로드 문서를 검색/정렬 조건으로 조회합니다.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "조회 성공",
                    content = @Content(schema = @Schema(implementation = DocumentManagementDto.UploadedDocumentPageRes.class))),
            @ApiResponse(responseCode = "500", description = "서버 오류")
    })
    public BaseResponse readUploadedDocuments(
            @Parameter(description = "프로젝트 ID", required = true, example = "1")
            @PathVariable(value = "project_id") Long project_id,
            @Parameter(description = "문서 타입 코드", example = "ALL")
            @RequestParam(value = "docType", required = false, defaultValue = "ALL") String docType,
            @Parameter(description = "검색어")
            @RequestParam(value = "q", required = false) String q,
            @Parameter(description = "조회 시작일", example = "2026-05-01")
            @RequestParam(value = "startDate", required = false)
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate startDate,
            @Parameter(description = "조회 종료일", example = "2026-05-31")
            @RequestParam(value = "endDate", required = false)
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate endDate,
            @Parameter(description = "협력사명")
            @RequestParam(value = "partnerName", required = false) String partnerName,
            @Parameter(description = "정렬 필드", example = "uploadDate")
            @RequestParam(value = "sortField", required = false, defaultValue = "uploadDate") String sortField,
            @Parameter(description = "정렬 방향", example = "desc")
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
    @Operation(summary = "고정 공정표 목록 조회", description = "프로젝트의 고정 공정표 목록을 조회합니다.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "조회 성공",
                    content = @Content(array = @ArraySchema(schema = @Schema(implementation = DocumentManagementDto.ReadRes.class)))),
            @ApiResponse(responseCode = "500", description = "서버 오류")
    })
    public BaseResponse readPinned(@PathVariable(value = "project_id") Long project_id) {
        List<DocumentManagementDto.ReadRes> res = documentManagementService.readPinnedSchedules(project_id);
        return BaseResponse.success(res);
    }

    @PostMapping(value = "/upload", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @Operation(summary = "문서 업로드", description = "문서를 업로드하고 저장 정보를 등록합니다.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "업로드 성공",
                    content = @Content(schema = @Schema(implementation = String.class))),
            @ApiResponse(responseCode = "400", description = "잘못된 요청"),
            @ApiResponse(responseCode = "500", description = "서버 오류")
    })
    public BaseResponse upload(@ModelAttribute DocumentManagementDto.UploadReq dto) {
        documentManagementService.upload(dto);
        return BaseResponse.success("성공");
    }

    @GetMapping("/download/{idx}")
    @Operation(summary = "문서 다운로드 링크 조회", description = "문서 다운로드 URL을 조회합니다.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "조회 성공",
                    content = @Content(schema = @Schema(implementation = String.class))),
            @ApiResponse(responseCode = "404", description = "문서를 찾을 수 없음"),
            @ApiResponse(responseCode = "500", description = "서버 오류")
    })
    public BaseResponse download(@PathVariable Long idx) {
        String url = documentManagementService.download(idx, false);
        return BaseResponse.success(url);
    }

    @GetMapping("/preview/{idx}")
    @Operation(summary = "문서 미리보기 링크 조회", description = "문서 미리보기 URL을 조회합니다.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "조회 성공",
                    content = @Content(schema = @Schema(implementation = String.class))),
            @ApiResponse(responseCode = "404", description = "문서를 찾을 수 없음"),
            @ApiResponse(responseCode = "500", description = "서버 오류")
    })
    public BaseResponse preview(@PathVariable Long idx) {
        String url = documentManagementService.download(idx, true);
        return BaseResponse.success(url);
    }

    // feat : 로컬 저장 모드 전용 파일 서빙 엔드포인트
    @GetMapping("/local-files/{encodedKey}")
    @Operation(summary = "로컬 파일 서빙", description = "로컬 저장 모드에서 파일을 직접 서빙합니다.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "서빙 성공"),
            @ApiResponse(responseCode = "404", description = "파일을 찾을 수 없음")
    })
    public ResponseEntity<Resource> serveLocalFile(
            @Parameter(description = "인코딩된 파일 키", required = true)
            @PathVariable String encodedKey,
            @Parameter(description = "파일명", required = true)
            @RequestParam String fileName,
            @Parameter(description = "미리보기 여부", example = "false")
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

        // feat : 파일 확장자 기반 Content-Type 자동 감지
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
