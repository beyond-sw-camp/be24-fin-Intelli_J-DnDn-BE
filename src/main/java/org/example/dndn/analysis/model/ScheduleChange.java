package org.example.dndn.analysis.model;

import jakarta.persistence.*;
import lombok.*;
import org.example.dndn.common.model.BaseEntity;
import org.example.dndn.project.model.entity.Project;
import org.example.dndn.project.model.entity.TradeProcess;
import org.example.dndn.workplan.model.entity.WorkPlan;

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
public class ScheduleChange extends BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long idx;

    // ── 연관 관계 ──────────────────────────────────────────────────────────

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "project_id", nullable = false)
    private Project project;

    /**
     * nullable — tradeProcess 없이 등록하는 경우도 허용.
     * AI 추천안 기반 요청은 연결, 수동 요청은 null 가능.
     */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "trade_process_id")
    private TradeProcess tradeProcess;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "work_plan_id")
    private WorkPlan workPlan;

    // ── 요청 내용 ──────────────────────────────────────────────────────────

    @Column(nullable = false)
    private String taskName;    // 작업명 (예: "기초 철근 배근")

    @Column(nullable = false)
    private String requester;   // 요청자 표시명 (예: "김철수 (철근 책임자)")

    @Column(nullable = false)
    private String process;     // 공종명 — 필터 조회용 denormalized 컬럼

    private LocalDate oldStart;
    private LocalDate oldEnd;

    @Column(nullable = false)
    private LocalDate newStart;

    @Column(nullable = false)
    private LocalDate newEnd;

    @Column(nullable = false, columnDefinition = "TEXT")
    private String reason;      // 변경 사유

    private String cause;       // 지연 원인 (기상/인력/자재 등)

    @Column(columnDefinition = "TEXT")
    private String changeSummaryJson;   // 승인 화면 요약 데이터(JSON)

    @Column(columnDefinition = "TEXT")
    private String detailChangesJson;   // 세부일정별 변경 데이터(JSON 배열)

    @Builder.Default
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
    private ScheduleChangeStatus status = ScheduleChangeStatus.PENDING;

    private String rejectReason;    // 반려 사유
    private String approver;        // 처리자 표시명 (예: "이감독 (현장 총 책임자)")
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
