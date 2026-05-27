package org.example.dndncore.workplan.model.entity;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.persistence.*;
import lombok.*;
import org.example.dndncore.common.model.BaseEntity;
import org.example.dndncore.workplan.model.enums.EquipmentType;

@AllArgsConstructor
@NoArgsConstructor
@Getter
@Builder
@Entity
@Table(name = "work_plan_equipment")
@Schema(description = "feat : 작업 계획 장비 엔티티")
public class WorkPlanEquipment extends BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Schema(description = "장비 항목 ID")
    private Long idx;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "work_plan_idx")
    @Schema(description = "feat : 연관된 작업 계획")
    private WorkPlan workPlan;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    @Schema(description = "장비 종류", example = "TOWER_CRANE")
    private EquipmentType type;

    @Column(nullable = false)
    @Schema(description = "수량", example = "1")
    private Integer count;

    // feat : 양방향 연관관계 - WorkPlan에서 호출
    public void bindWorkPlan(WorkPlan workPlan) {
        this.workPlan = workPlan;
    }

    // feat : 표시용 문자열 생성
    public String toDisplay() {
        if (type == null || count == null) {
            return "";
        }

        return type.getLabel() + " " + count + "대";
    }
}