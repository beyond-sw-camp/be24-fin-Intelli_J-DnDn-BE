package org.example.dndncore.workplan;

import org.example.dndncore.auth.security.AuthAccessService;
import org.example.dndncore.ai.dto.WorkPlanAiDto;
import org.example.dndncore.ai.extractor.WorkPlanDocumentExtractor;
import org.example.dndncore.document_management.DocumentManagementService;
import org.example.dndncore.document_management.model.DocumentManagementDto;
import org.example.dndncore.project.repository.TradeProcessRepository;
import org.example.dndncore.project.model.entity.TradeProcess;
import org.example.dndncore.project.model.enums.DocType;
import org.example.dndncore.report.DailyReportRepository;
import org.example.dndncore.report.model.DailyReport;
import org.example.dndncore.staffing.service.StaffingService;
import org.example.dndncore.workplan.model.*;
import org.example.dndncore.workplan.model.entity.WorkPlan;
import org.example.dndncore.workplan.model.entity.WorkPlanExtension;
import org.example.dndncore.workplan.model.enums.PlanStatus;
import org.example.dndncore.workplan.model.enums.PlanType;
import org.example.dndncore.workplan.model.enums.WorkTrade;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.math.BigDecimal;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.UUID;

@Service
@Transactional(readOnly = true)
public class WorkPlanService {

    private final WorkPlanRepository workPlanRepository;
    private final DailyReportRepository dailyReportRepository;
    private final AuthAccessService authAccessService;
    private final TradeProcessRepository tradeProcessRepository;
    private final StaffingService staffingService;
    private final WorkPlanDocumentExtractor workPlanDocumentExtractor;
    private final DocumentManagementService documentManagementService;

    public WorkPlanService(WorkPlanRepository workPlanRepository,
                           DailyReportRepository dailyReportRepository,
                           AuthAccessService authAccessService,
                           TradeProcessRepository tradeProcessRepository,
                           @Lazy StaffingService staffingService,
                           WorkPlanDocumentExtractor workPlanDocumentExtractor,
                           DocumentManagementService documentManagementService) {
        this.workPlanRepository = workPlanRepository;
        this.dailyReportRepository = dailyReportRepository;
        this.authAccessService = authAccessService;
        this.tradeProcessRepository = tradeProcessRepository;
        this.staffingService = staffingService;
        this.workPlanDocumentExtractor = workPlanDocumentExtractor;
        this.documentManagementService = documentManagementService;
    }

    // 작업 계획 등록
    @Transactional
    public Long create(WorkPlanDto.Req dto) {
        WorkPlan plan = savePlan(dto);
        triggerZoneSyncIfWeekly(plan);
        return plan.getIdx();
    }

    @Transactional
    public List<Long> createBulk(List<WorkPlanDto.Req> dtos) {
        if (dtos == null || dtos.isEmpty()) {
            throw new RuntimeException("등록할 작업 계획이 없습니다.");
        }

        List<WorkPlan> savedPlans = new ArrayList<>();
        for (WorkPlanDto.Req dto : dtos) {
            savedPlans.add(savePlan(dto));
        }

        Set<LocalDate> weeklySyncDates = new LinkedHashSet<>();
        for (WorkPlan plan : savedPlans) {
            if (plan.getPlanType() == PlanType.WEEKLY) {
                weeklySyncDates.add(plan.getStartDate() != null ? plan.getStartDate() : LocalDate.now());
            }
        }
        weeklySyncDates.forEach(this::triggerZoneSync);

        return savedPlans.stream().map(WorkPlan::getIdx).toList();
    }

    @Transactional
    public List<WorkPlanDto.UploadExtractRes> extractUpload(
            Long projectId,
            String planType,
            String trade,
            Integer year,
            Integer month,
            MultipartFile file
    ) {
        if (projectId == null) {
            throw new RuntimeException("현장 ID는 필수입니다.");
        }
        authAccessService.assertProjectWriteAccess(projectId);

        PlanType uploadPlanType = resolveUploadPlanType(planType);
        File storedFile = storeUploadFile(file);

        try {
            List<WorkPlanAiDto.Item> items = workPlanDocumentExtractor.extract(
                    storedFile,
                    uploadPlanType.getLabel(),
                    year,
                    month,
                    trade
            );
            List<TradeProcess> tradeProcesses =
                    tradeProcessRepository.findAllByMasterSchedule_Project_Idx(projectId);

            List<WorkPlanDto.UploadExtractRes> rows = items.stream()
                    .map(item -> toUploadExtractRes(item, tradeProcesses, uploadPlanType, trade))
                    .toList();
            saveUploadedPlanDocument(projectId, file);
            return rows;
        } finally {
            if (storedFile.exists() && !storedFile.delete()) {
                storedFile.deleteOnExit();
            }
        }
    }

