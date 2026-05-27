package org.example.dndndocumentmanagement.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import java.util.Map;
import org.example.dndndocumentmanagement.model.DocumentType;

@Schema(description = "문서 미리보기 데이터")
public record DocumentPreviewData(
        @Schema(description = "문서 식별자", example = "doc_1001")
        String documentId,
        @Schema(description = "문서 유형", example = "ALL")
        DocumentType documentType,
        @Schema(description = "미리보기 페이로드")
        Map<String, Object> payload
) {

    public static DocumentPreviewData empty(String documentId) {
        return new DocumentPreviewData(documentId, DocumentType.ALL, Map.of());
    }
}
