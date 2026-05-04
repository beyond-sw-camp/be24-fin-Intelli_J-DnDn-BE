package org.example.dndn.workplan.model.entity;

import jakarta.persistence.*;
import lombok.*;
import org.example.dndn.common.model.BaseEntity;
import org.example.dndn.workplan.model.enums.WorkerTrade;

@AllArgsConstructor
@NoArgsConstructor
@Getter
@Builder
@Entity
@Table(name = "work_plan_worker")
public class WorkPlanWorker extends BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long idx;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "work_plan_idx")
    private WorkPlan workPlan;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private WorkerTrade trade;   // 직종 (전공/보통공/...)

    @Column(nullable = false)
    private Integer count;        // 인원수

    /**
     * 양방향 연관관계 - WorkPlan에서 호출
     */
    public void bindWorkPlan(WorkPlan workPlan) {
        this.workPlan = workPlan;
    }

    /**
     * 표시용 문자열 - "전공 4명"
     */
    public String toDisplay() {
        if (trade == null || count == null) {
            return "";
        }

        return trade.getLabel() + " " + count + "명";
    }
}