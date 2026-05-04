package org.example.dndn.workplan.model.entity;

import jakarta.persistence.*;
import lombok.*;
import org.example.dndn.common.model.BaseEntity;
import org.example.dndn.workplan.model.enums.EquipmentType;

@AllArgsConstructor
@NoArgsConstructor
@Getter
@Builder
@Entity
@Table(name = "work_plan_equipment")
public class WorkPlanEquipment extends BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long idx;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "work_plan_idx")
    private WorkPlan workPlan;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private EquipmentType type;   // 장비 종류 (타워크레인/펌프카/...)

    @Column(nullable = false)
    private Integer count;         // 수량

    /**
     * 양방향 연관관계 - WorkPlan에서 호출
     */
    public void bindWorkPlan(WorkPlan workPlan) {
        this.workPlan = workPlan;
    }

    /**
     * 표시용 문자열 - "타워크레인 1대"
     */
    public String toDisplay() {
        if (type == null || count == null) {
            return "";
        }

        return type.getLabel() + " " + count + "대";
    }
}