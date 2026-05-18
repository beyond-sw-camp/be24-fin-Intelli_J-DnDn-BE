package org.example.dndncore.workorder.model;

import jakarta.persistence.*;
import lombok.*;
import org.example.dndncore.common.model.BaseEntity;

// [WORKORDER_002] 2단계 : 작업 지시서 장비 엔티티 추가
@Entity
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class WorkOrderEquipment extends BaseEntity{
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long idx;

    // feat : 작업 지시서 연관관계 매핑 (N:1)
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "work_order_idx")
    private WorkOrder workOrder;

    private Integer gateIdx;
    private String equipmentName;
    private Integer equipmentCount;

    // feat : 논리적 삭제 여부 (Soft Delete)
    @Column(name = "is_deleted", nullable = false)
    @Builder.Default
    private Boolean isDeleted = false;
}
