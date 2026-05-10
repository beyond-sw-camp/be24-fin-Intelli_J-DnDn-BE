package org.example.dndn.staffing.model;

import jakarta.persistence.*;
import lombok.*;
import org.example.dndn.common.model.BaseEntity;
import org.hibernate.annotations.BatchSize;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

@Entity
@Table(name = "zone_sub")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor(access = AccessLevel.PRIVATE)
@Builder
public class ZoneSub extends BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long idx;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "zone_main_idx", nullable = false)
    private ZoneMain zoneMain;

    @Column(nullable = false, length = 150)
    private String title;

    private int required;

    private int displayOrder;

    @Column(name = "work_plan_idx", unique = true)
    private Long workPlanIdx;

    @Column(name = "work_date")
    private LocalDate workDate;

    @Column(length = 150)
    private String location;

    @Column(name = "trade_name", length = 50)
    private String tradeName;

    @Column(name = "work_time", length = 50)
    private String workTime;

    @BatchSize(size = 64)
    @OneToMany(mappedBy = "zoneSub", cascade = CascadeType.ALL, orphanRemoval = true)
    @Builder.Default
    @OrderBy("trade ASC")
    private List<TradeNeed> tradeNeeds = new ArrayList<>();

    @BatchSize(size = 64)
    @OneToMany(mappedBy = "zoneSub", cascade = CascadeType.ALL, orphanRemoval = true)
    @Builder.Default
    private List<StaffingAssignment> assignments = new ArrayList<>();

    public void rename(String title) {
        this.title = title;
    }

    // 필요 총 인원 합 또는 투입 수 기준 재계산
    public void updateRequired(int required) {
        this.required = required;
    }

    public void updateFromWorkPlan(
            ZoneMain zoneMain,
            String title,
            int required,
            int displayOrder,
            Long workPlanIdx,
            LocalDate workDate,
            String location,
            String tradeName,
            String workTime
    ) {
        this.zoneMain = zoneMain;
        this.title = title;
        this.required = required;
        this.displayOrder = displayOrder;
        this.workPlanIdx = workPlanIdx;
        this.workDate = workDate;
        this.location = location;
        this.tradeName = tradeName;
        this.workTime = workTime;
    }
}
