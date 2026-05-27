package org.example.dndncore.project.model.entity;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.persistence.*;
import lombok.*;
import org.example.dndncore.common.model.BaseEntity;
import org.example.dndncore.project.model.enums.MilestoneStatus;

import java.time.LocalDate;

@AllArgsConstructor
@NoArgsConstructor
@Getter
@Builder
@Entity
@Table(name = "milestone")
@Schema(description = "마일스톤 엔티티")
public class Milestone extends BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Schema(description = "마일스톤 ID", example = "1")
    private Long idx;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "trade_process_id", nullable = false)
    @Schema(description = "공정")
    private TradeProcess tradeProcess;

    @Column(nullable = false)
    @Schema(description = "마일스톤명", example = "지하층 골조 완료")
    private String name;

    @Schema(description = "계획 일자", example = "2026-05-10")
    private LocalDate plannedDate;
    @Schema(description = "실제 완료 일자", example = "2026-05-12")
    private LocalDate actualDate;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    @Schema(description = "상태", example = "PLANNED")
    private MilestoneStatus status;

    public void complete(LocalDate actualDate) {
        this.actualDate = actualDate;
        this.status = MilestoneStatus.DONE;
    }

    public void updateStatus(MilestoneStatus status) {
        this.status = status;
    }
}