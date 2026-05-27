package org.example.dndndocumentmanagement.dto.event;

import io.swagger.v3.oas.annotations.media.Schema;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.Map;

@Schema(description = "작업지시 변경 이벤트 DTO")
public record WorkOrderChangedEvent(
        @Schema(description = "이벤트 ID", example = "evt-003")
        String eventId,
        @Schema(description = "이벤트 타입", example = "WORK_ORDER_CHANGED")
        String eventType,
        @Schema(description = "이벤트 발생 시각", example = "2026-05-27T10:15:30")
        LocalDateTime occurredAt,
        @Schema(description = "프로젝트 ID", example = "1")
        Long projectId,
        @Schema(description = "원본 소스 ID", example = "301")
        Long sourceId,
        @Schema(description = "문서 코드", example = "WORK-2026-0001")
        String docCode,
        @Schema(description = "제목", example = "5월 4주차 작업지시")
        String title,
        @Schema(description = "공종명", example = "골조")
        String tradeName,
        @Schema(description = "기한", example = "2026-05-31")
        LocalDate dueDate,
        @Schema(description = "업로더", example = "홍길동")
        String uploader,
        @Schema(description = "상태 코드", example = "ACTIVE")
        String statusCode,
        @Schema(description = "작업 상세", example = "거푸집 설치 및 검측")
        String workDetail,
        @Schema(description = "작업 시간", example = "09:00-18:00")
        String workTime,
        @Schema(description = "안전 내용", example = "안전모 착용")
        String safetyContent,
        @Schema(description = "작업 인원", example = "10")
        Integer workerCount,
        @Schema(description = "작업 위치", example = "A동 1층")
        String location,
        @Schema(description = "미리보기 데이터")
        Map<String, Object> previewPayload
) {
}