    private WorkPlan savePlan(WorkPlanDto.Req dto) {
        WorkPlan plan = dto.toEntity();

        linkTradeProcessIfPresent(plan, dto.getTradeProcessId());
        linkParentWorkPlanIfPresent(plan, dto.getParentWorkPlanId());
        authAccessService.assertWorkPlanWriteAccess(plan);

        return workPlanRepository.save(plan);
    }

    private void triggerZoneSyncIfWeekly(WorkPlan plan) {
        if (plan.getPlanType() == PlanType.WEEKLY) {
            triggerZoneSync(plan.getStartDate());
        }
    }

    public List<WorkPlanDto.workPlanRes> listByProject(Long projectId) {
        return listByProject(projectId, false);
    }

    public List<WorkPlanDto.workPlanRes> listByProject(Long projectId, boolean includeAllTrades) {
        authAccessService.assertProjectAccess(projectId);

        // 여기서는 원래 쓰던 메서드를 호출합니다.
        return workPlanRepository.findAllByTradeProcess_MasterSchedule_Project_Idx(projectId)
                .stream()
                .filter(plan -> includeAllTrades || authAccessService.canAccessWorkPlan(plan))
                .map(this::toWorkPlanResSimple)
                .toList();
    }

    // 작업 계획 목록 조회 (계획 종류 + 공종/상태 필터)
    // 프론트엔드가 호출하는 이곳에 최적화를 적용했습니다.
    // 파라미터에 startDate, endDate가 추가되었습니다.
    public List<WorkPlanDto.workPlanRes> list(Long projectId, String planType, String trade, String status, LocalDate startDate, LocalDate endDate) {
        PlanType type = PlanType.fromLabel(planType);
        WorkTrade tradeEnum = WorkTrade.fromLabel(trade);
        PlanStatus statusEnum = (status == null || status.isBlank())
                ? null : PlanStatus.fromLabel(status);
        String effectiveTrade = authAccessService.effectiveTrade(trade);

        List<WorkPlan> plans;

        if (projectId != null) {
            authAccessService.assertProjectAccess(projectId);

            // 프론트엔드가 날짜를 안 보냈을 때만 방어하고, 보냈으면 그 날짜를 씁니다.
            LocalDate finalStartDate = (startDate != null) ? startDate : LocalDate.of(1900, 1, 1);
            LocalDate finalEndDate = (endDate != null) ? endDate : LocalDate.of(9999, 12, 31);

            // 파라미터로 받은 진짜 날짜를 DB에 넘겨줍니다.
            plans = workPlanRepository.findAllOptimized(projectId, type, finalStartDate, finalEndDate);

        } else {
            // ... (기존 else 로직 동일)
            if (tradeEnum != null && statusEnum != null) {
                plans = workPlanRepository.findAllByPlanTypeAndTradeAndStatus(type, tradeEnum, statusEnum);
            } else if (tradeEnum != null) {
                plans = workPlanRepository.findAllByPlanTypeAndTrade(type, tradeEnum);
            } else if (statusEnum != null) {
                plans = workPlanRepository.findAllByPlanTypeAndStatus(type, statusEnum);
            } else {
                plans = workPlanRepository.findAllByPlanType(type);
            }
        }

        return plans.stream()
                .filter(plan -> statusEnum == null || plan.getStatus() == statusEnum)
                .filter(plan -> authAccessService.tradeMatches(
                        authAccessService.workPlanTradeName(plan),
                        effectiveTrade))
                .filter(authAccessService::canAccessWorkPlan)
                .map(this::toWorkPlanResSimple)
                .toList();
    }

