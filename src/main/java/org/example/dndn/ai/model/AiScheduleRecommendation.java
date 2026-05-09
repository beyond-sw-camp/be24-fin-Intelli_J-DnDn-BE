package org.example.dndn.ai.model;

import jakarta.persistence.*;
import lombok.*;
import org.example.dndn.common.model.BaseEntity;
import org.example.dndn.project.model.entity.Project;
import org.example.dndn.workplan.model.entity.WorkPlan;

@AllArgsConstructor
@NoArgsConstructor
@Getter
@Builder
@Entity
@Table(name = "ai_schedule_recommendation")
public class AiScheduleRecommendation extends BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long idx;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "project_id", nullable = false)
    private Project project;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "monthly_work_plan_id", nullable = false)
    private WorkPlan monthlyWorkPlan;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private AiScheduleRecommendationStatus status;

    @Column(columnDefinition = "LONGTEXT")
    private String contextJson;

    @Column(columnDefinition = "LONGTEXT")
    private String resultJson;

    @Column(columnDefinition = "TEXT")
    private String errorMessage;

    public static AiScheduleRecommendation pending(Project project, WorkPlan monthlyWorkPlan, String contextJson) {
        return AiScheduleRecommendation.builder()
                .project(project)
                .monthlyWorkPlan(monthlyWorkPlan)
                .status(AiScheduleRecommendationStatus.PENDING)
                .contextJson(contextJson)
                .build();
    }

    public void markSuccess(String resultJson) {
        this.status = AiScheduleRecommendationStatus.SUCCESS;
        this.resultJson = resultJson;
        this.errorMessage = null;
    }

    public void markFailed(String errorMessage) {
        this.status = AiScheduleRecommendationStatus.FAILED;
        this.errorMessage = errorMessage;
    }
}
