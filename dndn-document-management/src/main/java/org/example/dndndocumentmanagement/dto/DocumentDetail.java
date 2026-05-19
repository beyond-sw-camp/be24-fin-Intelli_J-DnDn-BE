package org.example.dndndocumentmanagement.dto;

import java.util.Map;

public record DocumentDetail(
        DocumentSummary summary,
        Map<String, Object> attributes
) {
}
