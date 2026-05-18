package org.example.dndncore.analysis.model;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
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
    public static class Req {
        private Long projectId;
        private Long tradeProcessId;    // 선택 — AI 추천 기반이면 연결
        private Long workPlanId;        // 선택 — 월간 세부계획 변경 요청이면 연결
        private String taskName;
        private String requester;
        private String process;
        private LocalDate oldStart;
        private LocalDate oldEnd;
        private LocalDate newStart;
        private LocalDate newEnd;
        private String reason;
        private String cause;
        private Map<String, Object> changeSummary;
        private List<Map<String, Object>> detailChanges;
        private Boolean aiApplied;
        private List<String> attachmentUrls;  // 업로드 후 URL 목록
    }

    // ── 승인/반려 (총 책임자) ─────────────────────────────────────────────

    @Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
    public static class ApproveReq {
        private String approver;    // 처리자 표시명
    }

    @Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
    public static class RejectReq {
        private String approver;
        private String rejectReason;
    }

    // ── 응답 ──────────────────────────────────────────────────────────────

    @Getter @NoArgsConstructor @AllArgsConstructor @Builder
    public static class Res {
        private Long idx;
        private Long projectId;
        private Long tradeProcessId;
        private Long workPlanId;
        private String taskName;
        private String requester;
        private String process;
        private String requestDate;     // createdAt 포맷
        private LocalDate oldStart;
        private LocalDate oldEnd;
        private LocalDate newStart;
        private LocalDate newEnd;
        private String reason;
        private String cause;
        private Map<String, Object> changeSummary;
        private List<Map<String, Object>> detailChanges;
        private Boolean aiApplied;
        private List<String> attachmentUrls;
        private String status;          // 프론트 상태 코드(pending/approved/applied/rejected)
        private String statusLabel;     // 한글 라벨
        private String rejectReason;
        private String approver;
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