    // 작업 계획 단일 조회
    public WorkPlanDto.Res read(Long planId) {
        WorkPlan plan = findPlan(planId);
        authAccessService.assertWorkPlanAccess(plan);
        return WorkPlanDto.Res.from(plan);
    }


    // 작업 계획 정보 수정
    @Transactional
    public void update(Long planId, WorkPlanDto.Req dto) {
        WorkPlan plan = findPlan(planId);
        authAccessService.assertWorkPlanWriteAccess(plan);

        plan.updateInfo(
                dto.getName(),
                WorkTrade.fromLabel(dto.getTrade()),
                dto.getLocation(),
                dto.getStartDate(),
                dto.getEndDate(),
                PlanStatus.fromLabel(dto.getStatus()),
                dto.getPartner(),
                dto.getManager(),
                dto.getContact(),
                dto.getNote()
        );

        if (dto.getWorkers() != null) {
            plan.replaceWorkers(dto.getWorkers().stream()
                    .filter(w -> w != null && w.getTrade() != null && !w.getTrade().isBlank())
                    .map(WorkPlanDto.WorkerEntry::toEntity).toList());
        }

        if (dto.getEquipment() != null) {
            plan.replaceEquipment(dto.getEquipment().stream()
                    .filter(e -> e != null && e.getType() != null && !e.getType().isBlank())
                    .map(WorkPlanDto.EquipmentEntry::toEntity).toList());
        }

        linkTradeProcessIfPresent(plan, dto.getTradeProcessId());
        linkParentWorkPlanIfPresent(plan, dto.getParentWorkPlanId());
        authAccessService.assertWorkPlanWriteAccess(plan);
        triggerZoneSync(plan.getStartDate());
    }

    // 일정 연장 등록/수정
    @Transactional
    public void extend(Long planId, WorkPlanDto.ExtReq dto) {
        WorkPlan plan = findPlan(planId);
        authAccessService.assertWorkPlanWriteAccess(plan);
        WorkPlanExtension extension = plan.getExtension();

        if (extension == null) {
            extension = WorkPlanExtension.builder().build();
            plan.attachExtension(extension);
        }

        Integer addedDays = dto.getAddedDays();
        if (addedDays == null && plan.getEndDate() != null && dto.getExtendedEnd() != null) {
            addedDays = (int) ChronoUnit.DAYS.between(plan.getEndDate(), dto.getExtendedEnd());
        }

        extension.update(dto.getExtendedEnd(), addedDays, dto.getReason(), LocalDate.now());
        triggerZoneSync(plan.getStartDate());
    }

    // 주간 계획서 일괄 제출
    @Transactional
    public List<Long> submitWeekly(WorkPlanDto.WeeklySubmitReq dto) {
        if (dto.getItems() == null || dto.getItems().isEmpty()) {
            throw new RuntimeException("제출할 작업 항목이 없습니다.");
        }
        if (dto.getPartner() == null || dto.getPartner().isBlank()
                || dto.getManager() == null || dto.getManager().isBlank()) {
            throw new RuntimeException("협력사명과 담당자명은 필수입니다.");
        }

        List<Long> savedIds = new ArrayList<>();

        for (WorkPlanDto.WeeklyItemReq item : dto.getItems()) {
            validateWeeklyItem(item);

            WorkPlan plan = WorkPlan.builder()
                    .name(item.getProcessName())
                    .location(item.getZone())
                    .planType(PlanType.WEEKLY)
                    .status(PlanStatus.PLANNED)
                    .startDate(item.getDate())
                    .endDate(item.getDate())
                    .partner(dto.getPartner())
                    .manager(dto.getManager())
                    .contact(dto.getContact())
                    .note(item.getNote())
                    .build();

            plan.replaceWorkers(item.getWorkers().stream()
                    .filter(w -> w != null && w.getTrade() != null && !w.getTrade().isBlank())
                    .map(WorkPlanDto.WorkerEntry::toEntity).toList());

            plan.replaceEquipment(item.getEquipment().stream()
                    .filter(e -> e != null && e.getType() != null && !e.getType().isBlank())
                    .map(WorkPlanDto.EquipmentEntry::toEntity).toList());

            linkTradeProcessIfPresent(plan, dto.getTradeProcessId());
            linkParentWorkPlanIfPresent(plan, dto.getParentWorkPlanId());
            authAccessService.assertWorkPlanWriteAccess(plan);

            savedIds.add(workPlanRepository.save(plan).getIdx());
        }

        triggerZoneSync(dto.getItems().get(0).getDate());
        return savedIds;
    }

