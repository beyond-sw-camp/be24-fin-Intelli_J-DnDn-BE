package org.example.dndncore.workorder.model;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.persistence.*;
import lombok.*;
import org.example.dndncore.common.model.BaseEntity;

// [WORKORDER_002] 2단계 : 작업 지시서 장비 엔티티 추가
@Entity
@Table(indexes = {
        @Index(name = "idx_work_order_equipment_order", columnList = "work_order_idx")
})
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
@Schema(description = "작업 지시서 장비 엔티티")
public class WorkOrderEquipment extends BaseEntity{
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Schema(description = "장비 ID", example = "1")
    private Long idx;

    // feat : 작업 지시서 연관관계 매핑 (N:1)
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "work_order_idx")
    @Schema(description = "작업 지시서")
    private WorkOrder workOrder;

    @Schema(description = "게이트 ID", example = "5")
    private Integer gateIdx;
    @Schema(description = "장비명", example = "크레인 10톤")
    private String equipmentName;
    @Schema(description = "장비 수량", example = "2")
    private Integer equipmentCount;

    // feat : 논리적 삭제 여부 (Soft Delete)
    @Column(name = "is_deleted", nullable = false)
    @Builder.Default
    @Schema(description = "삭제 여부", example = "false")
    private Boolean isDeleted = false;
}
