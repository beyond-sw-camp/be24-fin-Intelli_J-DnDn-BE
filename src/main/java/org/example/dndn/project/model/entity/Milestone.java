package org.example.dndn.project.model.entity;

import jakarta.persistence.*;
import lombok.*;
import org.example.dndn.common.model.BaseEntity;
import org.example.dndn.project.model.enums.MilestoneStatus;

import java.time.LocalDate;

@AllArgsConstructor
@NoArgsConstructor
@Getter
@Builder
@Entity
@Table(name = "milestone")
public class Milestone extends BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long idx;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "trade_process_id", nullable = false)
    private TradeProcess tradeProcess;

    @Column(nullable = false)
    private String name;            // 마일스톤명 (예: "지하층 골조 완료")

    private LocalDate plannedDate;  // 계획 일자
    private LocalDate actualDate;   // 실제 완료 일자

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private MilestoneStatus status; // 예정/완료/지연

    public void complete(LocalDate actualDate) {
        this.actualDate = actualDate;
        this.status = MilestoneStatus.DONE;
    }

    public void updateStatus(MilestoneStatus status) {
        this.status = status;
    }
}