    // 작업 착수 처리
    @Transactional
    public void start(Long planId) {
        WorkPlan plan = findPlan(planId);
        authAccessService.assertWorkPlanWriteAccess(plan);
        plan.markStarted(LocalDate.now());
    }

    // 작업 계획 삭제
    @Transactional
    public void delete(Long planId) {
        WorkPlan plan = findPlan(planId);
        authAccessService.assertWorkPlanWriteAccess(plan);
        LocalDate planDate = plan.getStartDate();
        workPlanRepository.delete(plan);
        triggerZoneSync(planDate);
    }

    private WorkPlanDto.UploadExtractRes toUploadExtractRes(
            WorkPlanAiDto.Item item,
            List<TradeProcess> tradeProcesses,
            PlanType planType,
            String selectedTradeName
    ) {
        TradeProcess matched = matchTradeProcess(item, tradeProcesses, selectedTradeName);
        LocalDate startDate = item.getStartDate() != null
                ? item.getStartDate()
                : matched != null ? matched.getPlannedStart() : null;
        LocalDate endDate = item.getEndDate() != null
                ? item.getEndDate()
                : matched != null ? matched.getPlannedEnd() : null;
        String name = firstNonBlank(
                item.getName(),
                item.getTradeProcessName(),
                matched != null ? matched.getProcessName() : null
        );

        String issue = null;
        if (matched == null || name == null || startDate == null || endDate == null) {
            issue = "error";
        }

        return WorkPlanDto.UploadExtractRes.builder()
                .tradeProcessId(matched != null ? matched.getIdx() : null)
                .tradeProcessName(matched != null
                        ? matched.getProcessName()
                        : item.getTradeProcessName())
                .trade(resolveWorkTradeLabel(
                        item.getTradeName(),
                        matched != null ? matched.getTradeName() : null,
                        selectedTradeName
                ))
                .name(name)
                .location(item.getLocation())
                .planType(planType.getLabel())
                .startDate(startDate)
                .endDate(endDate)
                .note(item.getNote())
                .issue(issue)
                .build();
    }

    private TradeProcess matchTradeProcess(
            WorkPlanAiDto.Item item,
            List<TradeProcess> tradeProcesses,
            String selectedTradeName
    ) {
        if (tradeProcesses == null || tradeProcesses.isEmpty()) {
            return null;
        }

        TradeProcess bestMatch = tradeProcesses.stream()
                .max(Comparator.comparingInt(process ->
                        tradeProcessMatchScore(process, item, selectedTradeName)))
                .orElse(null);

        int bestScore = bestMatch != null
                ? tradeProcessMatchScore(bestMatch, item, selectedTradeName)
                : 0;
        if (bestScore > 0) {
            return bestMatch;
        }

        return tradeProcesses.stream()
                .filter(process -> matchScore(selectedTradeName, process.getTradeName(), 1, 1) > 0)
                .findFirst()
                .orElse(tradeProcesses.get(0));
    }

    private int tradeProcessMatchScore(
            TradeProcess process,
            WorkPlanAiDto.Item item,
            String selectedTradeName
    ) {
        int score = 0;
        score += matchScore(item.getTradeProcessName(), process.getProcessName(), 100, 70);
        score += matchScore(item.getName(), process.getProcessName(), 80, 50);
        score += matchScore(item.getTradeName(), process.getTradeName(), 40, 25);
        score += matchScore(selectedTradeName, process.getTradeName(), 20, 10);
        if (Boolean.TRUE.equals(process.getIsMilestone())) {
            score -= 5;
        }
        return Math.max(score, 0);
    }

    private int matchScore(String candidate, String target, int exactScore, int containsScore) {
        String normalizedCandidate = normalizeForMatch(candidate);
        String normalizedTarget = normalizeForMatch(target);

        if (normalizedCandidate.isBlank() || normalizedTarget.isBlank()) {
            return 0;
        }
        if (normalizedCandidate.equals(normalizedTarget)) {
            return exactScore;
        }
        if (normalizedCandidate.contains(normalizedTarget) || normalizedTarget.contains(normalizedCandidate)) {
            return containsScore;
        }
        return 0;
    }

