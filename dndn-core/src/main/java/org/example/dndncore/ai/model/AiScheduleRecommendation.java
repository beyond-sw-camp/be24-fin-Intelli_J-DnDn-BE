package org.example.dndncore.ai.model;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.persistence.*;
import lombok.*;
import org.example.dndncore.common.model.BaseEntity;
import org.example.dndncore.project.model.entity.Project;
import org.example.dndncore.workplan.model.entity.WorkPlan;

@AllArgsConstructor
@NoArgsConstructor
@Getter
@Builder
@Entity
@Table(name = "ai_schedule_recommendation")
@Schema(description = "AI 스케줄 추천 엔티티")
public class AiScheduleRecommendation extends BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Schema(description = "AI 스케줄 추천 ID", example = "1")
    private Long idx;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "project_id", nullable = false)
    @Schema(description = "대상 프로젝트")
    private Project project;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "monthly_work_plan_id", nullable = false)
    @Schema(description = "대상 월간 작업계획")
    private WorkPlan monthlyWorkPlan;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    @Schema(description = "추천 상태", example = "PENDING")
    private AiScheduleRecommendationStatus status;

    @Column(columnDefinition = "LONGTEXT")
    @Schema(description = "요청 컨텍스트 JSON")
    private String contextJson;

    @Column(columnDefinition = "LONGTEXT")
    @Schema(description = "AI 추천 결과 JSON")
    private String resultJson;

    @Column(columnDefinition = "TEXT")
    @Schema(description = "실패 메시지")
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
