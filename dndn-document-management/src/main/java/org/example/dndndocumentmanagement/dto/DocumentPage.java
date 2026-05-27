package org.example.dndndocumentmanagement.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import java.util.List;

@Schema(description = "문서 목록 페이징 응답")
public record DocumentPage(
        @Schema(description = "문서 요약 목록")
        List<DocumentSummary> content,
        @Schema(description = "현재 페이지 번호", example = "0")
        int currentPage,
        @Schema(description = "전체 페이지 수", example = "3")
        int totalPages,
        @Schema(description = "전체 문서 수", example = "24")
        long totalElements,
        @Schema(description = "페이지 크기", example = "10")
        int size,
        @Schema(description = "첫 페이지 여부", example = "true")
        boolean first,
        @Schema(description = "마지막 페이지 여부", example = "false")
        boolean last
) {

    public static DocumentPage empty(int page, int size) {
        return new DocumentPage(List.of(), page, 1, 0L, size, page == 0, true);
    }
}
