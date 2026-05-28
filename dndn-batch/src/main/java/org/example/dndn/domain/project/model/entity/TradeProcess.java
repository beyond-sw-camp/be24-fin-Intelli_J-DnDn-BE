package org.example.dndn.domain.project.model.entity;

import jakarta.persistence.*;
import lombok.*;
import org.example.dndn.domain.common.model.BaseEntity;

import java.time.LocalDate;

@AllArgsConstructor
@NoArgsConstructor
@Getter
@Builder
@Entity
@Table(name = "trade_process")
public class TradeProcess extends BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long idx;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "master_schedule_id", nullable = false)
    private MasterSchedule masterSchedule;

    @Column(nullable = false)
    private String tradeName;

    @Column(nullable = false)
    private String processName;

    private String partnerCompany;
    private LocalDate plannedStart;
    private LocalDate plannedEnd;
    private Float weightPct;

    @Builder.Default
    private Boolean isMilestone = false;
}
