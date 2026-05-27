package org.example.dndndocumentmanagement.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import java.time.LocalDate;
import org.example.dndndocumentmanagement.model.DocumentType;

@Schema(description = "문서 검색 조건")
public record DocumentSearchCondition(
        @Schema(description = "프로젝트 ID", example = "1")
        Long projectId,
        @Schema(description = "문서 유형", example = "ALL")
        DocumentType documentType,
        @Schema(description = "검색어", example = "안전")
        String keyword,
        @Schema(description = "검색 시작일", example = "2026-05-01")
        LocalDate startDate,
        @Schema(description = "검색 종료일", example = "2026-05-31")
        LocalDate endDate,
        @Schema(description = "협력사명", example = "ABC건설")
        String partnerName,
        @Schema(description = "정렬 필드", example = "uploadDate")
        String sortField,
        @Schema(description = "정렬 방향", example = "desc")
        String sortDir,
        @Schema(description = "페이지 번호", example = "0")
        int page,
        @Schema(description = "페이지 크기", example = "10")
        int size
) {

    public DocumentSearchCondition normalized() {
        return new DocumentSearchCondition(
                projectId,
                documentType == null ? DocumentType.ALL : documentType,
                blankToNull(keyword),
                startDate,
                endDate,
                blankToNull(partnerName),
                blankToDefault(sortField, "uploadDate"),
                "asc".equalsIgnoreCase(sortDir) ? "asc" : "desc",
                Math.max(0, page),
                Math.min(Math.max(1, size), 100)
        );
    }

    private static String blankToNull(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        return value.trim();
    }

    private static String blankToDefault(String value, String defaultValue) {
        String normalized = blankToNull(value);
        return normalized == null ? defaultValue : normalized;
    }
}
