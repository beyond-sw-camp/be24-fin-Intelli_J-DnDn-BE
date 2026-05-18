package org.example.dndn.report.model;

import jakarta.persistence.*;
import lombok.*;
import org.example.dndn.common.model.BaseEntity;
import org.example.dndn.workplan.model.entity.WorkPlan;

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
public class DailyReport extends BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long idx;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "work_plan_idx")
    private WorkPlan workPlan;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "monthly_work_plan_idx")
    private WorkPlan monthlyWorkPlan;

    private Double actualProgress;

    private Double todayProgress;

    private Double progressIncrementPct;

    private Double monthlyProgressPct;

    private Integer actualWorkerCount;

    private String location;

    private String issue;

    private LocalDate reportDate;

    @Column(columnDefinition = "TEXT")
    private String todayWork;

    @Column(columnDefinition = "TEXT")
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
