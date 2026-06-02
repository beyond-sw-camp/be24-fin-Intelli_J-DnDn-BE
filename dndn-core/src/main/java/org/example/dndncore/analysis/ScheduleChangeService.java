package org.example.dndncore.analysis;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import org.example.dndncore.auth.security.AuthAccessService;
import org.example.dndncore.analysis.model.ScheduleChange;
import org.example.dndncore.analysis.model.ScheduleChangeDto;
import org.example.dndncore.analysis.model.ScheduleChangeStatus;
import org.example.dndncore.project.repository.ProjectRepository;
import org.example.dndncore.project.repository.TradeProcessRepository;
import org.example.dndncore.project.model.entity.Project;
import org.example.dndncore.project.model.entity.TradeProcess;
import org.example.dndncore.workplan.WorkPlanRepository;
import org.example.dndncore.workplan.model.entity.WorkPlan;
import org.example.dndncore.workplan.model.entity.WorkPlanExtension;
import org.example.dndncore.workplan.model.entity.WorkPlanWorker;
import org.example.dndncore.workplan.model.enums.WorkerTrade;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class ScheduleChangeService {

    private static final String APPLIED_DETAIL_MARKER = "\u005b\uc2b9\uc778 \ubcc0\uacbd \ubc18\uc601\u005d";
    private static final int LEGACY_WORK_PLAN_NOTE_LIMIT = 240;

    private final ObjectMapper objectMapper;
    private final ScheduleChangeRepository changeRepository;
    private final ProjectRepository projectRepository;
    private final TradeProcessRepository tradeProcessRepository;
    private final WorkPlanRepository workPlanRepository;
    private final AuthAccessService authAccessService;

    // ── 요청 등록 (공정 책임자) ────────────────────────────────────────────

    @Transactional
    public Long create(ScheduleChangeDto.Req dto) {
        Project project = projectRepository.findById(dto.getProjectId())
                .orElseThrow(() -> new RuntimeException("현장을 찾을 수 없습니다."));

        authAccessService.assertProjectWriteAccess(project.getIdx());

        TradeProcess tradeProcess = null;
        if (dto.getTradeProcessId() != null) {
            tradeProcess = tradeProcessRepository.findById(dto.getTradeProcessId())
                    .orElseThrow(() -> new RuntimeException("공정을 찾을 수 없습니다."));
        }

        if (tradeProcess != null) {
            authAccessService.assertTradeProcessAccess(tradeProcess);
        }

        WorkPlan workPlan = null;
        if (dto.getWorkPlanId() != null) {
            workPlan = workPlanRepository.findById(dto.getWorkPlanId())
                    .orElseThrow(() -> new RuntimeException("작업 계획을 찾을 수 없습니다."));
            if (tradeProcess == null) {
                tradeProcess = workPlan.getTradeProcess();
            }
        }

        if (workPlan != null) {
            authAccessService.assertWorkPlanWriteAccess(workPlan);
        }

        String resolvedProcess = resolveProcess(dto, tradeProcess, workPlan);
        authAccessService.assertTradeAccess(resolvedProcess);
        Optional<ScheduleChange> duplicate = findDuplicatePendingRequest(
                project.getIdx(), dto, resolvedProcess, tradeProcess, workPlan);
        if (duplicate.isPresent()) {
            return duplicate.get().getIdx();
        }

        String attachmentUrls = (dto.getAttachmentUrls() == null || dto.getAttachmentUrls().isEmpty())
                ? null
                : String.join(",", dto.getAttachmentUrls());

        ScheduleChange request = ScheduleChange.builder()
                .project(project)
                .tradeProcess(tradeProcess)
                .workPlan(workPlan)
                .taskName(dto.getTaskName())
                .requester(dto.getRequester())
                .process(resolvedProcess)
                .oldStart(dto.getOldStart())
                .oldEnd(dto.getOldEnd())
                .newStart(dto.getNewStart())
                .newEnd(dto.getNewEnd())
                .reason(dto.getReason())
                .cause(dto.getCause())
                .changeSummaryJson(toJson(dto.getChangeSummary()))
                .detailChangesJson(toJson(dto.getDetailChanges()))
                .aiApplied(dto.getAiApplied() != null ? dto.getAiApplied() : false)
                .attachmentUrls(attachmentUrls)
                .build();

        return changeRepository.save(request).getIdx();
    }

    // ── 목록 조회 ──────────────────────────────────────────────────────────

    /**
     * 변경 요청 목록 — 총 책임자(전체) / 공정 책임자(본인 공종)
     *
     * @param projectId  현장 ID
     * @param process    공종 필터 (null이면 전체)
     * @param requester  본인 식별자 (null이면 전체 — 총 책임자 뷰)
     */
    public List<ScheduleChangeDto.Res> listRequests(
            Long projectId, String process, String requester) {
        authAccessService.assertProjectAccess(projectId);
        String effectiveProcess = authAccessService.effectiveTrade(process);

        List<ScheduleChange> requests;
        List<ScheduleChangeStatus> activeStatuses = List.of(
                ScheduleChangeStatus.PENDING,
                ScheduleChangeStatus.APPROVED);

        if (requester != null && !requester.isBlank() && effectiveProcess != null && !effectiveProcess.isBlank()) {
            // 공정 책임자: 본인 요청만
            requests = changeRepository
                    .findAllByProject_IdxAndProcessAndRequesterAndStatusInOrderByCreatedAtDesc(
                            projectId, effectiveProcess, requester, activeStatuses);
        } else if (effectiveProcess != null && !effectiveProcess.isBlank()) {
            // 총 책임자 + 공종 필터
            requests = changeRepository
                    .findAllByProject_IdxAndProcessAndStatusInOrderByCreatedAtDesc(
                            projectId, effectiveProcess, activeStatuses);
        } else {
            // 총 책임자 + 전체
            requests = changeRepository
                    .findAllByProject_IdxAndStatusInOrderByCreatedAtDesc(
                            projectId, activeStatuses);
        }

        return deduplicateRequests(requests).stream()
                .filter(this::canAccessChange)
                .filter(request -> requester == null || requester.isBlank() || requester.equals(request.getRequester()))
                .map(ScheduleChangeDto.Res::from)
                .toList();
    }

    /**
     * 변경 이력 — 처리 완료(APPROVED, APPLIED, REJECTED)된 항목
     */
    public List<ScheduleChangeDto.Res> listHistory(Long projectId, String process) {
        authAccessService.assertProjectAccess(projectId);
        String effectiveProcess = authAccessService.effectiveTrade(process);
        List<ScheduleChangeStatus> doneStatuses = List.of(
                ScheduleChangeStatus.APPROVED,
                ScheduleChangeStatus.APPLIED,
                ScheduleChangeStatus.REJECTED);

        List<ScheduleChange> history = (effectiveProcess != null && !effectiveProcess.isBlank())
                ? changeRepository
                .findAllByProject_IdxAndProcessAndStatusInOrderByProcessedAtDesc(
                        projectId, effectiveProcess, doneStatuses)
                : changeRepository
                .findAllByProject_IdxAndStatusInOrderByProcessedAtDesc(
                        projectId, doneStatuses);

        return history.stream()
                .filter(this::canAccessChange)
                .map(ScheduleChangeDto.Res::from)
                .toList();
    }

    // ── 승인 (총 책임자) ───────────────────────────────────────────────────

    @Transactional
    public void approve(Long requestId, ScheduleChangeDto.ApproveReq dto) {
        ScheduleChange request = findRequest(requestId);
        assertChangeAccess(request);
        request.approve(dto.getApprover());
    }

    // ── 반려 (총 책임자) ───────────────────────────────────────────────────

    @Transactional
    public void reject(Long requestId, ScheduleChangeDto.RejectReq dto) {
        ScheduleChange request = findRequest(requestId);
        assertChangeAccess(request);
        request.reject(dto.getApprover(), dto.getRejectReason());
    }

    // ── 공정표 반영 (총 책임자) ────────────────────────────────────────────

    /**
     * 승인된 요청을 실제 공정 일정에 반영한다.
     *
     * 반영 대상:
     * 반영 대상
     *  1. WorkPlan 변경 요청 — 해당 WorkPlan의 변경 정보 동기화
     *  2. TradeProcess 기준 요청 — 최초 업로드 공정표는 유지하고 연결된 WorkPlan 변경 정보만 동기화
     */
    @Transactional
    public void applyToSchedule(Long requestId) {
        ScheduleChange request = findRequest(requestId);
        assertChangeAccess(request);
        if (request.getStatus() == ScheduleChangeStatus.APPLIED) {
            throw new IllegalStateException("이미 공정표에 반영된 요청입니다.");
        }

        if (request.getStatus() != ScheduleChangeStatus.APPROVED) {
            throw new IllegalStateException("승인 완료 상태에서만 공정표에 반영할 수 있습니다.");
        }

        // 1. 월간/주간 WorkPlan 기반 요청이면 해당 계획의 연장 정보만 동기화
        if (request.getWorkPlan() != null) {
            syncWorkPlanExtension(request.getWorkPlan(), request);
        }
        // 2. 공정표 공정 자체 변경 요청이면 해당 공정과 연결된 WorkPlan 동기화
        else if (request.getTradeProcess() != null) {
            TradeProcess tradeProcess = request.getTradeProcess();

            workPlanRepository.findAllByTradeProcess_Idx(tradeProcess.getIdx()).stream()
                    .filter(authAccessService::canAccessWorkPlan)
                    .forEach(wp -> syncWorkPlanExtension(wp, request));
        }

        applyDetailChangesToWorkPlans(request);

        // 3. 요청 상태를 APPLIED로 변경
        request.markApplied();
    }

    // ── 내부 헬퍼 ─────────────────────────────────────────────────────────

    private void assertChangeAccess(ScheduleChange request) {
        if (request != null && request.getProject() != null) {
            authAccessService.assertProjectActive(request.getProject().getIdx());
        }
        if (!canAccessChange(request)) {
            throw new org.springframework.web.server.ResponseStatusException(
                    org.springframework.http.HttpStatus.FORBIDDEN,
                    "No permission for this site or trade.");
        }
    }

    private boolean canAccessChange(ScheduleChange request) {
        if (request == null) return false;
        Long projectId = request.getProject() != null ? request.getProject().getIdx() : null;
        if (!authAccessService.canAccessProjectId(projectId)) return false;
        if (request.getTradeProcess() != null && !authAccessService.canAccessTradeProcess(request.getTradeProcess())) {
            return false;
        }
        if (request.getWorkPlan() != null && !authAccessService.canAccessWorkPlan(request.getWorkPlan())) {
            return false;
        }
        return authAccessService.canAccessTradeName(request.getProcess());
    }

    private Optional<ScheduleChange> findDuplicatePendingRequest(
            Long projectId,
            ScheduleChangeDto.Req dto,
            String resolvedProcess,
            TradeProcess tradeProcess,
            WorkPlan workPlan) {

        return changeRepository
                .findAllByProject_IdxAndStatusInOrderByCreatedAtDesc(
                        projectId, List.of(ScheduleChangeStatus.PENDING))
                .stream()
                .filter(request -> sameRequest(request, dto, resolvedProcess, tradeProcess, workPlan))
                .findFirst();
    }

    private boolean sameRequest(
            ScheduleChange request,
            ScheduleChangeDto.Req dto,
            String resolvedProcess,
            TradeProcess tradeProcess,
            WorkPlan workPlan) {

        Long requestWorkPlanId = request.getWorkPlan() != null ? request.getWorkPlan().getIdx() : null;
        Long dtoWorkPlanId = workPlan != null ? workPlan.getIdx() : null;
        if (!Objects.equals(requestWorkPlanId, dtoWorkPlanId)) {
            return false;
        }

        Long requestTradeProcessId = request.getTradeProcess() != null ? request.getTradeProcess().getIdx() : null;
        Long dtoTradeProcessId = tradeProcess != null ? tradeProcess.getIdx() : null;
        if (!Objects.equals(requestTradeProcessId, dtoTradeProcessId)) {
            return false;
        }

        return sameText(request.getRequester(), dto.getRequester())
                && sameText(request.getProcess(), resolvedProcess)
                && sameText(request.getTaskName(), dto.getTaskName())
                && Objects.equals(request.getOldStart(), dto.getOldStart())
                && Objects.equals(request.getOldEnd(), dto.getOldEnd())
                && Objects.equals(request.getNewStart(), dto.getNewStart())
                && Objects.equals(request.getNewEnd(), dto.getNewEnd());
    }

    private List<ScheduleChange> deduplicateRequests(List<ScheduleChange> requests) {
        Map<String, ScheduleChange> unique = new LinkedHashMap<>();
        for (ScheduleChange request : requests) {
            unique.putIfAbsent(deduplicateKey(request), request);
        }
        return new ArrayList<>(unique.values());
    }

    private String deduplicateKey(ScheduleChange request) {
        return String.join("|",
                text(request.getProject() != null ? request.getProject().getIdx() : null),
                text(request.getWorkPlan() != null ? request.getWorkPlan().getIdx() : null),
                text(request.getTradeProcess() != null ? request.getTradeProcess().getIdx() : null),
                normalized(request.getRequester()),
                normalized(request.getProcess()),
                normalized(request.getTaskName()),
                text(request.getOldStart()),
                text(request.getOldEnd()),
                text(request.getNewStart()),
                text(request.getNewEnd()));
    }

    private boolean sameText(String left, String right) {
        return normalized(left).equals(normalized(right));
    }

    private String normalized(String value) {
        return value == null ? "" : value.trim();
    }

    private String text(Object value) {
        return value == null ? "" : String.valueOf(value);
    }

    private ScheduleChange findRequest(Long requestId) {
        return changeRepository.findById(requestId)
                .orElseThrow(() -> new RuntimeException("일정 변경 요청을 찾을 수 없습니다."));
    }

    private String resolveProcess(ScheduleChangeDto.Req dto, TradeProcess tradeProcess, WorkPlan workPlan) {
        if (dto.getProcess() != null && !dto.getProcess().isBlank()) {
            return dto.getProcess();
        }
        if (tradeProcess != null && tradeProcess.getTradeName() != null) {
            return tradeProcess.getTradeName();
        }
        if (workPlan != null && workPlan.getTrade() != null) {
            return workPlan.getTrade().name();
        }
        return "기타";
    }

    private String toJson(Object value) {
        if (value == null) return null;

        try {
            return objectMapper.writeValueAsString(value);
        } catch (Exception e) {
            throw new IllegalArgumentException("변경 검토 데이터를 JSON으로 변환할 수 없습니다.", e);
        }
    }

    private void applyDetailChangesToWorkPlans(ScheduleChange request) {
        List<Map<String, Object>> detailChanges = readDetailChanges(request.getDetailChangesJson());
        if (detailChanges.isEmpty()) return;

        for (Map<String, Object> detail : detailChanges) {
            Long workPlanId = toLong(detail.get("workPlanId"));
            if (workPlanId == null) continue;

            WorkPlan workPlan = workPlanRepository.findById(workPlanId)
                    .orElseThrow(() -> new RuntimeException("변경 대상 작업 계획을 찾을 수 없습니다. id=" + workPlanId));

            authAccessService.assertWorkPlanWriteAccess(workPlan);
            applyDetailChangeToWorkPlan(workPlan, detail);
        }
    }

    private List<Map<String, Object>> readDetailChanges(String json) {
        if (json == null || json.isBlank()) return List.of();

        try {
            return objectMapper.readValue(json, new TypeReference<>() {});
        } catch (Exception e) {
            throw new IllegalArgumentException("세부 일정 변경 데이터를 읽을 수 없습니다.", e);
        }
    }

    private void applyDetailChangeToWorkPlan(WorkPlan workPlan, Map<String, Object> detail) {
        String recommendedName = nonBlank(detail.get("recommendedName"), workPlan.getName());
        String partner = nonBlank(detail.get("partner"), workPlan.getPartner());
        String manager = nonBlank(detail.get("manager"), workPlan.getManager());
        String contact = nonBlank(detail.get("contact"), workPlan.getContact());
        String note = buildAppliedDetailNote(workPlan.getNote(), detail);

        workPlan.updateInfo(
                recommendedName,
                workPlan.getTrade(),
                workPlan.getLocation(),
                workPlan.getStartDate(),
                workPlan.getEndDate(),
                workPlan.getStatus(),
                partner,
                manager,
                contact,
                note
        );

        Integer recommendedRequiredCount = toInteger(detail.get("recommendedRequiredCount"));
        if (recommendedRequiredCount != null) {
            applyRecommendedWorkerCount(workPlan, recommendedRequiredCount);
        }
    }

    private void applyRecommendedWorkerCount(WorkPlan workPlan, int recommendedRequiredCount) {
        int targetCount = Math.max(0, recommendedRequiredCount);
        List<WorkPlanWorker> existingWorkers = workPlan.getWorkers() != null
                ? workPlan.getWorkers()
                : List.of();

        if (targetCount == 0) {
            workPlan.replaceWorkers(List.of());
            return;
        }

        if (existingWorkers.isEmpty()) {
            workPlan.replaceWorkers(List.of(WorkPlanWorker.builder()
                    .trade(WorkerTrade.COMMON)
                    .count(targetCount)
                    .build()));
            return;
        }

        List<WorkerCount> workerCounts = existingWorkers.stream()
                .filter(worker -> worker.getTrade() != null)
                .map(worker -> new WorkerCount(
                        worker.getTrade(),
                        Math.max(0, worker.getCount() != null ? worker.getCount() : 0)))
                .toList();

        if (workerCounts.isEmpty()) {
            workPlan.replaceWorkers(List.of(WorkPlanWorker.builder()
                    .trade(WorkerTrade.COMMON)
                    .count(targetCount)
                    .build()));
            return;
        }

        int currentTotal = workerCounts.stream().mapToInt(WorkerCount::count).sum();
        if (currentTotal <= 0) {
            workPlan.replaceWorkers(List.of(WorkPlanWorker.builder()
                    .trade(workerCounts.get(0).trade())
                    .count(targetCount)
                    .build()));
            return;
        }

        List<WorkerCount> adjusted = new ArrayList<>(workerCounts);
        int delta = targetCount - currentTotal;
        if (delta >= 0) {
            WorkerCount first = adjusted.get(0);
            adjusted.set(0, new WorkerCount(first.trade(), first.count() + delta));
        } else {
            int remainingReduction = -delta;
            for (int i = adjusted.size() - 1; i >= 0 && remainingReduction > 0; i--) {
                WorkerCount current = adjusted.get(i);
                int reduction = Math.min(current.count(), remainingReduction);
                adjusted.set(i, new WorkerCount(current.trade(), current.count() - reduction));
                remainingReduction -= reduction;
            }
        }

        List<WorkPlanWorker> newWorkers = adjusted.stream()
                .filter(worker -> worker.count() > 0)
                .map(worker -> WorkPlanWorker.builder()
                        .trade(worker.trade())
                        .count(worker.count())
                        .build())
                .toList();

        if (newWorkers.isEmpty()) {
            newWorkers = List.of(WorkPlanWorker.builder()
                    .trade(adjusted.get(0).trade())
                    .count(targetCount)
                    .build());
        }

        workPlan.replaceWorkers(newWorkers);
    }

    private String buildAppliedDetailNote(String originalNote, Map<String, Object> detail) {
        List<String> lines = new ArrayList<>();
        String baseNote = removeAppliedDetailNote(nonBlank(detail.get("recommendedNote"), originalNote));

        String originalWorkTime = nonBlank(detail.get("originalWorkTime"), "");
        String recommendedWorkTime = nonBlank(detail.get("recommendedWorkTime"), "");
        if (!recommendedWorkTime.isBlank()) {
            if (!originalWorkTime.isBlank() && !originalWorkTime.equals(recommendedWorkTime)) {
                lines.add("\uc791\uc5c5\uc2dc\uac04: " + originalWorkTime + " -> " + recommendedWorkTime);
            } else {
                lines.add("\uc791\uc5c5\uc2dc\uac04: " + recommendedWorkTime);
            }
        }

        Double originalManHours = toDouble(detail.get("originalManHours"));
        Double recommendedManHours = toDouble(detail.get("recommendedManHours"));
        if (recommendedManHours != null) {
            String manHourLine = originalManHours != null
                    ? "\uacf5\uc218: " + formatNumber(originalManHours) + " -> " + formatNumber(recommendedManHours) + "\uc778\uc2dc"
                    : "\uacf5\uc218: " + formatNumber(recommendedManHours) + "\uc778\uc2dc";
            lines.add(manHourLine);
        }

        if (lines.isEmpty()) return limitLegacyNote(baseNote);

        String appliedBlock = APPLIED_DETAIL_MARKER + "\n" + String.join("\n", lines);
        if (baseNote == null || baseNote.isBlank()) {
            return limitLegacyNote(appliedBlock);
        }
        return joinLegacyNote(baseNote, appliedBlock);
    }

    private String removeAppliedDetailNote(String note) {
        if (note == null || note.isBlank()) return note;

        int markerIndex = note.indexOf(APPLIED_DETAIL_MARKER);
        if (markerIndex < 0) return note;

        return note.substring(0, markerIndex).stripTrailing();
    }

    private String joinLegacyNote(String baseNote, String appliedBlock) {
        String normalizedBase = baseNote == null ? "" : baseNote.stripTrailing();
        if (normalizedBase.isBlank()) return limitLegacyNote(appliedBlock);

        int separatorLength = 2;
        int availableBaseLength = LEGACY_WORK_PLAN_NOTE_LIMIT - appliedBlock.length() - separatorLength;
        if (availableBaseLength <= 20) {
            return limitLegacyNote(appliedBlock);
        }

        return limitLegacyNote(truncate(normalizedBase, availableBaseLength) + "\n\n" + appliedBlock);
    }

    private String limitLegacyNote(String note) {
        return truncate(note, LEGACY_WORK_PLAN_NOTE_LIMIT);
    }

    private String truncate(String text, int maxLength) {
        if (text == null) return null;
        if (text.length() <= maxLength) return text;
        if (maxLength <= 1) return text.substring(0, maxLength);
        return text.substring(0, maxLength - 1).stripTrailing() + "\u2026";
    }

    private String nonBlank(Object value, String fallback) {
        if (value == null) return fallback;
        String text = String.valueOf(value).trim();
        return text.isBlank() ? fallback : text;
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

    private Double toDouble(Object value) {
        if (value == null) return null;
        if (value instanceof Number number) return number.doubleValue();
        try {
            String text = String.valueOf(value).trim();
            return text.isBlank() ? null : Double.parseDouble(text);
        } catch (NumberFormatException e) {
            return null;
        }
    }

    private String formatNumber(Double value) {
        if (value == null) return "";
        if (Math.floor(value) == value) {
            return String.valueOf(value.longValue());
        }
        return String.valueOf(Math.round(value * 10.0) / 10.0);
    }

    /**
     * WorkPlan의 Extension을 변경 요청의 새 종료일 기준으로 동기화.
     * 기존 Extension이 없으면 새로 생성.
     */
    private void syncWorkPlanExtension(WorkPlan workPlan, ScheduleChange request) {
        WorkPlanExtension extension = workPlan.getExtension();
        if (extension == null) {
            extension = WorkPlanExtension.builder().build();
            workPlan.attachExtension(extension);
        }

        int addedDays = 0;
        if (workPlan.getEndDate() != null && request.getNewEnd() != null) {
            addedDays = (int) java.time.temporal.ChronoUnit.DAYS
                    .between(workPlan.getEndDate(), request.getNewEnd());
        }

        extension.update(
                request.getNewEnd(),
                addedDays,
                request.getReason(),
                LocalDate.now()
        );
    }

    private record WorkerCount(WorkerTrade trade, int count) {}
}
