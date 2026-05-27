package org.example.dndndocumentmanagement.controller;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.time.LocalDate;
import java.util.Map;
import org.example.dndndocumentmanagement.dto.ApiResponse;
import org.example.dndndocumentmanagement.dto.DocumentPage;
import org.example.dndndocumentmanagement.dto.DocumentSearchCondition;
import org.example.dndndocumentmanagement.model.DocumentType;
import org.example.dndndocumentmanagement.service.DocumentQueryService;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/document-management")
@Tag(name = "문서 관리", description = "문서 관리 조회 API")
public class DocumentController {

    private final DocumentQueryService documentQueryService;

    public DocumentController(DocumentQueryService documentQueryService) {
        this.documentQueryService = documentQueryService;
    }

    @GetMapping("/health")
    @Operation(summary = "문서 관리 모듈 헬스체크", description = "문서 관리 모듈의 상태를 확인합니다")
    @ApiResponses(value = {
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "정상 응답", content = @Content(schema = @Schema(implementation = Map.class)))
    })
    public Map<String, String> health() {
        return Map.of("status", "UP", "service", "dndn-document-management");
    }

    @GetMapping("/{projectId}/uploaded")
    @Operation(summary = "업로드 문서 목록 조회", description = "프로젝트 기준으로 업로드된 문서를 조건 검색하여 페이징 조회합니다")
    @ApiResponses(value = {
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "조회 성공", content = @Content(schema = @Schema(implementation = ApiResponse.class)))
    })
    public ApiResponse<DocumentPage> uploadedDocuments(
            @PathVariable
            @Parameter(description = "프로젝트 ID", example = "1")
            Long projectId,
            @RequestParam(value = "docType", required = false, defaultValue = "ALL")
            @Parameter(description = "문서 유형 코드", example = "ALL")
            String docType,
            @RequestParam(value = "q", required = false)
            @Parameter(description = "검색어", example = "안전")
            String keyword,
            @RequestParam(value = "startDate", required = false)
            @Parameter(description = "검색 시작일", example = "2026-05-01")
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate startDate,
            @RequestParam(value = "endDate", required = false)
            @Parameter(description = "검색 종료일", example = "2026-05-31")
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate endDate,
            @RequestParam(value = "partnerName", required = false)
            @Parameter(description = "협력사명", example = "ABC건설")
            String partnerName,
            @RequestParam(value = "sortField", required = false, defaultValue = "uploadDate")
            @Parameter(description = "정렬 필드", example = "uploadDate")
            String sortField,
            @RequestParam(value = "sortDir", required = false, defaultValue = "desc")
            @Parameter(description = "정렬 방향", example = "desc")
            String sortDir,
            @RequestParam(value = "page", required = false, defaultValue = "0")
            @Parameter(description = "페이지 번호", example = "0")
            int page,
            @RequestParam(value = "size", required = false, defaultValue = "10")
            @Parameter(description = "페이지 크기", example = "10")
            int size
    ) {
        DocumentSearchCondition condition = new DocumentSearchCondition(
                projectId,
                DocumentType.fromCode(docType),
                keyword,
                startDate,
                endDate,
                partnerName,
                sortField,
                sortDir,
                page,
                size
        );
        return ApiResponse.success(documentQueryService.search(condition));
    }
}
