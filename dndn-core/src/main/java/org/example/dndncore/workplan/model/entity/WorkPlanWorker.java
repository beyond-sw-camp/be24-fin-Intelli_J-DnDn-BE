package org.example.dndncore.workplan.model.entity;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.persistence.*;
import lombok.*;
import org.example.dndncore.common.model.BaseEntity;
import org.example.dndncore.workplan.model.enums.WorkerTrade;

@AllArgsConstructor
@NoArgsConstructor
@Getter
@Builder
@Entity
@Table(name = "work_plan_worker")
@Schema(description = "feat : 작업 계획 인력 엔티티")
public class WorkPlanWorker extends BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Schema(description = "인력 항목 ID")
    private Long idx;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "work_plan_idx")
    @Schema(description = "feat : 연관된 작업 계획")
    private WorkPlan workPlan;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    @Schema(description = "직종", example = "SKILLED")
    private WorkerTrade trade;

    @Column(nullable = false)
    @Schema(description = "인원수", example = "4")
    private Integer count;

    // feat : 양방향 연관관계 - WorkPlan에서 호출
    public void bindWorkPlan(WorkPlan workPlan) {
        this.workPlan = workPlan;
    }

    // feat : 표시용 문자열 생성
    public String toDisplay() {
        if (trade == null || count == null) {
            return "";
        }

        return trade.getLabel() + " " + count + "명";
    }
}