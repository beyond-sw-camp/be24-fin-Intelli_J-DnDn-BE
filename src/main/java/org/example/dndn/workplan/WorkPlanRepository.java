package org.example.dndn.workplan;

import org.example.dndn.workplan.model.PlanStatus;
import org.example.dndn.workplan.model.PlanType;
import org.example.dndn.workplan.model.WorkPlan;
import org.example.dndn.workplan.model.WorkTrade;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface WorkPlanRepository extends JpaRepository<WorkPlan, Long> {

    List<WorkPlan> findAllByPlanType(PlanType planType);

    List<WorkPlan> findAllByPlanTypeAndTrade(PlanType planType, WorkTrade trade);

    List<WorkPlan> findAllByPlanTypeAndStatus(PlanType planType, PlanStatus status);

    List<WorkPlan> findAllByPlanTypeAndTradeAndStatus(PlanType planType, WorkTrade trade, PlanStatus status);
}