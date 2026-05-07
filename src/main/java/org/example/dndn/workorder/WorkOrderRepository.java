package org.example.dndn.workorder;

import org.example.dndn.workorder.model.WorkOrder;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;

// [WORKORDER_003] 3단계 : 작업 지시서 Repository
public interface WorkOrderRepository extends JpaRepository<WorkOrder, Long> {

    // [WORKORDER_006] 6단계 : 주간계획 연동 초안 장비 불러오기 기능 쿼리
    // feat : WorkPlan 도메인 수정 없이 DB에서 장비 정보 조회
    @Query(value = "SELECT e.type, e.count FROM work_plan_equipment e WHERE e.work_plan_idx = :planIdx", nativeQuery = true)
    List<Object[]> findEquipmentsByPlanIdx(@Param("planIdx") Long planIdx);
}
