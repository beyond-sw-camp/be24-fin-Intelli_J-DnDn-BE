package org.example.dndndocumentmanagement.dto;

import java.util.List;

public record DocumentPage(
        List<DocumentSummary> content,
        int currentPage,
        int totalPages,
        long totalElements,
        int size,
        boolean first,
        boolean last
) {

    public static DocumentPage empty(int page, int size) {
        return new DocumentPage(List.of(), page, 1, 0L, size, page == 0, true);
    }
}
