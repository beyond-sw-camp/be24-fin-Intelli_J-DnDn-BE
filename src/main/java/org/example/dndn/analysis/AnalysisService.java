package org.example.dndn.analysis;

import lombok.RequiredArgsConstructor;
import org.example.dndn.analysis.model.AnalysisDto;
import org.example.dndn.project.model.entity.TradeProcess;
import org.example.dndn.project.repository.TradeProcessRepository;
import org.example.dndn.report.DailyReportRepository;
import org.example.dndn.report.model.DailyReport;
import org.example.dndn.workplan.WorkPlanRepository;
import org.example.dndn.workplan.model.entity.WorkPlan;
import org.example.dndn.workplan.model.enums.PlanType;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.List;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class AnalysisService {

    private static final String ACTUAL_SOURCE_DAILY_REPORT = "DAILY_REPORT";
    private static final String ACTUAL_SOURCE_NONE = "NONE";

    private final TradeProcessRepository tradeProcessRepository;
    private final WorkPlanRepository workPlanRepository;
    private final DailyReportRepository dailyReportRepository;

    // ─────────────────────────────────────────────
    // 1. 공정 진척률 비교
    // TradeProcess 기준
    // 예: 기초 콘크리트 타설 전체 진척률
    // ─────────────────────────────────────────────

    public List<AnalysisDto.ProcessProgressRes> getProgressList(Long projectId) {
        LocalDate today = LocalDate.now();

        return tradeProcessRepository
                .findAllByMasterSchedule_Project_Idx(projectId)
                .stream()
                .map(tp -> buildProgressRes(tp, today))
                .toList();
    }

    private AnalysisDto.ProcessProgressRes buildProgressRes(TradeProcess tp, LocalDate today) {
        double plannedPct = calcPlannedPct(tp.getPlannedStart(), tp.getPlannedEnd(), today);
        ActualProgressSnapshot actualProgress = calcActualProgressByTradeProcess(tp.getIdx(), today);
        double actualPct = actualProgress.actualPct();
        double diff = roundPct(plannedPct - actualPct);

        String status = classifyStatus(
                diff,
                tp.getPlannedStart(),
                tp.getPlannedEnd(),
                today,
                actualPct
        );

        String risk = classifyRisk(
                diff,
                tp.getPlannedStart(),
                tp.getPlannedEnd(),
                today,
                actualPct
        );

        return AnalysisDto.ProcessProgressRes.builder()
                .tradeProcessId(tp.getIdx())
                .tradeName(tp.getTradeName())
                .name(tp.getProcessName())
                .partner(tp.getPartnerCompany())
                .plannedStart(tp.getPlannedStart())
                .plannedEnd(tp.getPlannedEnd())
                .actualStart(tp.getPlannedStart())
                .forecastEnd(calcForecastEnd(tp.getPlannedStart(), tp.getPlannedEnd(), actualPct, today))
                .plannedPct(plannedPct)
                .actualPct(actualPct)
                .actualSource(actualProgress.source())
                .latestReportDate(actualProgress.reportDate())
                .diff(diff)
                .status(status)
                .risk(risk)
                .actualWorkers(calcActualWorkersByTradeProcess(tp.getIdx(), today))
                .build();
    }

    // ─────────────────────────────────────────────
    // 2. 지연 위험 세부 작업
    // MONTHLY WorkPlan 기준
    // 예: 101동 기초 콘크리트 타설
    //
    // 자식 WEEKLY WorkPlan
    // 예: 5/1 점검, 5/2 장비 반입, 5/3 1구간 타설
    // ─────────────────────────────────────────────

    public List<AnalysisDto.DelayRiskDetailRes> getDelayRiskTasks(Long projectId, Long tradeProcessId) {
        LocalDate today = LocalDate.now();

        return workPlanRepository
                .findAllByTradeProcess_MasterSchedule_Project_Idx(projectId)
                .stream()
                // 101동 기초 콘크리트 타설 같은 세부 작업
                .filter(wp -> wp.getPlanType() == PlanType.MONTHLY)

                // 특정 공정 선택 시 해당 공정의 세부 작업만 조회
                .filter(wp -> tradeProcessId == null
                        || (wp.getTradeProcess() != null
                        && wp.getTradeProcess().getIdx().equals(tradeProcessId)))

                .map(parentPlan -> buildDelayRiskTaskRes(parentPlan, today))
                .filter(task -> isDelayRiskTask(
                        task.getDate(),
                        task.getEffectiveEnd(),
                        today,
                        task.getDiff(),
                        task.getActualPct()
                ))
                .toList();
    }

    private AnalysisDto.DelayRiskDetailRes buildDelayRiskTaskRes(WorkPlan parentPlan, LocalDate today) {
        double plannedPct = calcPlannedPct(
                parentPlan.getStartDate(),
                parentPlan.effectiveEndDate(),
                today
        );

        ActualProgressSnapshot actualProgress = getActualProgressByMonthlyPlan(parentPlan, today);
        double actualPct = actualProgress.actualPct();
        double diff = roundPct(plannedPct - actualPct);

        String status = classifyStatus(
                diff,
                parentPlan.getStartDate(),
                parentPlan.effectiveEndDate(),
                today,
                actualPct
        );

        String risk = classifyRisk(
                diff,
                parentPlan.getStartDate(),
                parentPlan.effectiveEndDate(),
                today,
                actualPct
        );

        List<WorkPlan> childPlans = workPlanRepository.findAllByParentWorkPlan_Idx(parentPlan.getIdx());

        int actualWorkers = childPlans.stream()
                .mapToInt(child -> getLatestActualWorkersByWorkPlan(child.getIdx(), today))
                .sum();

        String latestIssue = getLatestIssueFromChildPlans(parentPlan.getIdx(), today);
        String cause = actualProgress.hasDailyReport()
                ? latestIssue
                : "공사일보의 월간 세부계획 진척률 미작성";

        return AnalysisDto.DelayRiskDetailRes.builder()
                .workPlanId(parentPlan.getIdx())
                .tradeProcessId(parentPlan.getTradeProcess() != null
                        ? parentPlan.getTradeProcess().getIdx()
                        : null)
                .process(resolveProcessName(parentPlan))
                .tradeName(parentPlan.getTradeProcess() != null
                        ? parentPlan.getTradeProcess().getTradeName()
                        : parentPlan.getTrade() != null ? parentPlan.getTrade().name() : "")
                .name(parentPlan.getName())
                .location(parentPlan.getLocation())
                .partner(resolvePartner(parentPlan))
                .date(parentPlan.getStartDate())
                .plannedStart(parentPlan.getStartDate())
                .plannedEnd(parentPlan.effectiveEndDate())
                .originalEnd(parentPlan.getEndDate())
                .effectiveEnd(parentPlan.effectiveEndDate())
                .plannedPct(plannedPct)
                .actualPct(actualPct)
                .actualSource(actualProgress.source())
                .latestReportDate(actualProgress.reportDate())
                .dailyReportId(actualProgress.reportId())
                .diff(diff)
                .status(status)
                .risk(risk)
                .expectedDelayDays(calcExpectedDelayDays(
                        parentPlan.getStartDate(),
                        parentPlan.effectiveEndDate(),
                        plannedPct,
                        actualPct
                ))
                .cause(cause)
                .followEffect(resolveFollowEffect(parentPlan, risk))
                .isCritical(parentPlan.getTradeProcess() != null
                        && Boolean.TRUE.equals(parentPlan.getTradeProcess().getIsMilestone()))
                .workersDisplay(parentPlan.workersDisplay())
                .equipmentDisplay(parentPlan.equipmentDisplay())
                .actualWorkers(actualWorkers)
                .hasReport(actualProgress.hasDailyReport())
                .build();
    }

    // ─────────────────────────────────────────────
    // 3. 실제 진척률 계산
    // ─────────────────────────────────────────────

    private ActualProgressSnapshot calcActualProgressByTradeProcess(Long tradeProcessId, LocalDate today) {
        List<WorkPlan> plans = workPlanRepository.findAllByTradeProcess_Idx(tradeProcessId);

        if (plans.isEmpty()) return ActualProgressSnapshot.none();

        List<ActualProgressSnapshot> snapshots = plans.stream()
                .filter(wp -> wp.getPlanType() == PlanType.MONTHLY)
                .map(wp -> getActualProgressByMonthlyPlan(wp, today))
                .filter(ActualProgressSnapshot::hasDailyReport)
                .toList();

        if (snapshots.isEmpty()) return ActualProgressSnapshot.none();

        double avg = snapshots.stream()
                .mapToDouble(ActualProgressSnapshot::actualPct)
                .average()
                .orElse(0.0);

        LocalDate latestReportDate = snapshots.stream()
                .map(ActualProgressSnapshot::reportDate)
                .filter(date -> date != null)
                .max(LocalDate::compareTo)
                .orElse(null);

        return ActualProgressSnapshot.dailyReport(roundPct(avg), latestReportDate, null);
    }

    private ActualProgressSnapshot getActualProgressByMonthlyPlan(WorkPlan monthlyPlan, LocalDate today) {
        if (monthlyPlan == null) return ActualProgressSnapshot.none();

        return dailyReportRepository
                .findTopByMonthlyWorkPlan_IdxAndReportDateLessThanEqualOrderByReportDateDesc(
                        monthlyPlan.getIdx(),
                        today
                )
                .map(this::toActualProgressSnapshot)
                .orElseGet(ActualProgressSnapshot::none);
    }

    private ActualProgressSnapshot toActualProgressSnapshot(DailyReport report) {
        Double progress = report.getMonthlyProgressPct();
        if (progress == null) {
            progress = report.getActualProgress();
        }

        if (progress == null) {
            return ActualProgressSnapshot.none(report.getReportDate(), report.getIdx());
        }

        return ActualProgressSnapshot.dailyReport(
                toPercent(progress),
                report.getReportDate(),
                report.getIdx()
        );
    }

    private double toPercent(Double progress) {
        if (progress == null) return 0.0;
        double clamped = Math.max(0.0, Math.min(100.0, progress));
        return roundPct(clamped);
    }

    // ─────────────────────────────────────────────
    // 4. 실제 투입 인원 / 이슈
    // ─────────────────────────────────────────────

    private int calcActualWorkersByTradeProcess(Long tradeProcessId, LocalDate today) {
        return workPlanRepository.findAllByTradeProcess_Idx(tradeProcessId)
                .stream()
                .filter(wp -> wp.getPlanType() == PlanType.MONTHLY)
                .flatMap(parent -> workPlanRepository.findAllByParentWorkPlan_Idx(parent.getIdx()).stream())
                .mapToInt(child -> getLatestActualWorkersByWorkPlan(child.getIdx(), today))
                .sum();
    }

    private int getLatestActualWorkersByWorkPlan(Long workPlanId, LocalDate today) {
        return dailyReportRepository
                .findTopByWorkPlan_IdxAndReportDateLessThanEqualOrderByReportDateDesc(
                        workPlanId,
                        today
                )
                .map(r -> r.getActualWorkerCount() != null ? r.getActualWorkerCount() : 0)
                .orElse(0);
    }

    private String getLatestIssueFromChildPlans(Long parentWorkPlanId, LocalDate today) {
        List<WorkPlan> childPlans = workPlanRepository.findAllByParentWorkPlan_Idx(parentWorkPlanId);

        if (childPlans.isEmpty()) {
            return getLatestIssueByWorkPlan(parentWorkPlanId, today);
        }

        return childPlans.stream()
                .map(child -> getLatestIssueByWorkPlan(child.getIdx(), today))
                .filter(issue -> issue != null && !issue.isBlank())
                .findFirst()
                .orElse("");
    }

    private String getLatestIssueByWorkPlan(Long workPlanId, LocalDate today) {
        return dailyReportRepository
                .findTopByWorkPlan_IdxAndReportDateLessThanEqualOrderByReportDateDesc(
                        workPlanId,
                        today
                )
                .map(DailyReport::getIssue)
                .orElse("");
    }

    // ─────────────────────────────────────────────
    // 5. 계획 진척률 / 예상 종료일
    // ─────────────────────────────────────────────

    private double calcPlannedPct(LocalDate start, LocalDate end, LocalDate today) {
        if (start == null || end == null) return 0.0;

        if (today.isBefore(start)) return 0.0;
        if (today.isAfter(end)) return 100.0;

        long totalDays = ChronoUnit.DAYS.between(start, end) + 1;
        long elapsedDays = ChronoUnit.DAYS.between(start, today) + 1;

        if (totalDays <= 0) return 100.0;

        return roundPct(elapsedDays * 100.0 / totalDays);
    }

    private LocalDate calcForecastEnd(LocalDate start, LocalDate plannedEnd, double actualPct, LocalDate today) {
        if (start == null || plannedEnd == null) return plannedEnd;
        if (actualPct <= 0) return plannedEnd;
        if (actualPct >= 100) return today;

        long elapsedDays = ChronoUnit.DAYS.between(start, today) + 1;
        if (elapsedDays <= 0) return plannedEnd;

        double daysPerPct = elapsedDays / (double) actualPct;
        long remainingDays = Math.round(daysPerPct * (100 - actualPct));

        return today.plusDays(remainingDays);
    }

    private int calcExpectedDelayDays(LocalDate start, LocalDate end, double plannedPct, double actualPct) {
        double lack = Math.max(0.0, plannedPct - actualPct);
        if (lack <= 0) return 0;

        long totalDays = start != null && end != null
                ? ChronoUnit.DAYS.between(start, end) + 1
                : 0;
        if (totalDays <= 0) return 1;

        double dailyPct = 100.0 / totalDays;
        return Math.max(1, (int) Math.ceil(lack / dailyPct));
    }

    private double roundPct(double value) {
        return Math.round(value * 10.0) / 10.0;
    }

    private String resolveProcessName(WorkPlan workPlan) {
        if (workPlan == null) return "";
        if (workPlan.getTradeProcess() != null && workPlan.getTradeProcess().getTradeName() != null) {
            return workPlan.getTradeProcess().getTradeName();
        }
        return workPlan.getTrade() != null ? workPlan.getTrade().name() : "";
    }

    private String resolvePartner(WorkPlan workPlan) {
        if (workPlan == null) return "-";
        if (workPlan.getPartner() != null && !workPlan.getPartner().isBlank()) {
            return workPlan.getPartner();
        }
        if (workPlan.getTradeProcess() != null
                && workPlan.getTradeProcess().getPartnerCompany() != null
                && !workPlan.getTradeProcess().getPartnerCompany().isBlank()) {
            return workPlan.getTradeProcess().getPartnerCompany();
        }
        return "-";
    }

    private String resolveFollowEffect(WorkPlan workPlan, String risk) {
        if ("매우 높음".equals(risk) || "높음".equals(risk)) {
            return "후속 공정 영향 검토 필요";
        }
        return "영향 낮음";
    }

    // ─────────────────────────────────────────────
    // 6. 상태 / 위험도 판단
    // ─────────────────────────────────────────────

    private String classifyStatus(double diff, LocalDate start, LocalDate end, LocalDate today, double actualPct) {
        if (actualPct >= 100) return "완료";

        if (isOverdueNotDone(end, today, actualPct)) return "지연";
        if (diff >= 15) return "지연";
        if (diff >= 10) return "지연 위험";
        if (isNearDeadlineAndLow(start, end, today, actualPct)) return "지연 위험";
        if (diff >= 5) return "주의";
        if (diff > 0) return "주의";

        return "정상";
    }

    private String classifyRisk(double diff, LocalDate start, LocalDate end, LocalDate today, double actualPct) {
        if (actualPct >= 100) return "낮음";

        if (isOverdueNotDone(end, today, actualPct)) return "매우 높음";
        if (diff >= 20) return "매우 높음";
        if (diff >= 15) return "높음";
        if (diff >= 10) return "보통";
        if (isNearDeadlineAndLow(start, end, today, actualPct)) return "보통";
        if (diff > 0) return "보통";

        return "낮음";
    }

    private boolean isDelayRiskTask(
            LocalDate start,
            LocalDate end,
            LocalDate today,
            Double diff,
            Double actualPct
    ) {
        double safeDiff = diff != null ? diff : 0.0;
        double safeActualPct = actualPct != null ? actualPct : 0.0;

        if (start == null || end == null) return false;

        // 아직 시작 전인 세부 작업은 지연 위험으로 보지 않음
        if (today.isBefore(start)) return false;

        return safeDiff > 0
                || isNearDeadlineAndLow(start, end, today, safeActualPct)
                || isOverdueNotDone(end, today, safeActualPct);
    }

    private boolean isNearDeadlineAndLow(LocalDate start, LocalDate end, LocalDate today, double actualPct) {
        if (start == null || end == null) return false;
        if (today.isBefore(start)) return false;
        if (actualPct >= 100) return false;

        long daysLeft = ChronoUnit.DAYS.between(today, end);

        return daysLeft >= 0 && daysLeft <= 3 && actualPct < 70;
    }

    private boolean isOverdueNotDone(LocalDate end, LocalDate today, double actualPct) {
        return end != null && today.isAfter(end) && actualPct < 100;
    }

    private static class ActualProgressSnapshot {
        private final double actualPct;
        private final String source;
        private final LocalDate reportDate;
        private final Long reportId;

        private ActualProgressSnapshot(double actualPct, String source, LocalDate reportDate, Long reportId) {
            this.actualPct = actualPct;
            this.source = source;
            this.reportDate = reportDate;
            this.reportId = reportId;
        }

        private static ActualProgressSnapshot dailyReport(double actualPct, LocalDate reportDate, Long reportId) {
            return new ActualProgressSnapshot(actualPct, ACTUAL_SOURCE_DAILY_REPORT, reportDate, reportId);
        }

        private static ActualProgressSnapshot none() {
            return new ActualProgressSnapshot(0.0, ACTUAL_SOURCE_NONE, null, null);
        }

        private static ActualProgressSnapshot none(LocalDate reportDate, Long reportId) {
            return new ActualProgressSnapshot(0.0, ACTUAL_SOURCE_NONE, reportDate, reportId);
        }

        private double actualPct() {
            return actualPct;
        }

        private String source() {
            return source;
        }

        private LocalDate reportDate() {
            return reportDate;
        }

        private Long reportId() {
            return reportId;
        }

        private boolean hasDailyReport() {
            return ACTUAL_SOURCE_DAILY_REPORT.equals(source);
        }
    }
}
