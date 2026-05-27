package org.example.dndndocumentmanagement.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import java.util.Map;

@Schema(description = "문서 상세 정보")
public record DocumentDetail(
        @Schema(description = "문서 요약 정보")
        DocumentSummary summary,
        @Schema(description = "문서 상세 속성")
        Map<String, Object> attributes
) {
}
