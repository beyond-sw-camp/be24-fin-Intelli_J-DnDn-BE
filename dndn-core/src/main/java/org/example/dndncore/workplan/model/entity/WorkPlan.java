package org.example.dndncore.workplan.model.entity;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.persistence.*;
import lombok.*;
import org.example.dndncore.common.model.BaseEntity;
import org.example.dndncore.project.model.entity.TradeProcess;
import org.example.dndncore.workplan.model.enums.PlanStatus;
import org.example.dndncore.workplan.model.enums.PlanType;
import org.example.dndncore.workplan.model.enums.WorkTrade;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

@AllArgsConstructor
@NoArgsConstructor
@Getter
@Builder
@Entity
@Table(name = "work_plan")
@Schema(description = "feat : 작업 계획 엔티티")
public class WorkPlan extends BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Schema(description = "작업 계획 ID")
    private Long idx;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "trade_process_id")
    @Schema(description = "feat : 상위 공정 관계")
    private TradeProcess tradeProcess;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "parent_work_plan_id")
    @Schema(description = "feat : 상위 작업 계획 관계")
    private WorkPlan parentWorkPlan;

    @OneToMany(mappedBy = "parentWorkPlan", fetch = FetchType.LAZY)
    @Builder.Default
    @Schema(description = "feat : 하위 작업 계획 목록")
    private List<WorkPlan> childWorkPlans = new ArrayList<>();

    @Schema(description = "작업명", example = "기초 공사")
    private String name;

    @Schema(description = "작업 위치/구역", example = "A동 1층")
    private String location;

    @Enumerated(EnumType.STRING)
    @Schema(description = "공종", example = "EARTHWORK")
    private WorkTrade trade;

    @Enumerated(EnumType.STRING)
    @Schema(description = "계획 유형", example = "MONTHLY")
    private PlanType planType;

    @Enumerated(EnumType.STRING)
    @Schema(description = "상태", example = "PLANNED")
    private PlanStatus status;

    @Schema(description = "계획 시작일", example = "2026-05-27")
    private LocalDate startDate;

    @Schema(description = "계획 종료일", example = "2026-06-27")
    private LocalDate endDate;

    @Schema(description = "실제 시작일", example = "2026-05-28")
    private LocalDate actualStart;

    @Schema(description = "필요 인원 수", example = "6")
    private Integer requiredCount;

    @Schema(description = "협력사명", example = "ABC 건설")
    private String partner;

    @Schema(description = "담당자명", example = "홍길동")
    private String manager;

    @Schema(description = "담당자 연락처", example = "010-1234-5678")
    private String contact;

    @Column(columnDefinition = "TEXT")
    @Schema(description = "비고", example = "날씨에 따라 조정 가능")
    private String note;

    @Column(name = "actual_progress_pct")
    @Schema(description = "실제 진행률", example = "75.5")
    private BigDecimal actualProgressPct = BigDecimal.ZERO;

    @OneToMany(mappedBy = "workPlan", fetch = FetchType.LAZY, cascade = CascadeType.ALL, orphanRemoval = true)
    @Builder.Default
    @Schema(description = "feat : 작업 인력 목록")
    private List<WorkPlanWorker> workers = new ArrayList<>();

    @OneToMany(mappedBy = "workPlan", fetch = FetchType.LAZY, cascade = CascadeType.ALL, orphanRemoval = true)
    @Builder.Default
    @Schema(description = "feat : 작업 장비 목록")
    private List<WorkPlanEquipment> equipment = new ArrayList<>();

    @OneToOne(mappedBy = "workPlan", fetch = FetchType.LAZY, cascade = CascadeType.ALL, orphanRemoval = true)
    @Schema(description = "feat : 일정 연장 정보")
    private WorkPlanExtension extension;

    // feat : 유효한 종료일 계산 (연장이 있으면 연장 종료일 반환)
    public LocalDate effectiveEndDate() {
        if (extension != null && extension.getExtendedEnd() != null) {
            return extension.getExtendedEnd();
        }
        return endDate;
    }

    // feat : 상위 작업 계획 연결
    public void linkParentWorkPlan(WorkPlan parentWorkPlan) {
        this.parentWorkPlan = parentWorkPlan;
    }

    // feat : 상위 공정 연결
    public void linkTradeProcess(TradeProcess tradeProcess) {
        this.tradeProcess = tradeProcess;
    }

    // feat : 작업 계획 기본 정보 수정
    public void updateInfo(String name, WorkTrade trade, String location,
                           LocalDate startDate, LocalDate endDate,
                           PlanStatus status,
                           String partner, String manager, String contact, String note) {
        this.name = name;
        this.trade = trade;
        this.location = location;
        this.startDate = startDate;
        this.endDate = endDate;
        this.status = status;
        this.partner = partner;
        this.manager = manager;
        this.contact = contact;
        this.note = note;
    }

    // feat : 인력 목록 일괄 교체 및 필요 인원 자동 동기화
    public void replaceWorkers(List<WorkPlanWorker> newWorkers) {
        this.workers.clear();
        if (newWorkers != null) {
            for (WorkPlanWorker worker : newWorkers) {
                worker.bindWorkPlan(this);
                this.workers.add(worker);
            }
        }
        recalculateRequiredCount();
    }

    // feat : 장비 목록 일괄 교체
    public void replaceEquipment(List<WorkPlanEquipment> newEquipment) {
        this.equipment.clear();
        if (newEquipment != null) {
            for (WorkPlanEquipment eq : newEquipment) {
                eq.bindWorkPlan(this);
                this.equipment.add(eq);
            }
        }
    }

    // feat : 일정 연장 등록 및 연결
    public void attachExtension(WorkPlanExtension extension) {
        this.extension = extension;
        extension.bindWorkPlan(this);
    }

    // feat : 실제 시작일 기록 및 상태 변경
    public void markStarted(LocalDate actualStart) {
        if (actualStart == null) {
            throw new IllegalArgumentException("실제 시작일은 필수입니다.");
        }
        if (this.status == PlanStatus.IN_PROGRESS) {
            throw new IllegalStateException("이미 진행 중인 작업입니다.");
        }
        this.actualStart = actualStart;
        this.status = PlanStatus.IN_PROGRESS;
    }

    // feat : 인력 표시용 문자열 생성
    public String workersDisplay() {
        if (workers == null || workers.isEmpty()) return "";
        return workers.stream()
                .map(WorkPlanWorker::toDisplay)
                .filter(s -> !s.isBlank())
                .collect(Collectors.joining(", "));
    }

    // feat : 장비 표시용 문자열 생성
    public String equipmentDisplay() {
        if (equipment == null || equipment.isEmpty()) return "";
        return equipment.stream()
                .map(WorkPlanEquipment::toDisplay)
                .filter(s -> !s.isBlank())
                .collect(Collectors.joining(", "));
    }

    // feat : 필요 인원 자동 계산
    private void recalculateRequiredCount() {
        if (workers == null || workers.isEmpty()) {
            this.requiredCount = 0;
            return;
        }
        this.requiredCount = workers.stream()
                .map(WorkPlanWorker::getCount)
                .filter(c -> c != null)
                .mapToInt(Integer::intValue)
                .sum();
    }

    // feat : 실제 진행률 업데이트
    public void updateActualProgressPct(Double actualProgressPct) {
        double value = actualProgressPct == null
                ? 0.0
                : Math.max(0.0, Math.min(100.0, actualProgressPct));

        this.actualProgressPct = java.math.BigDecimal.valueOf(value);
    }
}