    private String resolveWorkTradeLabel(String... candidates) {
        for (String candidate : candidates) {
            WorkTrade exact = WorkTrade.fromLabel(candidate);
            if (exact != null) {
                return exact.getLabel();
            }
        }

        for (String candidate : candidates) {
            String normalizedCandidate = normalizeForMatch(candidate);
            if (normalizedCandidate.isBlank()) {
                continue;
            }
            for (WorkTrade trade : WorkTrade.values()) {
                if (matchScore(normalizedCandidate, trade.getLabel(), 1, 1) > 0
                        || matchScore(normalizedCandidate, trade.getCategory(), 1, 1) > 0
                        || matchScore(normalizedCandidate, trade.name(), 1, 1) > 0) {
                    return trade.getLabel();
                }
            }
        }

        return WorkTrade.ETC.getLabel();
    }

    private String normalizeForMatch(String value) {
        if (value == null) {
            return "";
        }
        return value.toLowerCase(Locale.ROOT)
                .replaceAll("\\s+", "")
                .replaceAll("[^가-힣a-z0-9]", "");
    }

    private String firstNonBlank(String... values) {
        if (values == null) {
            return null;
        }
        for (String value : values) {
            if (value != null && !value.isBlank()) {
                return value;
            }
        }
        return null;
    }

    private PlanType resolveUploadPlanType(String planType) {
        if (planType == null || planType.isBlank()) {
            throw new RuntimeException("계획 유형은 필수입니다.");
        }

        for (PlanType type : List.of(PlanType.YEARLY, PlanType.MONTHLY)) {
            if (type.getLabel().equals(planType) || type.name().equalsIgnoreCase(planType)) {
                return type;
            }
        }

        throw new RuntimeException("연간 또는 월간 계획서만 업로드할 수 있습니다.");
    }

    private void saveUploadedPlanDocument(Long projectId, MultipartFile file) {
        DocumentManagementDto.UploadReq dto = new DocumentManagementDto.UploadReq();
        dto.setProjectId(projectId);
        dto.setFile(file);
        dto.setDocType(DocType.TRADE_PLAN);
        dto.setIsPartner(false);
        dto.setAffiliationName("본사");
        dto.setName(currentUploaderName());
        documentManagementService.upload(dto);
    }

    private String currentUploaderName() {
        return authAccessService.currentUser()
                .map(user -> firstNonBlank(user.getName(), user.getLoginId()))
                .orElse("system");
    }

    private File storeUploadFile(MultipartFile file) {
        if (file == null || file.isEmpty()) {
            throw new RuntimeException("업로드할 파일이 없습니다.");
        }

        try {
            Path uploadDir = Paths.get(System.getProperty("user.dir"), "uploads", "work-plan")
                    .toAbsolutePath()
                    .normalize();
            Files.createDirectories(uploadDir);

            String originalFileName = sanitizeFileName(file.getOriginalFilename());
            Path target = uploadDir.resolve(UUID.randomUUID() + "_" + originalFileName).normalize();
            if (!target.startsWith(uploadDir)) {
                throw new RuntimeException("잘못된 파일명입니다.");
            }

            try (InputStream inputStream = file.getInputStream()) {
                Files.copy(inputStream, target, StandardCopyOption.REPLACE_EXISTING);
            }
            return target.toFile();
        } catch (IOException e) {
            throw new RuntimeException("계획서 파일 저장 중 오류가 발생했습니다.", e);
        }
    }

    private String sanitizeFileName(String originalFileName) {
        if (originalFileName == null || originalFileName.isBlank()) {
            return "work-plan-upload";
        }

        String sanitized = Paths.get(originalFileName).getFileName().toString()
                .replaceAll("[\\\\/:*?\"<>|]", "_");
        return sanitized.isBlank() ? "work-plan-upload" : sanitized;
    }


    private WorkPlan findPlan(Long planId) {
        return workPlanRepository.findById(planId)
                .orElseThrow(() -> new RuntimeException("작업 계획을 찾을 수 없습니다."));
    }

