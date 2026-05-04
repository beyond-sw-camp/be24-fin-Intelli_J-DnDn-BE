package org.example.dndn.project.model.entity;

import jakarta.persistence.*;
import lombok.*;
import org.example.dndn.common.model.BaseEntity;

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
    private String tradeName;       // 공종명 (예: 전기, 철근, 골조)

    @Column(nullable = false)
    private String processName;     // 공정명 (예: 배선 작업, 기초 철근 배근)

    private String partnerCompany;  // 시공계획서에서 추출된 협력사명

    private LocalDate plannedStart; // 계획 시작일 → 간트차트 보라색 선 시작
    private LocalDate plannedEnd;   // 계획 종료일 → 간트차트 보라색 선 종료

    private Float weightPct;        // 보할율 (전체 공사 대비 비중)

    @Builder.Default
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

    /**
     * 승인된 일정 변경 요청 반영 시에만 호출
     */
    public void applyScheduleChange(LocalDate newStart, LocalDate newEnd) {
        this.plannedStart = newStart;
        this.plannedEnd = newEnd;
    }
}