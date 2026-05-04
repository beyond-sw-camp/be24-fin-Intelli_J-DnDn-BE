package org.example.dndn.workplan.model.entity;

import jakarta.persistence.*;
import lombok.*;
import org.example.dndn.common.model.BaseEntity;

import java.time.LocalDate;

@AllArgsConstructor
@NoArgsConstructor
@Getter
@Builder
@Entity
@Table(name = "work_plan_extension")
public class WorkPlanExtension extends BaseEntity {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long idx;

    @OneToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "work_plan_idx")
    private WorkPlan workPlan;

    private LocalDate extendedEnd;   // 연장된 종료일
    private Integer addedDays;       // 연장 일수
    private String reason;           // 연장 사유 (공정 분석 결과)
    private LocalDate decidedAt;     // 반영일


    public void bindWorkPlan(WorkPlan workPlan) {
        this.workPlan = workPlan;
    }


     // 연장 정보 수정
    public void update(LocalDate extendedEnd, Integer addedDays, String reason, LocalDate decidedAt) {
        this.extendedEnd = extendedEnd;
        this.addedDays = addedDays;
        this.reason = reason;
        this.decidedAt = decidedAt;
    }
}