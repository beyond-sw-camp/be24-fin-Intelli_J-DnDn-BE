package org.example.dndncore.analysis.model;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.*;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Map;

public class ScheduleChangeDto {

    private static final DateTimeFormatter DATE_FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd");
    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

    // ── 요청 등록 (공정 책임자) ────────────────────────────────────────────

    @Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
    @Schema(description = "일정 변경 요청")
    public static class Req {
        @Schema(description = "프로젝트 ID", example = "1")
        private Long projectId;
        @Schema(description = "공정 단계 ID", example = "1001")
        private Long tradeProcessId;    // 선택 — AI 추천 기반이면 연결
        @Schema(description = "작업 계획 ID", example = "2001")
        private Long workPlanId;        // 선택 — 월간 세부계획 변경 요청이면 연결
        @Schema(description = "작업명", example = "기초 철근 배근")
        private String taskName;
        @Schema(description = "요청자", example = "김철수 (철근 책임자)")
        private String requester;
        @Schema(description = "공정/공종", example = "철근콘크리트공사")
        private String process;
        @Schema(description = "기존 시작일", example = "2026-05-01")
        private LocalDate oldStart;
        @Schema(description = "기존 종료일", example = "2026-05-10")
        private LocalDate oldEnd;
        @Schema(description = "변경 시작일", example = "2026-05-03")
        private LocalDate newStart;
        @Schema(description = "변경 종료일", example = "2026-05-12")
        private LocalDate newEnd;
        @Schema(description = "변경 사유", example = "자재 수급 지연")
        private String reason;
        @Schema(description = "지연 원인", example = "비 예보로 작업 불가")
        private String cause;
        @Schema(description = "변경 요약 정보")
        private Map<String, Object> changeSummary;
        @Schema(description = "세부 변경 목록")
        private List<Map<String, Object>> detailChanges;
        @Schema(description = "AI 반영 여부", example = "true")
        private Boolean aiApplied;
        @Schema(description = "첨부파일 URL 목록")
        private List<String> attachmentUrls;  // 업로드 후 URL 목록
    }

    // ── 승인/반려 (총 책임자) ─────────────────────────────────────────────

    @Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
    @Schema(description = "일정 변경 승인 요청")
    public static class ApproveReq {
        @Schema(description = "처리자", example = "이감독 (현장 총 책임자)")
        private String approver;    // 처리자 표시명
    }

    @Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
    @Schema(description = "일정 변경 반려 요청")
    public static class RejectReq {
        @Schema(description = "처리자", example = "이감독 (현장 총 책임자)")
        private String approver;
        @Schema(description = "반려 사유", example = "공정 일정이 과도하게 연장됨")
        private String rejectReason;
    }

    // ── 응답 ──────────────────────────────────────────────────────────────

    @Getter @NoArgsConstructor @AllArgsConstructor @Builder
    @Schema(description = "일정 변경 응답")
    public static class Res {
        @Schema(description = "요청 ID", example = "1")
        private Long idx;
        @Schema(description = "프로젝트 ID", example = "1")
        private Long projectId;
        @Schema(description = "공정 단계 ID", example = "1001")
        private Long tradeProcessId;
        @Schema(description = "작업 계획 ID", example = "2001")
        private Long workPlanId;
        @Schema(description = "작업명", example = "기초 철근 배근")
        private String taskName;
        @Schema(description = "요청자", example = "김철수 (철근 책임자)")
        private String requester;
        @Schema(description = "공정/공종", example = "철근콘크리트공사")
        private String process;
        @Schema(description = "요청일", example = "2026-05-27")
        private String requestDate;     // createdAt 포맷
        @Schema(description = "기존 시작일", example = "2026-05-01")
        private LocalDate oldStart;
        @Schema(description = "기존 종료일", example = "2026-05-10")
        private LocalDate oldEnd;
        @Schema(description = "변경 시작일", example = "2026-05-03")
        private LocalDate newStart;
        @Schema(description = "변경 종료일", example = "2026-05-12")
        private LocalDate newEnd;
        @Schema(description = "변경 사유", example = "자재 수급 지연")
        private String reason;
        @Schema(description = "지연 원인", example = "비 예보로 작업 불가")
        private String cause;
        @Schema(description = "변경 요약 정보")
        private Map<String, Object> changeSummary;
        @Schema(description = "세부 변경 목록")
        private List<Map<String, Object>> detailChanges;
        @Schema(description = "AI 반영 여부", example = "true")
        private Boolean aiApplied;
        @Schema(description = "첨부파일 URL 목록")
        private List<String> attachmentUrls;
        @Schema(description = "상태값", example = "approved")
        private String status;          // 프론트 상태 코드(pending/approved/applied/rejected)
        @Schema(description = "상태 라벨", example = "승인")
        private String statusLabel;     // 한글 라벨
        @Schema(description = "반려 사유", example = "공정 일정이 과도하게 연장됨")
        private String rejectReason;
        @Schema(description = "처리자", example = "이감독 (현장 총 책임자)")
        private String approver;
        @Schema(description = "처리일", example = "2026-05-28")
        private LocalDate processedAt;

        public static Res from(ScheduleChange entity) {
            String requestDate = "";
            if (entity.getCreatedAt() != null) {
                requestDate = entity.getCreatedAt().toLocalDate().format(DATE_FORMATTER);
            }

            List<String> urls = Collections.emptyList();
            if (entity.getAttachmentUrls() != null && !entity.getAttachmentUrls().isBlank()) {
                urls = Arrays.asList(entity.getAttachmentUrls().split(","));
            }

            return Res.builder()
                    .idx(entity.getIdx())
                    .projectId(entity.getProject().getIdx())
                    .tradeProcessId(entity.getTradeProcess() != null
                            ? entity.getTradeProcess().getIdx() : null)
                    .workPlanId(entity.getWorkPlan() != null
                            ? entity.getWorkPlan().getIdx() : null)
                    .taskName(entity.getTaskName())
                    .requester(entity.getRequester())
                    .process(entity.getProcess())
                    .requestDate(requestDate)
                    .oldStart(entity.getOldStart())
                    .oldEnd(entity.getOldEnd())
                    .newStart(entity.getNewStart())
                    .newEnd(entity.getNewEnd())
                    .reason(entity.getReason())
                    .cause(entity.getCause())
                    .changeSummary(readObject(entity.getChangeSummaryJson()))
                    .detailChanges(readList(entity.getDetailChangesJson()))
                    .aiApplied(entity.getAiApplied())
                    .attachmentUrls(urls)
                    .status(toClientStatus(entity.getStatus()))
                    .statusLabel(entity.getStatus().getLabel())
                    .rejectReason(entity.getRejectReason())
                    .approver(entity.getApprover())
                    .processedAt(entity.getProcessedAt())
                    .build();
        }

        private static String toClientStatus(ScheduleChangeStatus status) {
            if (status == null) return "";

            return switch (status) {
                case PENDING -> "pending";
                case APPROVED -> "approved";
                case APPLIED -> "applied";
                case REJECTED -> "rejected";
            };
        }

        private static Map<String, Object> readObject(String json) {
            if (json == null || json.isBlank()) return null;

            try {
                return OBJECT_MAPPER.readValue(json, new TypeReference<>() {});
            } catch (Exception e) {
                return null;
            }
        }

        private static List<Map<String, Object>> readList(String json) {
            if (json == null || json.isBlank()) return Collections.emptyList();

            try {
                return OBJECT_MAPPER.readValue(json, new TypeReference<>() {});
            } catch (Exception e) {
                return Collections.emptyList();
            }
        }
    }
}
