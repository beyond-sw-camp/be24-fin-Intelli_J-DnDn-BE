package org.example.dndn.workplan;

import lombok.RequiredArgsConstructor;
import org.example.dndn.auth.security.AuthAccessService;
import org.example.dndn.project.repository.TradeProcessRepository;
import org.example.dndn.project.model.entity.TradeProcess;
import org.example.dndn.report.DailyReportRepository;
import org.example.dndn.report.model.DailyReport;
import org.example.dndn.workplan.model.*;
import org.example.dndn.workplan.model.entity.WorkPlan;
import org.example.dndn.workplan.model.entity.WorkPlanExtension;
import org.example.dndn.workplan.model.enums.PlanStatus;
import org.example.dndn.workplan.model.enums.PlanType;
import org.example.dndn.workplan.model.enums.WorkTrade;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class WorkPlanService {

    private final WorkPlanRepository workPlanRepository;
    private final DailyReportRepository dailyReportRepository;
    private final AuthAccessService authAccessService;
    // ── 1단계 추가 ─────────────────────────────────────────────────────────
    private final TradeProcessRepository tradeProcessRepository;
    // ────────────────────────────────────────────────────────────────────────

    // 작업 계획 등록
    @Transactional
    public Long create(WorkPlanDto.Req dto) {
        WorkPlan plan = dto.toEntity();

        linkTradeProcessIfPresent(plan, dto.getTradeProcessId());
        linkParentWorkPlanIfPresent(plan, dto.getParentWorkPlanId());
        authAccessService.assertWorkPlanAccess(plan);

        return workPlanRepository.save(plan).getIdx();
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
            LocalDate finalStartDate = (startDate != null) ? startDate : LocalDate.now().minusMonths(6);
            LocalDate finalEndDate = (endDate != null) ? endDate : LocalDate.now().plusMonths(6);

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
        authAccessService.assertWorkPlanAccess(plan);

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
        authAccessService.assertWorkPlanAccess(plan);
    }

    // 일정 연장 등록/수정
    @Transactional
    public void extend(Long planId, WorkPlanDto.ExtReq dto) {
        WorkPlan plan = findPlan(planId);
        authAccessService.assertWorkPlanAccess(plan);
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
            authAccessService.assertWorkPlanAccess(plan);

            savedIds.add(workPlanRepository.save(plan).getIdx());
        }

        return savedIds;
    }

    // 작업 착수 처리
    @Transactional
    public void start(Long planId) {
        WorkPlan plan = findPlan(planId);
        authAccessService.assertWorkPlanAccess(plan);
        plan.markStarted(LocalDate.now());
    }

    // 작업 계획 삭제
    @Transactional
    public void delete(Long planId) {
        WorkPlan plan = findPlan(planId);
        authAccessService.assertWorkPlanAccess(plan);
        workPlanRepository.delete(plan);
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

        authAccessService.assertTradeProcessAccess(tradeProcess);
        plan.linkTradeProcess(tradeProcess);
    }

    private void linkParentWorkPlanIfPresent(WorkPlan plan, Long parentWorkPlanId) {
        if (parentWorkPlanId == null) return;

        WorkPlan parent = workPlanRepository.findById(parentWorkPlanId)
                .orElseThrow(() -> new RuntimeException("상위 작업 계획을 찾을 수 없습니다. id=" + parentWorkPlanId));

        authAccessService.assertWorkPlanAccess(parent);
        plan.linkParentWorkPlan(parent);
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
