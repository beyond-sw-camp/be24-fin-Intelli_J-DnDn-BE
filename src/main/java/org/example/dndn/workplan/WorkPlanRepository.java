package org.example.dndn.workplan;

import org.example.dndn.workplan.model.enums.PlanStatus;
import org.example.dndn.workplan.model.enums.PlanType;
import org.example.dndn.workplan.model.entity.WorkPlan;
import org.example.dndn.workplan.model.enums.WorkTrade;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface WorkPlanRepository extends JpaRepository<WorkPlan, Long> {

    List<WorkPlan> findAllByPlanType(PlanType planType);

    List<WorkPlan> findAllByPlanTypeAndTrade(PlanType planType, WorkTrade trade);

    List<WorkPlan> findAllByPlanTypeAndStatus(PlanType planType, PlanStatus status);

    List<WorkPlan> findAllByPlanTypeAndTradeAndStatus(PlanType planType, WorkTrade trade, PlanStatus status);

    // AnalysisService — 현장 기준 WorkPlan 조회
    // WorkPlan → tradeProcess → masterSchedule → project 경로
    List<WorkPlan> findAllByTradeProcess_MasterSchedule_Project_Idx(Long projectId);

    // AnalysisService — 특정 TradeProcess에 연결된 WorkPlan 조회
    List<WorkPlan> findAllByTradeProcess_Idx(Long tradeProcessId);
}