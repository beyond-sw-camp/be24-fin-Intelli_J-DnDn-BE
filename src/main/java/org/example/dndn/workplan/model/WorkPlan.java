package org.example.dndn.workplan.model;

import jakarta.persistence.*;
import lombok.*;
import org.example.dndn.common.model.BaseEntity;

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
public class WorkPlan extends BaseEntity {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long idx;

    private String name;            // 작업명 (공정명)
    private String location;        // 작업 위치/구역

    @Enumerated(EnumType.STRING)
    private WorkTrade trade;        // 공종

    @Enumerated(EnumType.STRING)
    private PlanType planType;      // 연간/월간/주간

    @Enumerated(EnumType.STRING)
    private PlanStatus status;      // 계획/검토 중/확정/진행 중

    private LocalDate startDate;    // 계획 시작일
    private LocalDate endDate;      // 계획 종료일
    private LocalDate actualStart;  // 실제 시작일

    /**
     * 필요 인원 - workers 합산값으로 자동 계산되는 derived 컬럼.
     * 외부에서 직접 set하지 않음. replaceWorkers() 호출 시 자동 동기화.
     * 컬럼 자체는 유지(검색/정렬/통계 편의).
     */
    private Integer requiredCount;

    private String partner;         // 협력사명 (주간 계획서)
    private String manager;         // 담당자명 (주간 계획서)
    private String contact;         // 담당자 연락처
    private String note;            // 비고

    @OneToMany(mappedBy = "workPlan", fetch = FetchType.LAZY, cascade = CascadeType.ALL, orphanRemoval = true)
    @Builder.Default
    private List<WorkPlanWorker> workers = new ArrayList<>();

    @OneToMany(mappedBy = "workPlan", fetch = FetchType.LAZY, cascade = CascadeType.ALL, orphanRemoval = true)
    @Builder.Default
    private List<WorkPlanEquipment> equipment = new ArrayList<>();

    @OneToOne(mappedBy = "workPlan", fetch = FetchType.LAZY, cascade = CascadeType.ALL, orphanRemoval = true)
    private WorkPlanExtension extension;

    /**
     * 화면에 보일 최종 종료일 - 연장이 있으면 연장 종료일, 없으면 원래 종료일
     */
    public LocalDate effectiveEndDate() {
        if (extension != null && extension.getExtendedEnd() != null) {
            return extension.getExtendedEnd();
        }

        return endDate;
    }

    /**
     * 작업 계획 기본 정보 수정
     * - requiredCount는 받지 않음. workers로부터 자동 계산.
     */
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

    /**
     * 인력 목록 일괄 교체 + requiredCount 자동 동기화
     */
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

    /**
     * 장비 목록 일괄 교체
     */
    public void replaceEquipment(List<WorkPlanEquipment> newEquipment) {
        this.equipment.clear();

        if (newEquipment != null) {
            for (WorkPlanEquipment eq : newEquipment) {
                eq.bindWorkPlan(this);
                this.equipment.add(eq);
            }
        }
    }

    /**
     * 일정 연장 등록/연결
     */
    public void attachExtension(WorkPlanExtension extension) {
        this.extension = extension;
        extension.bindWorkPlan(this);
    }

    /**
     * 실제 시작일 기록 (착수 처리)
     */
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

    /**
     * "전공 4명, 보통공 2명" 형태의 표시용 문자열 (응답 편의용)
     */
    public String workersDisplay() {
        if (workers == null || workers.isEmpty()) {
            return "";
        }

        return workers.stream()
                .map(WorkPlanWorker::toDisplay)
                .filter(s -> !s.isBlank())
                .collect(Collectors.joining(", "));
    }

    /**
     * "타워크레인 1대, 펌프카 1대" 형태의 표시용 문자열 (응답 편의용)
     */
    public String equipmentDisplay() {
        if (equipment == null || equipment.isEmpty()) {
            return "";
        }

        return equipment.stream()
                .map(WorkPlanEquipment::toDisplay)
                .filter(s -> !s.isBlank())
                .collect(Collectors.joining(", "));
    }

    /**
     * workers 합산으로 requiredCount 재계산
     */
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
}