package org.example.dndncore.workorder;

import org.example.dndncore.workorder.model.WorkOrder;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDate;
import java.util.List;

// [WORKORDER_003] 3단계 : 작업 지시서 Repository
public interface WorkOrderRepository extends JpaRepository<WorkOrder, Long> {

    @Query("""
            SELECT DISTINCT wo
            FROM WorkOrder wo
            LEFT JOIN FETCH wo.equipments
            WHERE (wo.isDeleted = false OR wo.isDeleted IS NULL)
            ORDER BY wo.dueDate DESC, wo.idx DESC
            """)
    List<WorkOrder> findActiveWithEquipments();

    @Query("""
            SELECT DISTINCT wo
            FROM WorkOrder wo
            LEFT JOIN FETCH wo.equipments
            WHERE (wo.isDeleted = false OR wo.isDeleted IS NULL)
              AND wo.siteIdx IN :siteIds
            ORDER BY wo.dueDate DESC, wo.idx DESC
            """)
    List<WorkOrder> findActiveBySiteIdxInWithEquipments(@Param("siteIds") List<Long> siteIds);

    @Query("""
            SELECT wo.idx
            FROM WorkOrder wo
            WHERE (wo.isDeleted = false OR wo.isDeleted IS NULL)
              AND (:targetDate IS NULL OR wo.dueDate = :targetDate)
              AND (:tradeType IS NULL OR wo.tradeType = :tradeType)
              AND (:statusCode IS NULL OR wo.statusCode = :statusCode OR (:statusCode = 'OPEN' AND wo.statusCode IS NULL))
              AND (
                    :keyword IS NULL
                    OR LOWER(wo.title) LIKE LOWER(CONCAT('%', :keyword, '%'))
                    OR LOWER(wo.workDetail) LIKE LOWER(CONCAT('%', :keyword, '%'))
                    OR LOWER(wo.instructionContent) LIKE LOWER(CONCAT('%', :keyword, '%'))
                  )
              AND (
                    :cursorDueDate IS NULL
                    OR wo.dueDate < :cursorDueDate
                    OR (wo.dueDate = :cursorDueDate AND wo.idx < :cursorId)
                  )
            ORDER BY wo.dueDate DESC, wo.idx DESC
            """)
    List<Long> findActiveIdsBefore(
            @Param("targetDate") LocalDate targetDate,
            @Param("tradeType") String tradeType,
            @Param("statusCode") String statusCode,
            @Param("keyword") String keyword,
            @Param("cursorDueDate") LocalDate cursorDueDate,
            @Param("cursorId") Long cursorId,
            Pageable pageable
    );

    @Query("""
            SELECT wo.idx
            FROM WorkOrder wo
            WHERE (wo.isDeleted = false OR wo.isDeleted IS NULL)
              AND wo.siteIdx IN :siteIds
              AND (:targetDate IS NULL OR wo.dueDate = :targetDate)
              AND (:tradeType IS NULL OR wo.tradeType = :tradeType)
              AND (:statusCode IS NULL OR wo.statusCode = :statusCode OR (:statusCode = 'OPEN' AND wo.statusCode IS NULL))
              AND (
                    :keyword IS NULL
                    OR LOWER(wo.title) LIKE LOWER(CONCAT('%', :keyword, '%'))
                    OR LOWER(wo.workDetail) LIKE LOWER(CONCAT('%', :keyword, '%'))
                    OR LOWER(wo.instructionContent) LIKE LOWER(CONCAT('%', :keyword, '%'))
                  )
              AND (
                    :cursorDueDate IS NULL
                    OR wo.dueDate < :cursorDueDate
                    OR (wo.dueDate = :cursorDueDate AND wo.idx < :cursorId)
                  )
            ORDER BY wo.dueDate DESC, wo.idx DESC
            """)
    List<Long> findActiveIdsBySiteIdxInBefore(
            @Param("siteIds") List<Long> siteIds,
            @Param("targetDate") LocalDate targetDate,
            @Param("tradeType") String tradeType,
            @Param("statusCode") String statusCode,
            @Param("keyword") String keyword,
            @Param("cursorDueDate") LocalDate cursorDueDate,
            @Param("cursorId") Long cursorId,
            Pageable pageable
    );

    @Query("""
            SELECT DISTINCT wo
            FROM WorkOrder wo
            LEFT JOIN FETCH wo.equipments
            WHERE wo.idx IN :ids
            """)
    List<WorkOrder> findByIdxInWithEquipments(@Param("ids") List<Long> ids);

    // [WORKORDER_006] 6단계 : 주간계획 연동 초안 장비 불러오기 기능 쿼리
    // feat : WorkPlan 도메인 수정 없이 DB에서 장비 정보 조회
    @Query(value = "SELECT e.type, e.count FROM work_plan_equipment e WHERE e.work_plan_idx = :planIdx", nativeQuery = true)
    List<Object[]> findEquipmentsByPlanIdx(@Param("planIdx") Long planIdx);

    // [WORKORDER_008] 중장비 입출차/기상관제/ESG 연동용 장비 조회 쿼리
    // feat : 작업지시서 장비와 연결된 작업계획의 작업구역/상세내역을 함께 조회
    @Query(value = """
            SELECT  we.idx,
                    wo.idx AS work_order_idx,
                    wo.title,
                    wo.trade_type,
                    COALESCE(wo.work_detail, wo.instruction_content, wp.note, wp.name, '') AS work_detail,
                    COALESCE(wo.due_date, wp.start_date) AS work_date,
                    wp.location AS work_location,
                    wp.name AS work_plan_name,
                    we.gate_idx,
                    we.equipment_name,
                    we.equipment_count,
                    wo.status_code,
                    wo.site_idx
            FROM work_order wo
            LEFT JOIN work_order_equipment we
                   ON we.work_order_idx = wo.idx
                  AND (we.is_deleted = false OR we.is_deleted IS NULL)
            LEFT JOIN work_plan wp ON wp.idx = wo.work_plan_id
            WHERE (wo.is_deleted = false OR wo.is_deleted IS NULL)
              AND (:projectId IS NULL OR wo.site_idx = :projectId)
              AND (wo.status_code IS NULL OR UPPER(wo.status_code) NOT IN ('REJECTED', 'CANCELLED', 'CANCELED', 'DELETED'))
              AND (:includeNoEquipment = true OR we.idx IS NOT NULL)
              AND (
                    :targetDate IS NULL
                    OR wo.due_date = :targetDate
                    OR (:targetDate BETWEEN wp.start_date AND COALESCE(wp.end_date, wp.start_date))
                  )
            ORDER BY COALESCE(wo.due_date, wp.start_date) DESC,
                     wo.idx DESC,
                     we.gate_idx ASC,
                     we.idx ASC
            """, nativeQuery = true)
    List<Object[]> findGateEquipmentsByTargetDate(
            @Param("targetDate") LocalDate targetDate,
            @Param("projectId") Long projectId,
            @Param("includeNoEquipment") boolean includeNoEquipment
    );

    default List<Object[]> findGateEquipmentsByTargetDate(LocalDate targetDate) {
        return findGateEquipmentsByTargetDate(targetDate, null, false);
    }
}
