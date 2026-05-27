package org.example.dndndocumentmanagement.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import java.time.LocalDate;
import java.util.Map;
import org.example.dndndocumentmanagement.model.DocumentType;

@Schema(description = "문서 요약 정보")
public record DocumentSummary(
        @Schema(description = "문서 식별자", example = "doc_1001")
        String id,
        @Schema(description = "문서 출처 유형", example = "UPLOAD_DOCUMENT")
        DocumentType sourceType,
        @Schema(description = "원본 소스 ID", example = "101")
        Long sourceId,
        @Schema(description = "문서 코드", example = "DOC-2026-0001")
        String docCode,
        @Schema(description = "문서 타입 코드", example = "TRADE_PLAN")
        String docTypeCode,
        @Schema(description = "파일명", example = "5월 공정 계획표.xlsx")
        String fileName,
        @Schema(description = "파일 확장자", example = "xlsx")
        String fileExt,
        @Schema(description = "파일 URL", example = "/uploads/master-schedule/uuid_file.xlsx")
        String fileUrl,
        @Schema(description = "문서 출처", example = "본사")
        String origin,
        @Schema(description = "협력사명", example = "ABC건설")
        String partnerName,
        @Schema(description = "업로드 일자", example = "2026-05-27")
        LocalDate uploadDate,
        @Schema(description = "문서 기준 일자", example = "2026-05-01")
        LocalDate docDate,
        @Schema(description = "업로더", example = "홍길동")
        String uploader,
        @Schema(description = "버전", example = "v1")
        String version,
        @Schema(description = "파일 크기", example = "1.2MB")
        String fileSize,
        @Schema(description = "상태 코드", example = "ACTIVE")
        String statusCode,
        @Schema(description = "공종명", example = "골조")
        String tradeName,
        @Schema(description = "다운로드 가능 여부", example = "true")
        boolean downloadable,
        @Schema(description = "원본 부가 데이터")
        Map<String, Object> raw
) {
}
