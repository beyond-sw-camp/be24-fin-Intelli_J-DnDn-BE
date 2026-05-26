package org.example.dndndocumentmanagement.dto;

import java.time.LocalDate;
import org.example.dndndocumentmanagement.model.DocumentType;

public record DocumentSearchCondition(
        Long projectId,
        DocumentType documentType,
        String keyword,
        LocalDate startDate,
        LocalDate endDate,
        String partnerName,
        String sortField,
        String sortDir,
        int page,
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
