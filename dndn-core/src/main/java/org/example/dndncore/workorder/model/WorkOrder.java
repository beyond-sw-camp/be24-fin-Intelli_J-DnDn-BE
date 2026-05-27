package org.example.dndncore.workorder.model;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.persistence.*;
import lombok.*;
import org.example.dndncore.common.model.BaseEntity;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

// [WORKORDER_001] 1단계 : 작업 지시서 기본 엔티티 설계
@Entity
@Table(indexes = {
        @Index(name = "idx_work_order_site_deleted_due", columnList = "site_idx,is_deleted,due_date"),
        @Index(name = "idx_work_order_deleted_due", columnList = "is_deleted,due_date")
})
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
@Schema(description = "작업 지시서 엔티티")
public class WorkOrder extends BaseEntity {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Schema(description = "작업 지시서 ID", example = "1")
    private Long idx;

    @Schema(description = "현장 ID", example = "1")
    private Long siteIdx;
    @Schema(description = "협력사 ID", example = "10")
    private Long partnerCompanyIdx;

    // [WORKORDER_007] 7단계 : 작업 지시서 승인 시 주간 계획 반영을 위한 연결 ID 추가
    // feat : 작업 지시서와 연결된 주간 작업 계획 ID
    @Column(name = "work_plan_id")
    @Schema(description = "작업 계획 ID", example = "100")
    private Long workPlanId;

    @Schema(description = "공종 유형", example = "CARPENTRY")
    private String tradeType;
    @Schema(description = "제목", example = "철근 배근 작업")
    private String title;

    @Column(columnDefinition = "TEXT")
    @Schema(description = "지시 내용", example = "A동 3층 철근 배근 진행")
    private String instructionContent;

    @Column(columnDefinition = "TEXT")
    @Schema(description = "작업 내용", example = "철근 결속 및 배근")
    private String workDetail;

    @Schema(description = "작업 시간", example = "08:00-17:00")
    private String workTime;

    @Column(columnDefinition = "TEXT")
    @Schema(description = "안전 유의사항", example = "안전모 착용 필수")
    private String safetyContent;

    @Schema(description = "마감일", example = "2026-05-30")
    private LocalDate dueDate;
    @Schema(description = "상태 코드", example = "PENDING")
    private String statusCode;

    // feat : 투입 인원 수
    @Column(name = "worker_count")
    @Schema(description = "투입 인원 수", example = "8")
    private Integer workerCount;

    // feat : 논리적 삭제 여부 (Soft Delete)
    @Column(name = "is_deleted", nullable = false)
    @Builder.Default
    @Schema(description = "삭제 여부", example = "false")
    private Boolean isDeleted = false;

    // [WORKORDER_002] 2단계 : 장비 매핑 로직 추가
    // feat : 연관된 장비 목록 (Cascade 설정)
    @OneToMany(mappedBy = "workOrder", cascade = CascadeType.ALL, orphanRemoval = true)
    @Builder.Default
    @Schema(description = "등록 장비 목록")
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
