package org.example.dndndocumentmanagement.controller;

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
public class DocumentController {

    private final DocumentQueryService documentQueryService;

    public DocumentController(DocumentQueryService documentQueryService) {
        this.documentQueryService = documentQueryService;
    }

    @GetMapping("/health")
    public Map<String, String> health() {
        return Map.of("status", "UP", "service", "dndn-document-management");
    }

    @GetMapping("/{projectId}/uploaded")
    public ApiResponse<DocumentPage> uploadedDocuments(
            @PathVariable Long projectId,
            @RequestParam(value = "docType", required = false, defaultValue = "ALL") String docType,
            @RequestParam(value = "q", required = false) String keyword,
            @RequestParam(value = "startDate", required = false)
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate startDate,
            @RequestParam(value = "endDate", required = false)
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate endDate,
            @RequestParam(value = "partnerName", required = false) String partnerName,
            @RequestParam(value = "sortField", required = false, defaultValue = "uploadDate") String sortField,
            @RequestParam(value = "sortDir", required = false, defaultValue = "desc") String sortDir,
            @RequestParam(value = "page", required = false, defaultValue = "0") int page,
            @RequestParam(value = "size", required = false, defaultValue = "10") int size
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
