package org.example.dndncore.staffing.model;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.persistence.*;
import lombok.*;
import org.example.dndncore.common.model.BaseEntity;
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
@Schema(description = "인력 배치 상세 구역 엔티티")
public class ZoneSub extends BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Schema(description = "상세 구역 ID", example = "10")
    private Long idx;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "zone_main_idx", nullable = false)
    @Schema(description = "상위 기본 구역")
    private ZoneMain zoneMain;

    @Column(nullable = false, length = 150)
    @Schema(description = "상세 구역명", example = "A동 3층 거푸집")
    private String title;

    @Schema(description = "필요 인원", example = "8")
    private int required;

    @Schema(description = "표시 순서", example = "2")
    private int displayOrder;

    @Column(name = "work_plan_idx", unique = true)
    @Schema(description = "연결된 작업 계획 ID", example = "1001")
    private Long workPlanIdx;

    @Column(name = "work_date")
    @Schema(description = "작업 일자", example = "2026-05-27")
    private LocalDate workDate;

    @Column(length = 150)
    @Schema(description = "작업 위치", example = "A동 3층")
    private String location;

    @Column(name = "trade_name", length = 50)
    @Schema(description = "공종명", example = "철근")
    private String tradeName;

    @Column(name = "work_time", length = 50)
    @Schema(description = "작업 시간", example = "08:00-17:00")
    private String workTime;

    @BatchSize(size = 64)
    @OneToMany(mappedBy = "zoneSub", cascade = CascadeType.ALL, orphanRemoval = true)
    @Builder.Default
    @OrderBy("trade ASC")
    @Schema(description = "직종별 필요 인원 목록")
    private List<TradeNeed> tradeNeeds = new ArrayList<>();

    @BatchSize(size = 64)
    @OneToMany(mappedBy = "zoneSub", cascade = CascadeType.ALL, orphanRemoval = true)
    @Builder.Default
    @Schema(description = "현재 배치 목록")
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
