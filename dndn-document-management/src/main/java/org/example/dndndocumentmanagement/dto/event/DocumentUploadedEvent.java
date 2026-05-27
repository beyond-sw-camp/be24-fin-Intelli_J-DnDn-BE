package org.example.dndndocumentmanagement.dto.event;

import io.swagger.v3.oas.annotations.media.Schema;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.Map;

@Schema(description = "문서 업로드 이벤트 DTO")
public record DocumentUploadedEvent(
        @Schema(description = "이벤트 ID", example = "evt-001")
        String eventId,
        @Schema(description = "이벤트 타입", example = "DOCUMENT_UPLOADED")
        String eventType,
        @Schema(description = "이벤트 발생 시각", example = "2026-05-27T10:15:30")
        LocalDateTime occurredAt,
        @Schema(description = "프로젝트 ID", example = "1")
        Long projectId,
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
        Boolean downloadable,
        @Schema(description = "추출된 본문 텍스트")
        String contentText,
        @Schema(description = "미리보기 데이터")
        Map<String, Object> previewPayload
) {
}
