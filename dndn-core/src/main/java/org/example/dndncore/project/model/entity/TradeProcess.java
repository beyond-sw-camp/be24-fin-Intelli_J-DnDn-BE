package org.example.dndncore.project.model.entity;

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
@Table(name = "trade_process")
@Schema(description = "공정 엔티티")
public class TradeProcess extends BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Schema(description = "공정 ID", example = "1")
    private Long idx;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "master_schedule_id", nullable = false)
    @Schema(description = "마스터 공정표")
    private MasterSchedule masterSchedule;

    @Column(nullable = false)
    @Schema(description = "공종명", example = "골조")
    private String tradeName;

    @Column(nullable = false)
    @Schema(description = "공정명", example = "기초 철근 배근")
    private String processName;

    @Schema(description = "협력사명", example = "OO건설")
    private String partnerCompany;

    @Schema(description = "계획 시작일", example = "2026-05-01")
    private LocalDate plannedStart;
    @Schema(description = "계획 종료일", example = "2026-05-20")
    private LocalDate plannedEnd;

    @Schema(description = "보할율", example = "12.5")
    private Float weightPct;

    @Builder.Default
    @Schema(description = "마일스톤 여부", example = "false")
    private Boolean isMilestone = false;

    public void update(String tradeName, String processName, String partnerCompany,
                       LocalDate plannedStart, LocalDate plannedEnd,
                       Float weightPct, Boolean isMilestone) {
        this.tradeName = tradeName;
        this.processName = processName;
        this.partnerCompany = partnerCompany;
        this.plannedStart = plannedStart;
        this.plannedEnd = plannedEnd;
        this.weightPct = weightPct;
        this.isMilestone = isMilestone != null ? isMilestone : false;
    }

    // feat : 승인된 일정 변경 요청 반영 시 호출
    public void applyScheduleChange(LocalDate newStart, LocalDate newEnd) {
        this.plannedStart = newStart;
        this.plannedEnd = newEnd;
    }
}