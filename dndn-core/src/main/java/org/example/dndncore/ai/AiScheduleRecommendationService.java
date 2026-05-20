package org.example.dndncore.ai;

import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import org.example.dndncore.ai.model.AiScheduleRecommendation;
import org.example.dndncore.ai.model.AiScheduleRecommendationDto;
import org.example.dndncore.analysis.AnalysisService;
import org.example.dndncore.analysis.model.AnalysisDto;
import org.example.dndncore.project.model.entity.Project;
import org.example.dndncore.project.repository.ProjectRepository;
import org.example.dndncore.workplan.WorkPlanRepository;
import org.example.dndncore.workplan.model.entity.WorkPlan;
import org.example.dndncore.workplan.model.enums.PlanType;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class AiScheduleRecommendationService {

    private static final Pattern WORK_TIME_PATTERN =
            Pattern.compile("\\d{1,2}:\\d{2}\\s*(?:~|\\uFF5E)\\s*\\d{1,2}:\\d{2}");

    private final AiScheduleRecommendationRepository recommendationRepository;
    private final ProjectRepository projectRepository;
    private final WorkPlanRepository workPlanRepository;
    private final AnalysisService analysisService;
    private final ObjectMapper objectMapper;
    private final OpenAiScheduleRecommendationClient openAiClient;

    @Transactional
    public AiScheduleRecommendationDto.Res create(AiScheduleRecommendationDto.CreateReq req) {
        Project project = projectRepository.findById(req.getProjectId())
                .orElseThrow(() -> new RuntimeException("Project not found. id=" + req.getProjectId()));
        WorkPlan monthlyWorkPlan = workPlanRepository.findById(req.getMonthlyWorkPlanId())
                .orElseThrow(() -> new RuntimeException("Monthly work plan not found. id=" + req.getMonthlyWorkPlanId()));

        if (monthlyWorkPlan.getPlanType() != PlanType.MONTHLY) {
            throw new IllegalArgumentException("AI schedule recommendation requires a monthly work plan.");
        }

        Map<String, Object> context = buildContext(project, monthlyWorkPlan);
        AiScheduleRecommendation recommendation = recommendationRepository.save(
                AiScheduleRecommendation.pending(project, monthlyWorkPlan, toJson(context))
        );

        try {
            Map<String, Object> result = openAiClient.generate(context);
            validateResult(recommendation, result);
            recommendation.markSuccess(toJson(result));
        } catch (Exception e) {
            recommendation.markFailed(e.getMessage());
        }

        return AiScheduleRecommendationDto.Res.from(recommendation);
    }

    public AiScheduleRecommendationDto.Res get(Long id) {
        return AiScheduleRecommendationDto.Res.from(findRecommendation(id));
    }

    public List<AiScheduleRecommendationDto.Res> list(Long projectId) {
        return recommendationRepository.findAllByProject_IdxOrderByCreatedAtDesc(projectId)
                .stream()
                .map(AiScheduleRecommendationDto.Res::from)
                .toList();
    }

    @Transactional
    public AiScheduleRecommendationDto.Res complete(
            Long id,
            AiScheduleRecommendationDto.CompleteReq req
    ) {
        AiScheduleRecommendation recommendation = findRecommendation(id);
        Map<String, Object> result = normalizeResult(req);
        validateResult(recommendation, result);
        recommendation.markSuccess(toJson(result));
        return AiScheduleRecommendationDto.Res.from(recommendation);
    }

    @Transactional
    public AiScheduleRecommendationDto.Res fail(Long id, AiScheduleRecommendationDto.FailReq req) {
        AiScheduleRecommendation recommendation = findRecommendation(id);
        String message = req.getErrorMessage() == null || req.getErrorMessage().isBlank()
                ? "AI recommendation failed."
                : req.getErrorMessage();
        recommendation.markFailed(message);
        return AiScheduleRecommendationDto.Res.from(recommendation);
    }

    private Map<String, Object> buildContext(Project project, WorkPlan monthlyWorkPlan) {
        List<WorkPlan> childPlans = workPlanRepository.findAllByParentWorkPlan_Idx(monthlyWorkPlan.getIdx());
        List<Map<String, Object>> recoveryCandidates = childPlans.stream()
                .filter(this::isActionableRecoveryCandidate)
                .map(this::toRecoveryCandidateContext)
                .toList();

        Map<String, Object> context = new LinkedHashMap<>();
        context.put("project", toProjectContext(project));
        context.put("monthlyWorkPlan", toWorkPlanContext(monthlyWorkPlan));
        context.put("delayRisk", findDelayRisk(project.getIdx(), monthlyWorkPlan.getIdx()));
        context.put("childWorkPlans", childPlans.stream().map(this::toWorkPlanContext).toList());
        context.put("recoveryCandidates", recoveryCandidates);
        context.put("guardrails", List.of(
                "Return JSON only.",
                "Use only workPlanId values from recoveryCandidates.",
                "If recoveryCandidates is empty, return an empty detailChanges array.",
                "Do not modify completed, unrelated, zero-worker, zero-man-hour, or date-missing work plans.",
                "Recommend the nearest actionable candidates first and return at most 2 detailChanges.",
                "Do not create new work names. Preserve the original name and append only a short concrete suffix when needed.",
                "Keep recommended work time inside HH:mm ~ HH:mm format.",
                "Prefer extending work time before increasing workers. Do not increase workers by more than 30% or 2 people.",
                "Do not copy previous [승인 변경 반영] audit text into recommendedNote.",
                "recommendedNote must be concrete field work guidance based on the existing note.",
                "The result is a recommendation only; schedule changes require human approval."
        ));
        return context;
    }

    private Map<String, Object> toProjectContext(Project project) {
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("projectId", project.getIdx());
        data.put("name", project.getName());
        data.put("location", project.getLocation());
        data.put("startDate", project.getStartDate());
        data.put("endDate", project.getEndDate());
        return data;
    }

    private Map<String, Object> toWorkPlanContext(WorkPlan workPlan) {
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("workPlanId", workPlan.getIdx());
        data.put("parentWorkPlanId", workPlan.getParentWorkPlan() != null
                ? workPlan.getParentWorkPlan().getIdx()
                : null);
        data.put("name", workPlan.getName());
        data.put("trade", workPlan.getTrade() != null ? workPlan.getTrade().name() : null);
        data.put("location", workPlan.getLocation());
        data.put("planType", workPlan.getPlanType() != null ? workPlan.getPlanType().name() : null);
        data.put("status", workPlan.getStatus() != null ? workPlan.getStatus().name() : null);
        data.put("startDate", workPlan.getStartDate());
        data.put("endDate", workPlan.getEndDate());
        data.put("effectiveEndDate", workPlan.effectiveEndDate());
        data.put("requiredCount", workPlan.getRequiredCount());
        data.put("workersDisplay", workPlan.workersDisplay());
        data.put("equipmentDisplay", workPlan.equipmentDisplay());
        data.put("note", workPlan.getNote());
        data.put("originalWorkTime", extractWorkTime(workPlan));
        data.put("actionableRecoveryCandidate", isActionableRecoveryCandidate(workPlan));
        return data;
    }

    private Map<String, Object> toRecoveryCandidateContext(WorkPlan workPlan) {
        Map<String, Object> data = toWorkPlanContext(workPlan);
        data.put("originalName", workPlan.getName());
        data.put("originalRequiredCount", workPlan.getRequiredCount());
        data.put("originalNote", sanitizeNote(workPlan.getNote()));
        return data;
    }

    private boolean isActionableRecoveryCandidate(WorkPlan workPlan) {
        return workPlan != null
                && workPlan.getIdx() != null
                && workPlan.getStartDate() != null
                && workPlan.getEndDate() != null
                && workPlan.getRequiredCount() != null
                && workPlan.getRequiredCount() > 0;
    }

    private String extractWorkTime(WorkPlan workPlan) {
        String note = workPlan == null ? "" : String.valueOf(workPlan.getNote() == null ? "" : workPlan.getNote());
        java.util.regex.Matcher matcher = WORK_TIME_PATTERN.matcher(note);
        if (matcher.find()) {
            return matcher.group().replaceAll("\\s*(?:~|\\uFF5E)\\s*", " ~ ");
        }
        return "07:00 ~ 17:00";
    }

    private String sanitizeNote(String note) {
        if (note == null || note.isBlank()) return "";
        int markerIndex = note.indexOf("[승인 변경 반영]");
        return markerIndex >= 0 ? note.substring(0, markerIndex).trim() : note.trim();
    }

    private Map<String, Object> findDelayRisk(Long projectId, Long monthlyWorkPlanId) {
        return analysisService.getDelayRiskTasks(projectId, null)
                .stream()
                .filter(item -> monthlyWorkPlanId.equals(item.getWorkPlanId()))
                .findFirst()
                .map(this::toDelayRiskContext)
                .orElseGet(LinkedHashMap::new);
    }

    private Map<String, Object> toDelayRiskContext(AnalysisDto.DelayRiskDetailRes item) {
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("workPlanId", item.getWorkPlanId());
        data.put("tradeProcessId", item.getTradeProcessId());
        data.put("process", item.getProcess());
        data.put("tradeName", item.getTradeName());
        data.put("name", item.getName());
        data.put("location", item.getLocation());
        data.put("plannedPct", item.getPlannedPct());
        data.put("actualPct", item.getActualPct());
        data.put("diff", item.getDiff());
        data.put("status", item.getStatus());
        data.put("risk", item.getRisk());
        data.put("expectedDelayDays", item.getExpectedDelayDays());
        data.put("actualSource", item.getActualSource());
        data.put("latestReportDate", item.getLatestReportDate());
        data.put("analysisDate", item.getAnalysisDate());
        data.put("cause", item.getCause());
        return data;
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> normalizeResult(AiScheduleRecommendationDto.CompleteReq req) {
        if (req.getResult() != null && !req.getResult().isEmpty()) {
            return req.getResult();
        }

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("changeSummary", req.getChangeSummary() != null
                ? req.getChangeSummary()
                : new LinkedHashMap<>());
        result.put("detailChanges", req.getDetailChanges() != null
                ? req.getDetailChanges()
                : new ArrayList<>());
        return result;
    }

    @SuppressWarnings("unchecked")
    private void validateResult(AiScheduleRecommendation recommendation, Map<String, Object> result) {
        Object rawDetailChanges = result.get("detailChanges");
        if (!(rawDetailChanges instanceof List<?> detailChanges)) {
            Object nestedSummary = result.get("recommendation");
            if (nestedSummary instanceof Map<?, ?> nested) {
                rawDetailChanges = nested.get("detailChanges");
            }
        }

        if (!(rawDetailChanges instanceof List<?> detailChanges)) {
            throw new IllegalArgumentException("AI result must include detailChanges array.");
        }

        for (Object item : detailChanges) {
            if (!(item instanceof Map<?, ?> detail)) {
                throw new IllegalArgumentException("Each detailChange must be an object.");
            }
            validateDetailChange(recommendation, (Map<String, Object>) detail);
        }
    }

    private void validateDetailChange(
            AiScheduleRecommendation recommendation,
            Map<String, Object> detail
    ) {
        Long workPlanId = toLong(detail.get("workPlanId"));
        if (workPlanId == null) {
            throw new IllegalArgumentException("detailChange.workPlanId is required.");
        }

        WorkPlan workPlan = workPlanRepository.findById(workPlanId)
                .orElseThrow(() -> new IllegalArgumentException("Unknown workPlanId: " + workPlanId));

        Long parentId = workPlan.getParentWorkPlan() != null ? workPlan.getParentWorkPlan().getIdx() : null;
        if (!recommendation.getMonthlyWorkPlan().getIdx().equals(parentId)
                && !recommendation.getMonthlyWorkPlan().getIdx().equals(workPlan.getIdx())) {
            throw new IllegalArgumentException("workPlanId is outside the requested monthly work plan: " + workPlanId);
        }

        if (!isActionableRecoveryCandidate(workPlan)) {
            throw new IllegalArgumentException("workPlanId is not an actionable recovery candidate: " + workPlanId);
        }

        Integer recommendedCount = toInteger(detail.get("recommendedRequiredCount"));
        if (recommendedCount != null && (recommendedCount < 0 || recommendedCount > 100)) {
            throw new IllegalArgumentException("recommendedRequiredCount must be between 0 and 100.");
        }
        if (recommendedCount != null) {
            int currentCount = Math.max(0, workPlan.getRequiredCount() == null ? 0 : workPlan.getRequiredCount());
            int maxIncrease = Math.max(2, (int) Math.ceil(currentCount * 0.3));
            if (recommendedCount < currentCount || recommendedCount > currentCount + maxIncrease) {
                throw new IllegalArgumentException(
                        "recommendedRequiredCount must stay within the current count and a realistic recovery increase.");
            }
        }

        String recommendedWorkTime = asText(detail.get("recommendedWorkTime"));
        if (recommendedWorkTime != null
                && !recommendedWorkTime.isBlank()
                && !WORK_TIME_PATTERN.matcher(recommendedWorkTime).matches()) {
            throw new IllegalArgumentException("recommendedWorkTime must use HH:mm ~ HH:mm format.");
        }
    }

    private AiScheduleRecommendation findRecommendation(Long id) {
        return recommendationRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("AI schedule recommendation not found. id=" + id));
    }

    private String toJson(Object value) {
        try {
            return objectMapper.writeValueAsString(value);
        } catch (Exception e) {
            throw new IllegalArgumentException("Failed to serialize AI recommendation data.", e);
        }
    }

    private Long toLong(Object value) {
        if (value == null) return null;
        if (value instanceof Number number) return number.longValue();
        try {
            String text = String.valueOf(value).trim();
            return text.isBlank() ? null : Long.parseLong(text);
        } catch (NumberFormatException e) {
            return null;
        }
    }

    private Integer toInteger(Object value) {
        if (value == null) return null;
        if (value instanceof Number number) return number.intValue();
        try {
            String text = String.valueOf(value).trim();
            return text.isBlank() ? null : Integer.parseInt(text);
        } catch (NumberFormatException e) {
            return null;
        }
    }

    private String asText(Object value) {
        return value == null ? null : String.valueOf(value).trim();
    }
}
