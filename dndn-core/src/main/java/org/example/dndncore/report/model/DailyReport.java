package org.example.dndncore.report.model;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.persistence.*;
import lombok.*;
import org.example.dndncore.common.model.BaseEntity;
import org.example.dndncore.workplan.model.entity.WorkPlan;

import java.time.LocalDate;

@Entity
@Table(
        name = "daily_report",
        indexes = {
                @Index(name = "idx_daily_report_monthly_plan_date", columnList = "monthly_work_plan_idx, report_date"),
                @Index(name = "idx_daily_report_work_plan_date", columnList = "work_plan_idx, report_date")
        }
)
@Getter
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "공사일보 엔티티")
public class DailyReport extends BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Schema(description = "공사일보 ID", example = "1")
    private Long idx;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "work_plan_idx")
    @Schema(description = "작업 계획")
    private WorkPlan workPlan;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "monthly_work_plan_idx")
    @Schema(description = "월간 작업 계획")
    private WorkPlan monthlyWorkPlan;

    @Schema(description = "누적 실제 진척률", example = "45.5")
    private Double actualProgress;

    @Schema(description = "당일 진척률", example = "2.3")
    private Double todayProgress;

    @Schema(description = "진척 증가율", example = "0.8")
    private Double progressIncrementPct;

    @Schema(description = "월간 진척률", example = "61.2")
    private Double monthlyProgressPct;

    @Schema(description = "실제 투입 인원 수", example = "18")
    private Integer actualWorkerCount;

    @Schema(description = "작업 위치", example = "A동 3층")
    private String location;

    @Schema(description = "특이사항", example = "콘크리트 타설 전 거푸집 점검 완료")
    private String issue;

    @Schema(description = "공사일보 기준 일자", example = "2026-05-27")
    private LocalDate reportDate;

    @Column(columnDefinition = "TEXT")
    @Schema(description = "당일 작업 내용", example = "철근 배근 및 결속 작업 진행")
    private String todayWork;

    @Column(columnDefinition = "TEXT")
    @Schema(description = "익일 작업 계획", example = "거푸집 설치 및 동바리 보강")
    private String tomorrowPlan;

    public void updateReport(
            WorkPlan monthlyWorkPlan,
            Double actualProgress,
            Double todayProgress,
            Double progressIncrementPct,
            Double monthlyProgressPct,
            Integer actualWorkerCount,
            String location,
            String issue,
            String todayWork,
            String tomorrowPlan
    ) {
        this.monthlyWorkPlan = monthlyWorkPlan;
        this.actualProgress = actualProgress;
        this.todayProgress = todayProgress;
        this.progressIncrementPct = progressIncrementPct;
        this.monthlyProgressPct = monthlyProgressPct;
        this.actualWorkerCount = actualWorkerCount;
        this.location = location;
        this.issue = issue;
        this.todayWork = todayWork;
        this.tomorrowPlan = tomorrowPlan;
    }
}
