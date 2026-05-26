package org.example.dndncore.analysis;

import lombok.RequiredArgsConstructor;
import org.example.dndncore.auth.security.AuthAccessService;
import org.example.dndncore.analysis.model.AnalysisDto;
import org.example.dndncore.project.model.entity.TradeProcess;
import org.example.dndncore.project.repository.TradeProcessRepository;
import org.example.dndncore.report.DailyReportRepository;
import org.example.dndncore.report.model.DailyReport;
import org.example.dndncore.workplan.WorkPlanRepository;
import org.example.dndncore.workplan.model.entity.WorkPlan;
import org.example.dndncore.workplan.model.enums.PlanType;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZoneId;
import java.time.temporal.ChronoUnit;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class AnalysisService {

    private static final String ACTUAL_SOURCE_DAILY_REPORT = "DAILY_REPORT";
    private static final String ACTUAL_SOURCE_NONE = "NONE";
    private static final ZoneId ANALYSIS_ZONE = ZoneId.of("Asia/Seoul");
    private static final LocalTime DEFAULT_WORK_END_TIME = LocalTime.of(18, 0);
    private static final Pattern WORK_TIME_RANGE_PATTERN = Pattern.compile(
            "(\\d{1,2}:\\d{2})\\s*(?:~|\\uFF5E)\\s*(\\d{1,2}:\\d{2})"
    );
    private static final Comparator<DailyReport> LATEST_REPORT_FIRST = Comparator
            .comparing(DailyReport::getReportDate, Comparator.nullsLast(Comparator.reverseOrder()))
            .thenComparing(DailyReport::getIdx, Comparator.nullsLast(Comparator.reverseOrder()));

    private final TradeProcessRepository tradeProcessRepository;
    private final WorkPlanRepository workPlanRepository;
    private final DailyReportRepository dailyReportRepository;
    private final AuthAccessService authAccessService;

    // ?????????????????????????????????????????????
    // 1. 怨듭젙 吏꾩쿃瑜?鍮꾧탳
    // TradeProcess 湲곗?
    // ?? 湲곗큹 肄섑겕由ы듃 ????꾩껜 吏꾩쿃瑜?
    // ?????????????????????????????????????????????

    public List<AnalysisDto.ProcessProgressRes> getProgressList(Long projectId) {
        authAccessService.assertProjectAccess(projectId);
        LocalDate today = LocalDate.now(ANALYSIS_ZONE);
        AnalysisContext context = buildAnalysisContext(projectId, today);

        return tradeProcessRepository
                .findAllByMasterSchedule_Project_Idx(projectId)
                .stream()
                .filter(authAccessService::canAccessTradeProcess)
                .filter(tp -> !isMilestoneProcess(tp))
                .map(tp -> buildProgressRes(tp, today, context))
                .toList();
    }

    private boolean isMilestoneProcess(TradeProcess tradeProcess) {
        if (tradeProcess == null) return false;
        if (Boolean.TRUE.equals(tradeProcess.getIsMilestone())) return true;
        return "마일스톤".equals(String.valueOf(tradeProcess.getTradeName()).trim());
    }

    private AnalysisDto.ProcessProgressRes buildProgressRes(TradeProcess tp, LocalDate today, AnalysisContext context) {
        ActualProgressSnapshot actualProgress = calcActualProgressByTradeProcess(tp.getIdx(), today, context);
        LocalDate referenceDate = resolveReferenceDateForTradeProcess(tp.getIdx(), actualProgress, today, context);
        double plannedPct = calcPlannedPct(tp.getPlannedStart(), tp.getPlannedEnd(), referenceDate);
        double actualPct = actualProgress.actualPct();
        double diff = roundPct(plannedPct - actualPct);

        String status = classifyStatus(
                diff,
                tp.getPlannedStart(),
                tp.getPlannedEnd(),
                referenceDate,
                actualPct
        );

        String risk = classifyRisk(
                diff,
                tp.getPlannedStart(),
                tp.getPlannedEnd(),
                referenceDate,
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
                .forecastEnd(calcForecastEnd(tp.getPlannedStart(), tp.getPlannedEnd(), actualPct, referenceDate))
                .plannedPct(plannedPct)
                .actualPct(actualPct)
                .actualSource(actualProgress.source())
                .latestReportDate(actualProgress.reportDate())
                .analysisDate(referenceDate)
                .diff(diff)
                .status(status)
                .risk(risk)
                .actualWorkers(calcActualWorkersByTradeProcess(tp.getIdx(), referenceDate, context))
                .build();
    }

    private AnalysisContext buildAnalysisContext(Long projectId, LocalDate today) {
        List<WorkPlan> allPlans = workPlanRepository.findAllForAnalysis(projectId);
        List<WorkPlan> monthlyPlans = allPlans.stream()
                .filter(this::isMonthlyPlan)
                .toList();

        Map<Long, List<WorkPlan>> monthlyPlansByTradeProcessId = monthlyPlans.stream()
                .filter(plan -> tradeProcessId(plan) != null)
                .collect(Collectors.groupingBy(this::tradeProcessId));

        Map<Long, List<WorkPlan>> childrenByParentId = allPlans.stream()
                .filter(plan -> plan.getParentWorkPlan() != null)
                .filter(plan -> plan.getParentWorkPlan().getIdx() != null)
                .collect(Collectors.groupingBy(plan -> plan.getParentWorkPlan().getIdx()));

        Set<Long> monthlyPlanIds = monthlyPlans.stream()
                .map(WorkPlan::getIdx)
                .filter(Objects::nonNull)
                .collect(Collectors.toSet());

        Set<Long> workPlanIds = allPlans.stream()
                .map(WorkPlan::getIdx)
                .filter(Objects::nonNull)
                .collect(Collectors.toSet());

        List<DailyReport> monthlyReports = monthlyPlanIds.isEmpty()
                ? List.of()
                : dailyReportRepository
                .findAllByMonthlyPlanIdsUntilDate(
                        monthlyPlanIds,
                        today
                );

        List<DailyReport> workPlanReports = workPlanIds.isEmpty()
                ? List.of()
                : dailyReportRepository
                .findAllByWorkPlanIdsUntilDate(
                        workPlanIds,
                        today
                );

        return new AnalysisContext(
                monthlyPlans,
                monthlyPlansByTradeProcessId,
                childrenByParentId,
                groupReportsByMonthlyPlanId(monthlyReports),
                groupReportsByWorkPlanId(workPlanReports)
        );
    }

    private Map<Long, List<DailyReport>> groupReportsByMonthlyPlanId(List<DailyReport> reports) {
        Map<Long, List<DailyReport>> grouped = reports.stream()
                .filter(report -> report.getMonthlyWorkPlan() != null)
                .filter(report -> report.getMonthlyWorkPlan().getIdx() != null)
                .collect(Collectors.groupingBy(report -> report.getMonthlyWorkPlan().getIdx()));
        grouped.values().forEach(list -> list.sort(LATEST_REPORT_FIRST));
        return grouped;
    }

    private Map<Long, List<DailyReport>> groupReportsByWorkPlanId(List<DailyReport> reports) {
        Map<Long, List<DailyReport>> grouped = reports.stream()
                .filter(report -> report.getWorkPlan() != null)
                .filter(report -> report.getWorkPlan().getIdx() != null)
                .collect(Collectors.groupingBy(report -> report.getWorkPlan().getIdx()));
        grouped.values().forEach(list -> list.sort(LATEST_REPORT_FIRST));
        return grouped;
    }

    private DailyReport findLatestReport(Map<Long, List<DailyReport>> reportsByPlanId, Long planId, LocalDate date) {
        if (planId == null || date == null) return null;
        return reportsByPlanId.getOrDefault(planId, List.of())
                .stream()
                .filter(report -> report.getReportDate() != null)
                .filter(report -> !report.getReportDate().isAfter(date))
                .findFirst()
                .orElse(null);
    }

    private Long tradeProcessId(WorkPlan plan) {
        if (plan == null || plan.getTradeProcess() == null) return null;
        return plan.getTradeProcess().getIdx();
    }

    // ?????????????????????????????????????????????
    // 2. 吏???꾪뿕 ?몃? ?묒뾽
    // MONTHLY WorkPlan 湲곗?
    // ?? 101??湲곗큹 肄섑겕由ы듃 ???
    //
    // ?먯떇 WEEKLY WorkPlan
    // ?? 5/1 ?먭?, 5/2 ?λ퉬 諛섏엯, 5/3 1援ш컙 ???
    // ?????????????????????????????????????????????

    public List<AnalysisDto.DelayRiskDetailRes> getDelayRiskTasks(Long projectId, Long tradeProcessId) {
        authAccessService.assertProjectAccess(projectId);
        LocalDate today = LocalDate.now(ANALYSIS_ZONE);
        AnalysisContext context = buildAnalysisContext(projectId, today);

        return context.monthlyPlans()
                .stream()
                // 101??湲곗큹 肄섑겕由ы듃 ???媛숈? ?몃? ?묒뾽
                .filter(authAccessService::canAccessWorkPlan)
                .filter(this::isMonthlyPlan)

                // ?뱀젙 怨듭젙 ?좏깮 ???대떦 怨듭젙???몃? ?묒뾽留?議고쉶
                .filter(wp -> tradeProcessId == null
                        || (wp.getTradeProcess() != null
                        && wp.getTradeProcess().getIdx().equals(tradeProcessId)))

                .map(parentPlan -> buildDelayRiskTaskRes(parentPlan, today, context))
                .filter(task -> isDelayRiskTask(
                        task.getDate(),
                        task.getEffectiveEnd(),
                        task.getAnalysisDate() != null ? task.getAnalysisDate() : today,
                        task.getDiff(),
                        task.getActualPct()
                ))
                .toList();
    }

    private AnalysisDto.DelayRiskDetailRes buildDelayRiskTaskRes(WorkPlan parentPlan, LocalDate today, AnalysisContext context) {
        ActualProgressSnapshot actualProgress = getActualProgressByMonthlyPlan(parentPlan, today, context);
        LocalDate referenceDate = resolveReferenceDateForMonthlyPlan(parentPlan, actualProgress, today, context);
        double plannedPct = calcPlannedPct(
                parentPlan.getStartDate(),
                parentPlan.effectiveEndDate(),
                referenceDate
        );

        double actualPct = actualProgress.actualPct();
        double diff = roundPct(plannedPct - actualPct);

        String status = classifyStatus(
                diff,
                parentPlan.getStartDate(),
                parentPlan.effectiveEndDate(),
                referenceDate,
                actualPct
        );

        String risk = classifyRisk(
                diff,
                parentPlan.getStartDate(),
                parentPlan.effectiveEndDate(),
                referenceDate,
                actualPct
        );

        List<WorkPlan> childPlans = context.childrenByParentId().getOrDefault(parentPlan.getIdx(), List.of());

        int actualWorkers = childPlans.stream()
                .mapToInt(child -> getLatestActualWorkersByWorkPlan(child.getIdx(), referenceDate, context))
                .sum();

        String latestIssue = getLatestIssueFromChildPlans(parentPlan.getIdx(), referenceDate, context);
        String cause = actualProgress.hasDailyReport()
                ? latestIssue
                : "\uacf5\uc0ac\uc77c\ubcf4 \uae30\uc900 \uc6d4\uac04 \uc138\ubd80\uacc4\ud68d \uc9c4\ucc99\ub960 \ubbf8\uc791\uc131";

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
                .analysisDate(referenceDate)
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

    // ?????????????????????????????????????????????
    // 3. ?ㅼ젣 吏꾩쿃瑜?怨꾩궛
    // ?????????????????????????????????????????????

    private LocalDate resolveReferenceDateForTradeProcess(
            Long tradeProcessId,
            ActualProgressSnapshot actualProgress,
            LocalDate today,
            AnalysisContext context
    ) {
        if (actualProgress != null && actualProgress.reportDate() != null) {
            return actualProgress.reportDate();
        }

        LocalTime workEndTime = resolveTradeProcessWorkEndTime(tradeProcessId, today, context);
        if (LocalTime.now(ANALYSIS_ZONE).isBefore(workEndTime)) {
            return trustedReferenceDate(actualProgress, today);
        }

        return today;
    }

    private LocalDate resolveReferenceDateForMonthlyPlan(
            WorkPlan monthlyPlan,
            ActualProgressSnapshot actualProgress,
            LocalDate today,
            AnalysisContext context
    ) {
        if (actualProgress != null && actualProgress.reportDate() != null) {
            return actualProgress.reportDate();
        }

        LocalTime workEndTime = resolveMonthlyPlanWorkEndTime(monthlyPlan, today, context);
        if (LocalTime.now(ANALYSIS_ZONE).isBefore(workEndTime)) {
            return trustedReferenceDate(actualProgress, today);
        }

        return today;
    }

    private boolean hasDailyReportOn(ActualProgressSnapshot actualProgress, LocalDate date) {
        return actualProgress != null
                && actualProgress.hasDailyReport()
                && date != null
                && date.equals(actualProgress.reportDate());
    }

    private LocalDate trustedReferenceDate(ActualProgressSnapshot actualProgress, LocalDate today) {
        if (actualProgress != null && actualProgress.reportDate() != null) {
            return actualProgress.reportDate();
        }
        return today.minusDays(1);
    }

    private LocalTime resolveTradeProcessWorkEndTime(Long tradeProcessId, LocalDate date, AnalysisContext context) {
        if (tradeProcessId == null) return DEFAULT_WORK_END_TIME;

        return context.monthlyPlansByTradeProcessId()
                .getOrDefault(tradeProcessId, List.of())
                .stream()
                .map(monthlyPlan -> resolveMonthlyPlanWorkEndTime(monthlyPlan, date, context))
                .max(LocalTime::compareTo)
                .orElse(DEFAULT_WORK_END_TIME);
    }

    private LocalTime resolveMonthlyPlanWorkEndTime(WorkPlan monthlyPlan, LocalDate date, AnalysisContext context) {
        if (monthlyPlan == null || monthlyPlan.getIdx() == null) return DEFAULT_WORK_END_TIME;

        return context.childrenByParentId()
                .getOrDefault(monthlyPlan.getIdx(), List.of())
                .stream()
                .filter(child -> containsDate(child, date))
                .map(child -> parseWorkEndTime(child.getNote()))
                .filter(time -> time != null)
                .max(LocalTime::compareTo)
                .orElse(DEFAULT_WORK_END_TIME);
    }

    private boolean containsDate(WorkPlan workPlan, LocalDate date) {
        if (workPlan == null || date == null) return false;
        LocalDate start = workPlan.getStartDate();
        LocalDate end = workPlan.getEndDate();
        if (start == null || end == null) return false;
        return !date.isBefore(start) && !date.isAfter(end);
    }

    private LocalTime parseWorkEndTime(String note) {
        if (note == null || note.isBlank()) return null;

        Matcher matcher = WORK_TIME_RANGE_PATTERN.matcher(note);
        LocalTime latestEnd = null;
        while (matcher.find()) {
            LocalTime parsedEnd = parseTime(matcher.group(2));
            if (parsedEnd != null) {
                latestEnd = parsedEnd;
            }
        }
        return latestEnd;
    }

    private LocalTime parseTime(String value) {
        if (value == null || value.isBlank()) return null;
        try {
            return LocalTime.parse(value.trim());
        } catch (Exception e) {
            return null;
        }
    }

    private ActualProgressSnapshot calcActualProgressByTradeProcess(Long tradeProcessId, LocalDate today, AnalysisContext context) {
        List<WorkPlan> monthlyPlans = context.monthlyPlansByTradeProcessId()
                .getOrDefault(tradeProcessId, List.of());

        if (monthlyPlans.isEmpty()) return ActualProgressSnapshot.none();

        List<ActualProgressSnapshot> snapshots = monthlyPlans.stream()
                .map(wp -> getActualProgressByMonthlyPlan(wp, today, context))
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

    private ActualProgressSnapshot getActualProgressByMonthlyPlan(WorkPlan monthlyPlan, LocalDate today, AnalysisContext context) {
        if (monthlyPlan == null) return ActualProgressSnapshot.none();

        DailyReport report = findLatestReport(
                context.monthlyReportsByPlanId(),
                monthlyPlan.getIdx(),
                today
        );
        if (report != null) {
            return toActualProgressSnapshot(report);
        }

        DailyReport childReport = findLatestReportFromChildPlans(monthlyPlan.getIdx(), today, context);
        return childReport != null ? toActualProgressSnapshot(childReport) : ActualProgressSnapshot.none();
    }

    private DailyReport findLatestReportFromChildPlans(Long parentWorkPlanId, LocalDate today, AnalysisContext context) {
        if (parentWorkPlanId == null) return null;

        return context.childrenByParentId()
                .getOrDefault(parentWorkPlanId, List.of())
                .stream()
                .map(child -> findLatestReport(context.workReportsByPlanId(), child.getIdx(), today))
                .filter(Objects::nonNull)
                .sorted(LATEST_REPORT_FIRST)
                .findFirst()
                .orElse(null);
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

    // ?????????????????????????????????????????????
    // 4. ?ㅼ젣 ?ъ엯 ?몄썝 / ?댁뒋
    // ?????????????????????????????????????????????

    private int calcActualWorkersByTradeProcess(Long tradeProcessId, LocalDate today, AnalysisContext context) {
        return context.monthlyPlansByTradeProcessId()
                .getOrDefault(tradeProcessId, List.of())
                .stream()
                .flatMap(parent -> context.childrenByParentId().getOrDefault(parent.getIdx(), List.of()).stream())
                .mapToInt(child -> getLatestActualWorkersByWorkPlan(child.getIdx(), today, context))
                .sum();
    }

    private int getLatestActualWorkersByWorkPlan(Long workPlanId, LocalDate today, AnalysisContext context) {
        DailyReport report = findLatestReport(context.workReportsByPlanId(), workPlanId, today);
        return report != null && report.getActualWorkerCount() != null ? report.getActualWorkerCount() : 0;
    }

    private String getLatestIssueFromChildPlans(Long parentWorkPlanId, LocalDate today, AnalysisContext context) {
        List<WorkPlan> childPlans = context.childrenByParentId().getOrDefault(parentWorkPlanId, List.of());

        if (childPlans.isEmpty()) {
            return getLatestIssueByWorkPlan(parentWorkPlanId, today, context);
        }

        return childPlans.stream()
                .map(child -> getLatestIssueByWorkPlan(child.getIdx(), today, context))
                .filter(issue -> issue != null && !issue.isBlank())
                .findFirst()
                .orElse("");
    }

    private String getLatestIssueByWorkPlan(Long workPlanId, LocalDate today, AnalysisContext context) {
        DailyReport report = findLatestReport(context.workReportsByPlanId(), workPlanId, today);
        return report != null ? report.getIssue() : "";
    }

    // ?????????????????????????????????????????????
    // 5. 怨꾪쉷 吏꾩쿃瑜?/ ?덉긽 醫낅즺??
    // ?????????????????????????????????????????????

    private boolean isMonthlyPlan(WorkPlan workPlan) {
        return workPlan != null
                && workPlan.getPlanType() == PlanType.MONTHLY;
    }

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
        if ("\ub9e4\uc6b0 \ub192\uc74c".equals(risk) || "\ub192\uc74c".equals(risk)) {
            return "\ud6c4\uc18d \uacf5\uc815 \uc601\ud5a5 \uac80\ud1a0 \ud544\uc694";
        }
        return "\uc601\ud5a5 \ub0ae\uc74c";
    }

    // ?????????????????????????????????????????????
    // 6. ?곹깭 / ?꾪뿕???먮떒
    // ?????????????????????????????????????????????

    private String classifyStatus(double diff, LocalDate start, LocalDate end, LocalDate today, double actualPct) {
        if (actualPct >= 100) return "\uc644\ub8cc";

        if (isOverdueNotDone(end, today, actualPct)) return "\uc9c0\uc5f0";
        if (diff >= 15) return "\uc9c0\uc5f0";
        if (diff >= 10) return "\uc9c0\uc5f0 \uc704\ud5d8";
        if (isNearDeadlineAndLow(start, end, today, actualPct)) return "\uc9c0\uc5f0 \uc704\ud5d8";
        if (diff >= 5) return "\uc8fc\uc758";
        if (diff > 0) return "\uc8fc\uc758";

        return "\uc815\uc0c1";
    }

    private String classifyRisk(double diff, LocalDate start, LocalDate end, LocalDate today, double actualPct) {
        if (actualPct >= 100) return "\ub0ae\uc74c";

        if (isOverdueNotDone(end, today, actualPct)) return "\ub9e4\uc6b0 \ub192\uc74c";
        if (diff >= 20) return "\ub9e4\uc6b0 \ub192\uc74c";
        if (diff >= 15) return "\ub192\uc74c";
        if (diff >= 10) return "\ubcf4\ud1b5";
        if (isNearDeadlineAndLow(start, end, today, actualPct)) return "\ubcf4\ud1b5";
        if (diff > 0) return "\ubcf4\ud1b5";

        return "\ub0ae\uc74c";
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

        // ?꾩쭅 ?쒖옉 ?꾩씤 ?몃? ?묒뾽? 吏???꾪뿕?쇰줈 蹂댁? ?딆쓬
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

    private record AnalysisContext(
            List<WorkPlan> monthlyPlans,
            Map<Long, List<WorkPlan>> monthlyPlansByTradeProcessId,
            Map<Long, List<WorkPlan>> childrenByParentId,
            Map<Long, List<DailyReport>> monthlyReportsByPlanId,
            Map<Long, List<DailyReport>> workReportsByPlanId
    ) {
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
