package org.example.dndn.workorder.model;

import jakarta.persistence.*;
import lombok.*;
import org.example.dndn.common.model.BaseEntity;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

// [WORKORDER_001] 1단계 : 작업 지시서 기본 엔티티 설계
@Entity
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class WorkOrder extends BaseEntity {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long idx;

    private Long siteIdx;
    private Long partnerCompanyIdx;

    // [WORKORDER_007] 7단계 : 작업 지시서 승인 시 주간 계획 반영을 위한 연결 ID 추가
    // feat : 작업 지시서와 연결된 주간 작업 계획 ID
    @Column(name = "work_plan_id")
    private Long workPlanId;

    private String tradeType;
    private String title;

    @Column(columnDefinition = "TEXT")
    private String instructionContent;

    @Column(columnDefinition = "TEXT")
    private String workDetail;

    private String workTime;

    @Column(columnDefinition = "TEXT")
    private String safetyContent;

    private LocalDate dueDate;
    private String statusCode;

    // feat : 투입 인원 수
    @Column(name = "worker_count")
    private Integer workerCount;

    // feat : 논리적 삭제 여부 (Soft Delete)
    @Column(name = "is_deleted", nullable = false)
    @Builder.Default
    private Boolean isDeleted = false;

    // [WORKORDER_002] 2단계 : 장비 매핑 로직 추가
    // feat : 연관된 장비 목록 (Cascade 설정)
    @OneToMany(mappedBy = "workOrder", cascade = CascadeType.ALL, orphanRemoval = true)
    @Builder.Default
    private List<WorkOrderEquipment> equipments = new ArrayList<>();

    // [WORKORDER_002] 2단계 : 장비 매핑 로직 추가
    // feat : 장비 추가 편의 메서드
    public void addEquipment(WorkOrderEquipment equipment) {
        equipments.add(equipment);
        equipment.setWorkOrder(this);
    }

    // [WORKORDER_005] 5단계 : 장비 초기화 로직 추가 (수정 기능 고도화)
    // feat : 장비 초기화 편의 메서드
    public void clearEquipments() {
        for (WorkOrderEquipment eq : this.equipments) {
            eq.setWorkOrder(null);
        }
        this.equipments.clear();
    }
}
