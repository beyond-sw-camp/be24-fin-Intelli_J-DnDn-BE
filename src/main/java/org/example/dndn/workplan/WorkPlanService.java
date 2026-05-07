package org.example.dndn.workplan;

import lombok.RequiredArgsConstructor;
import org.example.dndn.project.repository.TradeProcessRepository;
import org.example.dndn.project.model.entity.TradeProcess;
import org.example.dndn.workplan.model.*;
import org.example.dndn.workplan.model.entity.WorkPlan;
import org.example.dndn.workplan.model.entity.WorkPlanExtension;
import org.example.dndn.workplan.model.enums.PlanStatus;
import org.example.dndn.workplan.model.enums.PlanType;
import org.example.dndn.workplan.model.enums.WorkTrade;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class WorkPlanService {

    private final WorkPlanRepository workPlanRepository;
    // ── 1단계 추가 ─────────────────────────────────────────────────────────
    private final TradeProcessRepository tradeProcessRepository;
    // ────────────────────────────────────────────────────────────────────────

    // 작업 계획 등록
    @Transactional
    public Long create(WorkPlanDto.Req dto) {
        WorkPlan plan = dto.toEntity();

        linkTradeProcessIfPresent(plan, dto.getTradeProcessId());
        linkParentWorkPlanIfPresent(plan, dto.getParentWorkPlanId());

        return workPlanRepository.save(plan).getIdx();
    }

    // 작업 계획 단일 조회
    public WorkPlanDto.Res read(Long planId) {
        return WorkPlanDto.Res.from(findPlan(planId));
    }

    // 작업 계획 목록 조회 (계획 종류 + 공종/상태 필터)
    public List<WorkPlanDto.workPlanRes> list(String planType, String trade, String status) {
        PlanType type = PlanType.fromLabel(planType);
        WorkTrade tradeEnum = WorkTrade.fromLabel(trade);
        PlanStatus statusEnum = (status == null || status.isBlank())
                ? null : PlanStatus.fromLabel(status);

        List<WorkPlan> plans;
        if (tradeEnum != null && statusEnum != null) {
            plans = workPlanRepository.findAllByPlanTypeAndTradeAndStatus(type, tradeEnum, statusEnum);
        } else if (tradeEnum != null) {
            plans = workPlanRepository.findAllByPlanTypeAndTrade(type, tradeEnum);
        } else if (statusEnum != null) {
            plans = workPlanRepository.findAllByPlanTypeAndStatus(type, statusEnum);
        } else {
            plans = workPlanRepository.findAllByPlanType(type);
        }

        return plans.stream().map(WorkPlanDto.workPlanRes::from).toList();
    }

    // 작업 계획 정보 수정
    @Transactional
    public void update(Long planId, WorkPlanDto.Req dto) {
        WorkPlan plan = findPlan(planId);

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
    }

    // 일정 연장 등록/수정
    @Transactional
    public void extend(Long planId, WorkPlanDto.ExtReq dto) {
        WorkPlan plan = findPlan(planId);
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

            savedIds.add(workPlanRepository.save(plan).getIdx());
        }

        return savedIds;
    }

    // 작업 착수 처리
    @Transactional
    public void start(Long planId) {
        findPlan(planId).markStarted(LocalDate.now());
    }

    // 작업 계획 삭제
    @Transactional
    public void delete(Long planId) {
        workPlanRepository.delete(findPlan(planId));
    }


    private WorkPlan findPlan(Long planId) {
        return workPlanRepository.findById(planId)
                .orElseThrow(() -> new RuntimeException("작업 계획을 찾을 수 없습니다."));
    }

    /**
     * tradeProcessId가 있을 때만 공정 연결.
     * null이면 기존 연결 유지 (연결 해제는 별도 API로 분리 권장).
     */
    private void linkTradeProcessIfPresent(WorkPlan plan, Long tradeProcessId) {
        if (tradeProcessId == null) return;

        TradeProcess tradeProcess = tradeProcessRepository.findById(tradeProcessId)
                .orElseThrow(() -> new RuntimeException("공정을 찾을 수 없습니다. id=" + tradeProcessId));

        plan.linkTradeProcess(tradeProcess);
    }

    private void linkParentWorkPlanIfPresent(WorkPlan plan, Long parentWorkPlanId) {
        if (parentWorkPlanId == null) return;

        WorkPlan parent = workPlanRepository.findById(parentWorkPlanId)
                .orElseThrow(() -> new RuntimeException("상위 작업 계획을 찾을 수 없습니다. id=" + parentWorkPlanId));

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
}