    // Prefer the latest submitted daily report progress over the cached monthly plan value.
    private WorkPlanDto.workPlanRes toWorkPlanResWithReportProgress(WorkPlan plan) {
        return WorkPlanDto.workPlanRes.from(plan, resolveActualProgressPct(plan));
    }

    private BigDecimal resolveActualProgressPct(WorkPlan plan) {
        BigDecimal stored = plan.getActualProgressPct() != null
                ? plan.getActualProgressPct()
                : BigDecimal.ZERO;

        return dailyReportRepository
                .findTopByMonthlyWorkPlan_IdxAndReportDateLessThanEqualOrderByReportDateDesc(
                        plan.getIdx(),
                        LocalDate.now()
                )
                .map(report -> extractMonthlyProgressPct(report, stored))
                .orElse(stored);
    }

    private BigDecimal extractMonthlyProgressPct(DailyReport report, BigDecimal fallback) {
        Double progress = report.getMonthlyProgressPct() != null
                ? report.getMonthlyProgressPct()
                : report.getActualProgress();

        if (progress == null) return fallback;

        double clamped = Math.max(0.0, Math.min(100.0, progress));
        return BigDecimal.valueOf(clamped);
    }

    /**
     * tradeProcessId가 있을 때만 공정 연결.
     * null이면 기존 연결 유지 (연결 해제는 별도 API로 분리 권장).
     */
    private void linkTradeProcessIfPresent(WorkPlan plan, Long tradeProcessId) {
        if (tradeProcessId == null) return;

        TradeProcess tradeProcess = tradeProcessRepository.findById(tradeProcessId)
                .orElseThrow(() -> new RuntimeException("공정을 찾을 수 없습니다. id=" + tradeProcessId));

        authAccessService.assertTradeProcessWriteAccess(tradeProcess);
        plan.linkTradeProcess(tradeProcess);
    }

    private void linkParentWorkPlanIfPresent(WorkPlan plan, Long parentWorkPlanId) {
        if (parentWorkPlanId == null) return;

        WorkPlan parent = workPlanRepository.findById(parentWorkPlanId)
                .orElseThrow(() -> new RuntimeException("상위 작업 계획을 찾을 수 없습니다. id=" + parentWorkPlanId));

        authAccessService.assertWorkPlanWriteAccess(parent);
        plan.linkParentWorkPlan(parent);
    }

    private void triggerZoneSync(LocalDate date) {
        staffingService.syncZonesFromWorkPlans(date != null ? date : LocalDate.now());
    }

    private void validateWeeklyItem(WorkPlanDto.WeeklyItemReq item) {
        if (item.getDate() == null) throw new RuntimeException("작업일자는 필수입니다.");
        if (item.getProcessName() == null || item.getProcessName().isBlank()) throw new RuntimeException("공정명은 필수입니다.");
        if (item.getZone() == null || item.getZone().isBlank()) throw new RuntimeException("작업구역은 필수입니다.");
        if (item.getWorkers() == null || item.getWorkers().isEmpty()) throw new RuntimeException("인력은 최소 1개 이상 필요합니다.");

        boolean hasValidWorker = item.getWorkers().stream()
                .anyMatch(w -> w != null && w.getTrade() != null && !w.getTrade().isBlank()
                        && w.getCount() != null && w.getCount() > 0);
        if (!hasValidWorker) throw new RuntimeException("유효한 인력 항목이 없습니다.");

        if (item.getEquipment() == null || item.getEquipment().isEmpty()) throw new RuntimeException("장비는 최소 1개 이상 필요합니다.");

        boolean hasValidEquipment = item.getEquipment().stream()
                .anyMatch(e -> e != null && e.getType() != null && !e.getType().isBlank()
                        && e.getCount() != null && e.getCount() > 0);
        if (!hasValidEquipment) throw new RuntimeException("유효한 장비 항목이 없습니다.");
    }
    // Feat : 목록 조회용 (일보 DB를 뒤지지 않고 빠르게 변환)
    private WorkPlanDto.workPlanRes toWorkPlanResSimple(WorkPlan plan) {
        return WorkPlanDto.workPlanRes.from(plan, plan.getActualProgressPct());
    }
}
