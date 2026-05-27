package org.example.dndndocumentmanagement.dto.event;

import io.swagger.v3.oas.annotations.media.Schema;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.Map;

@Schema(description = "일보 변경 이벤트 DTO")
public record DailyReportChangedEvent(
        @Schema(description = "이벤트 ID", example = "evt-002")
        String eventId,
        @Schema(description = "이벤트 타입", example = "DAILY_REPORT_CHANGED")
        String eventType,
        @Schema(description = "이벤트 발생 시각", example = "2026-05-27T10:15:30")
        LocalDateTime occurredAt,
        @Schema(description = "프로젝트 ID", example = "1")
        Long projectId,
        @Schema(description = "원본 소스 ID", example = "201")
        Long sourceId,
        @Schema(description = "문서 코드", example = "DAILY-2026-0001")
        String docCode,
        @Schema(description = "공종명", example = "골조")
        String tradeName,
        @Schema(description = "작업 일자", example = "2026-05-27")
        LocalDate reportDate,
        @Schema(description = "업로더", example = "홍길동")
        String uploader,
        @Schema(description = "누적 진도율", example = "55.5")
        Double actualProgress,
        @Schema(description = "당일 진도율", example = "2.5")
        Double todayProgress,
        @Schema(description = "실투입 인원", example = "12")
        Integer actualWorkerCount,
        @Schema(description = "작업 위치", example = "A동 1층")
        String location,
        @Schema(description = "이슈", example = "우천으로 지연")
        String issue,
        @Schema(description = "금일 작업", example = "거푸집 설치")
        String todayWork,
        @Schema(description = "익일 계획", example = "철근 배근")
        String tomorrowPlan,
        @Schema(description = "미리보기 데이터")
        Map<String, Object> previewPayload
) {
}
