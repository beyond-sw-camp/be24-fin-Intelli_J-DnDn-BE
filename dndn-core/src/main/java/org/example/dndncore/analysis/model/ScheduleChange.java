package org.example.dndncore.analysis.model;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.persistence.*;
import lombok.*;
import org.example.dndncore.common.model.BaseEntity;
import org.example.dndncore.project.model.entity.Project;
import org.example.dndncore.project.model.entity.TradeProcess;
import org.example.dndncore.workplan.model.entity.WorkPlan;

import java.time.LocalDate;

/**
 * 일정 변경 요청 엔티티
 *
 * 라이프사이클:
 *   공정 책임자 등록 (PENDING)
 *     → 총 책임자 승인 (APPROVED) 또는 반려 (REJECTED)
 *     → 승인 후 공정표 반영 (APPLIED)
 *
 * APPLIED 시 TradeProcess.applyScheduleChange() + WorkPlanExtension.update() 호출
 */
@Entity
@Table(name = "schedule_change_request")
@Getter
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "일정 변경 요청 엔티티")
public class ScheduleChange extends BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Schema(description = "요청 ID", example = "1")
    private Long idx;

    // ── 연관 관계 ──────────────────────────────────────────────────────────

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "project_id", nullable = false)
    @Schema(description = "프로젝트")
    private Project project;

    /**
     * nullable — tradeProcess 없이 등록하는 경우도 허용.
     * AI 추천안 기반 요청은 연결, 수동 요청은 null 가능.
     */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "trade_process_id")
    @Schema(description = "공정 단계")
    private TradeProcess tradeProcess;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "work_plan_id")
    @Schema(description = "작업 계획")
    private WorkPlan workPlan;

    // ── 요청 내용 ──────────────────────────────────────────────────────────

    @Column(nullable = false)
    @Schema(description = "작업명", example = "기초 철근 배근")
    private String taskName;    // 작업명 (예: "기초 철근 배근")

    @Column(nullable = false)
    @Schema(description = "요청자", example = "김철수 (철근 책임자)")
    private String requester;   // 요청자 표시명 (예: "김철수 (철근 책임자)")

    @Column(nullable = false)
    @Schema(description = "공정/공종", example = "철근콘크리트공사")
    private String process;     // 공종명 — 필터 조회용 denormalized 컬럼

    @Schema(description = "기존 시작일", example = "2026-05-01")
    private LocalDate oldStart;
    @Schema(description = "기존 종료일", example = "2026-05-10")
    private LocalDate oldEnd;

    @Column(nullable = false)
    @Schema(description = "변경 시작일", example = "2026-05-03")
    private LocalDate newStart;

    @Column(nullable = false)
    @Schema(description = "변경 종료일", example = "2026-05-12")
    private LocalDate newEnd;

    @Column(nullable = false, columnDefinition = "TEXT")
    @Schema(description = "변경 사유")
    private String reason;      // 변경 사유

    @Schema(description = "지연 원인")
    private String cause;       // 지연 원인 (기상/인력/자재 등)

    @Column(columnDefinition = "TEXT")
    @Schema(description = "변경 요약 JSON")
    private String changeSummaryJson;   // 승인 화면 요약 데이터(JSON)

    @Column(columnDefinition = "TEXT")
    @Schema(description = "세부 변경 JSON")
    private String detailChangesJson;   // 세부일정별 변경 데이터(JSON 배열)

    @Builder.Default
    @Schema(description = "AI 반영 여부", example = "false")
    private Boolean aiApplied = false;  // AI 추천안 반영 여부

    // ── 첨부파일 ──────────────────────────────────────────────────────────

    /**
     * 첨부파일 URL 목록을 콤마 구분 문자열로 저장.
     * 파일 업로드 API와 연동 시 S3 URL 등을 여기에 저장.
     */
    private String attachmentUrls;

    // ── 상태 / 처리 결과 ──────────────────────────────────────────────────

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    @Builder.Default
    @Schema(description = "상태", example = "PENDING")
    private ScheduleChangeStatus status = ScheduleChangeStatus.PENDING;

    @Schema(description = "반려 사유")
    private String rejectReason;    // 반려 사유
    @Schema(description = "처리자")
    private String approver;        // 처리자 표시명 (예: "이감독 (현장 총 책임자)")
    @Schema(description = "처리일", example = "2026-05-28")
    private LocalDate processedAt;  // 승인/반려/반영 처리일

    // ── 도메인 메서드 ──────────────────────────────────────────────────────

    /**
     * 총 책임자 승인
     */
    public void approve(String approver) {
        validateStatus(ScheduleChangeStatus.PENDING, "승인");
        this.status = ScheduleChangeStatus.APPROVED;
        this.approver = approver;
        this.processedAt = LocalDate.now();
    }

    /**
     * 총 책임자 반려
     */
    public void reject(String approver, String rejectReason) {
        validateStatus(ScheduleChangeStatus.PENDING, "반려");
        if (rejectReason == null || rejectReason.isBlank()) {
            throw new IllegalArgumentException("반려 사유는 필수입니다.");
        }
        this.status = ScheduleChangeStatus.REJECTED;
        this.approver = approver;
        this.rejectReason = rejectReason;
        this.processedAt = LocalDate.now();
    }

    /**
     * 공정표 반영 처리 — 실제 TradeProcess/WorkPlan 수정은 서비스에서 수행 후 호출.
     */
    public void markApplied() {
        validateStatus(ScheduleChangeStatus.APPROVED, "공정표 반영");
        this.status = ScheduleChangeStatus.APPLIED;
        this.processedAt = LocalDate.now();
    }

    private void validateStatus(ScheduleChangeStatus required, String action) {
        if (this.status != required) {
            throw new IllegalStateException(
                    String.format("'%s' 상태에서만 %s할 수 있습니다. 현재 상태: %s",
                            required.getLabel(), action, this.status.getLabel()));
        }
    }
}
