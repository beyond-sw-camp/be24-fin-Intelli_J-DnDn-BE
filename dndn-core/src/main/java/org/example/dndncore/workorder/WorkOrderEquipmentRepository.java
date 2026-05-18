package org.example.dndncore.workorder;

import org.example.dndncore.workorder.model.WorkOrderEquipment;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

// [WORKORDER_002] 2단계 : 작업 지시서 장비 Repository
public interface WorkOrderEquipmentRepository extends JpaRepository<WorkOrderEquipment, Long> {

    // [WORKORDER_005] 5단계 : 기존 장비 일괄 삭제 쿼리 추가
    // feat : 특정 작업 지시서에 연결된 장비 일괄 삭제
    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("DELETE FROM WorkOrderEquipment e WHERE e.workOrder.idx = :orderIdx")
    void deleteAllByWorkOrderIdx(@Param("orderIdx") Long orderIdx);
}
