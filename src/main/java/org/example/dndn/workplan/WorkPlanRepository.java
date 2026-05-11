package org.example.dndn.workplan;

import org.example.dndn.workplan.model.enums.PlanStatus;
import org.example.dndn.workplan.model.enums.PlanType;
import org.example.dndn.workplan.model.entity.WorkPlan;
import org.example.dndn.workplan.model.enums.WorkTrade;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Collection;
import java.util.List;

public interface WorkPlanRepository extends JpaRepository<WorkPlan, Long> {

    List<WorkPlan> findAllByPlanType(PlanType planType);

    @EntityGraph(attributePaths = {"parentWorkPlan", "tradeProcess", "workers", "extension"})
    @Query("select distinct wp from WorkPlan wp where wp.planType = :planType")
    List<WorkPlan> findAllByPlanTypeWithStaffingGraph(@Param("planType") PlanType planType);

    List<WorkPlan> findAllByPlanTypeAndTrade(PlanType planType, WorkTrade trade);

    List<WorkPlan> findAllByPlanTypeAndStatus(PlanType planType, PlanStatus status);

    List<WorkPlan> findAllByPlanTypeAndTradeAndStatus(PlanType planType, WorkTrade trade, PlanStatus status);

    // AnalysisService — 현장 기준 WorkPlan 조회
    // WorkPlan → tradeProcess → masterSchedule → project 경로
    List<WorkPlan> findAllByTradeProcess_MasterSchedule_Project_Idx(Long projectId);

    // AnalysisService — 특정 TradeProcess에 연결된 WorkPlan 조회
    List<WorkPlan> findAllByTradeProcess_Idx(Long tradeProcessId);

    List<WorkPlan> findAllByParentWorkPlan_Idx(Long parentWorkPlanId);

    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("update WorkPlan wp set wp.tradeProcess = null where wp.tradeProcess.idx in :tradeProcessIds")
    int clearTradeProcessByIds(@Param("tradeProcessIds") Collection<Long> tradeProcessIds);
}
