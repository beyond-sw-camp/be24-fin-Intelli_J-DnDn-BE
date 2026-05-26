package org.example.dndncore.workplan;

import org.example.dndncore.workplan.model.enums.PlanStatus;
import org.example.dndncore.workplan.model.enums.PlanType;
import org.example.dndncore.workplan.model.entity.WorkPlan;
import org.example.dndncore.workplan.model.enums.WorkTrade;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDate;
import java.util.Collection;
import java.util.List;

public interface WorkPlanRepository extends JpaRepository<WorkPlan, Long> {

    List<WorkPlan> findAllByPlanType(PlanType planType);

    @EntityGraph(attributePaths = {"parentWorkPlan", "tradeProcess", "workers", "extension"})
    @Query("select distinct wp from WorkPlan wp where wp.planType = :planType")
    List<WorkPlan> findAllByPlanTypeWithStaffingGraph(@Param("planType") PlanType planType);

    @Query("SELECT DISTINCT wp FROM WorkPlan wp " +
            "LEFT JOIN FETCH wp.extension e " +
            "LEFT JOIN FETCH wp.parentWorkPlan " +
            "LEFT JOIN FETCH wp.tradeProcess " +
            "LEFT JOIN FETCH wp.workers " +
            "WHERE wp.planType = :planType " +
            "AND wp.startDate <= :date " +
            "AND (wp.endDate >= :date OR e.extendedEnd >= :date)")
    List<WorkPlan> findActiveByPlanTypeWithStaffingGraph(
            @Param("planType") PlanType planType,
            @Param("date") LocalDate date
    );

    List<WorkPlan> findAllByPlanTypeAndTrade(PlanType planType, WorkTrade trade);

    List<WorkPlan> findAllByPlanTypeAndStatus(PlanType planType, PlanStatus status);

    List<WorkPlan> findAllByPlanTypeAndTradeAndStatus(PlanType planType, WorkTrade trade, PlanStatus status);

    // AnalysisService — 현장 기준 WorkPlan 조회
    // WorkPlan → tradeProcess → masterSchedule → project 경로
    //    List<WorkPlan> findAllByTradeProcess_MasterSchedule_Project_Idx(Long projectId);

    // AnalysisService — 현장 기준 WorkPlan 조회
    @Query("SELECT DISTINCT wp FROM WorkPlan wp " +
            "LEFT JOIN FETCH wp.extension " +
            "LEFT JOIN FETCH wp.tradeProcess tp " +
            "WHERE tp.masterSchedule.project.idx = :projectId")
    List<WorkPlan> findAllByTradeProcess_MasterSchedule_Project_Idx(@Param("projectId") Long projectId);

    @Query("SELECT DISTINCT wp FROM WorkPlan wp " +
            "LEFT JOIN FETCH wp.extension " +
            "LEFT JOIN FETCH wp.tradeProcess tp " +
            "LEFT JOIN FETCH wp.parentWorkPlan " +
            "WHERE tp.masterSchedule.project.idx = :projectId")
    List<WorkPlan> findAllForAnalysis(@Param("projectId") Long projectId);

    // AnalysisService — 특정 TradeProcess에 연결된 WorkPlan 조회
    List<WorkPlan> findAllByTradeProcess_Idx(Long tradeProcessId);

    List<WorkPlan> findAllByParentWorkPlan_Idx(Long parentWorkPlanId);

    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("update WorkPlan wp set wp.tradeProcess = null where wp.tradeProcess.idx in :tradeProcessIds")
    int clearTradeProcessByIds(@Param("tradeProcessIds") Collection<Long> tradeProcessIds);

    // 무조건 프로젝트 전체를 가져오지 않고, 시작일(startDate)과 종료일(endDate) 조건을 쿼리에 추가합니다.
    // N+1 최적화 적용.
    @Query("SELECT DISTINCT wp FROM WorkPlan wp " +
            "LEFT JOIN FETCH wp.extension " +
            "LEFT JOIN FETCH wp.tradeProcess tp " +
            "LEFT JOIN tp.masterSchedule ms " +
            "WHERE ms.project.idx = :projectId " +
            "AND wp.planType = :planType " +
            "AND wp.startDate <= :endDate AND wp.endDate >= :startDate")
    List<WorkPlan> findAllOptimized(
            @Param("projectId") Long projectId,
            @Param("planType") PlanType planType,
            @Param("startDate") LocalDate startDate,
            @Param("endDate") LocalDate endDate
    );
}

