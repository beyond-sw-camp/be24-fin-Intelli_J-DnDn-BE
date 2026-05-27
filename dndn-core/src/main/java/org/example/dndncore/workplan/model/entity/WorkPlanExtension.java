package org.example.dndncore.workplan.model.entity;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.persistence.*;
import lombok.*;
import org.example.dndncore.common.model.BaseEntity;

import java.time.LocalDate;

@AllArgsConstructor
@NoArgsConstructor
@Getter
@Builder
@Entity
@Table(name = "work_plan_extension")
@Schema(description = "feat : 작업 계획 일정 연장 엔티티")
public class WorkPlanExtension extends BaseEntity {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Schema(description = "일정 연장 ID")
    private Long idx;

    @OneToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "work_plan_idx")
    @Schema(description = "feat : 연관된 작업 계획")
    private WorkPlan workPlan;

    @Schema(description = "연장된 종료일", example = "2026-07-27")
    private LocalDate extendedEnd;

    @Schema(description = "연장 일수", example = "30")
    private Integer addedDays;

    @Schema(description = "연장 사유", example = "날씨 지연")
    private String reason;

    @Schema(description = "반영일", example = "2026-06-27")
    private LocalDate decidedAt;

    // feat : 양방향 연관관계 - WorkPlan에서 호출
    public void bindWorkPlan(WorkPlan workPlan) {
        this.workPlan = workPlan;
    }

    // feat : 일정 연장 정보 수정
    public void update(LocalDate extendedEnd, Integer addedDays, String reason, LocalDate decidedAt) {
        this.extendedEnd = extendedEnd;
        this.addedDays = addedDays;
        this.reason = reason;
        this.decidedAt = decidedAt;
    }
}