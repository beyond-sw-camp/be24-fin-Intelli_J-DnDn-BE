package org.example.dndn.workplan.model;

import lombok.*;
import org.example.dndn.workplan.model.entity.WorkPlan;
import org.example.dndn.workplan.model.entity.WorkPlanEquipment;
import org.example.dndn.workplan.model.entity.WorkPlanExtension;
import org.example.dndn.workplan.model.entity.WorkPlanWorker;
import org.example.dndn.workplan.model.enums.*;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;

public class WorkPlanDto {

    private static final DateTimeFormatter DATE_FORMATTER = DateTimeFormatter.ofPattern("yyyy.MM.dd");

    /**
     * 인력 항목 - 직종 + 인원수. 요청/응답 공용.
     */
    @Getter @Setter @AllArgsConstructor @NoArgsConstructor @Builder
    public static class WorkerEntry {
        private String trade;
        private Integer count;

        public WorkPlanWorker toEntity() {
            WorkerTrade tradeEnum = WorkerTrade.fromLabel(this.trade);
            if (tradeEnum == null) throw new IllegalArgumentException("직종은 필수입니다.");
            if (this.count == null || this.count < 1) throw new IllegalArgumentException("인원수는 1명 이상이어야 합니다.");
            return WorkPlanWorker.builder().trade(tradeEnum).count(this.count).build();
        }

        public static WorkerEntry from(WorkPlanWorker entity) {
            return WorkerEntry.builder()
                    .trade(entity.getTrade() == null ? null : entity.getTrade().getLabel())
                    .count(entity.getCount())
                    .build();
        }
    }

    /**
     * 장비 항목 - 장비 종류 + 수량. 요청/응답 공용.
     */
    @Getter @Setter @AllArgsConstructor @NoArgsConstructor @Builder
    public static class EquipmentEntry {
        private String type;
        private Integer count;

        public WorkPlanEquipment toEntity() {
            EquipmentType typeEnum = EquipmentType.fromLabel(this.type);
            if (typeEnum == null) throw new IllegalArgumentException("장비 종류는 필수입니다.");
            if (this.count == null || this.count < 1) throw new IllegalArgumentException("수량은 1대 이상이어야 합니다.");
            return WorkPlanEquipment.builder().type(typeEnum).count(this.count).build();
        }

        public static EquipmentEntry from(WorkPlanEquipment entity) {
            return EquipmentEntry.builder()
                    .type(entity.getType() == null ? null : entity.getType().getLabel())
                    .count(entity.getCount())
                    .build();
        }
    }

    @Getter @Setter @AllArgsConstructor @NoArgsConstructor @Builder
    public static class Req {
        // ── 1단계 추가 ───────────────────────────────────────────────────────
        private Long tradeProcessId;  // 상위 공정 연결 (선택 — 없으면 null)
        // ────────────────────────────────────────────────────────────────────
        private Long parentWorkPlanId;
        private String name;
        private String trade;
        private String location;
        private String planType;
        private String status;
        private LocalDate startDate;
        private LocalDate endDate;
        private String partner;
        private String manager;
        private String contact;
        private String note;
        private List<WorkerEntry> workers;
        private List<EquipmentEntry> equipment;

        public WorkPlan toEntity() {
            WorkPlan plan = WorkPlan.builder()
                    .name(this.name)
                    .trade(WorkTrade.fromLabel(this.trade))
                    .location(this.location)
                    .planType(PlanType.fromLabel(this.planType))
                    .status(PlanStatus.fromLabel(this.status))
                    .startDate(this.startDate)
                    .endDate(this.endDate)
                    .partner(this.partner)
                    .manager(this.manager)
                    .contact(this.contact)
                    .note(this.note)
                    .build();

            if (this.workers != null) {
                plan.replaceWorkers(this.workers.stream()
                        .filter(w -> w != null && w.getTrade() != null && !w.getTrade().isBlank())
                        .map(WorkerEntry::toEntity).toList());
            }
            if (this.equipment != null) {
                plan.replaceEquipment(this.equipment.stream()
                        .filter(e -> e != null && e.getType() != null && !e.getType().isBlank())
                        .map(EquipmentEntry::toEntity).toList());
            }
            return plan;
        }
    }

    @Getter @Setter @AllArgsConstructor @NoArgsConstructor @Builder
    public static class ExtReq {
        private LocalDate extendedEnd;
        private Integer addedDays;
        private String reason;
    }

    @Getter @Setter @AllArgsConstructor @NoArgsConstructor @Builder
    public static class WeeklySubmitReq {
        private Long tradeProcessId;
        private Long parentWorkPlanId;
        private String partner;
        private String manager;
        private String contact;
        private LocalDate weekStart;
        private List<WeeklyItemReq> items;
    }

    @Getter @Setter @AllArgsConstructor @NoArgsConstructor @Builder
    public static class WeeklyItemReq {
        private LocalDate date;
        private String processName;
        private String zone;
        private List<WorkerEntry> workers;
        private List<EquipmentEntry> equipment;
        private String note;
    }

    @Getter @NoArgsConstructor @AllArgsConstructor @Builder
    public static class Res {
        // ── 1단계 추가 ───────────────────────────────────────────────────────
        private Long tradeProcessId;    // 연결된 공정 ID
        private String tradeProcessName; // 연결된 공정명 (표시용)
        // ────────────────────────────────────────────────────────────────────
        private Long parentWorkPlanId;
        private String parentWorkPlanName;
        private Long idx;
        private String name;
        private String trade;
        private String location;
        private String planType;
        private String status;
        private String period;
        private LocalDate startDate;
        private LocalDate endDate;
        private LocalDate actualStart;
        private LocalDate effectiveEnd;
        private Integer requiredCount;
        private String partner;
        private String manager;
        private String contact;
        private String note;
        private List<WorkerEntry> workers;
        private String workersDisplay;
        private List<EquipmentEntry> equipment;
        private String equipmentDisplay;
        private ExtRes extension;

        public static Res from(WorkPlan entity) {
            String period = "";
            if (entity.getStartDate() != null && entity.getEndDate() != null) {
                period = entity.getStartDate().format(DATE_FORMATTER)
                        + " ~ " + entity.getEndDate().format(DATE_FORMATTER);
            }

            List<WorkerEntry> workerDto = entity.getWorkers() != null
                    ? entity.getWorkers().stream().map(WorkerEntry::from).toList()
                    : new ArrayList<>();

            List<EquipmentEntry> equipmentDto = entity.getEquipment() != null
                    ? entity.getEquipment().stream().map(EquipmentEntry::from).toList()
                    : new ArrayList<>();

            return Res.builder()
                    // ── 1단계 추가 ──────────────────────────────────────────
                    .tradeProcessId(entity.getTradeProcess() != null
                            ? entity.getTradeProcess().getIdx() : null)
                    .tradeProcessName(entity.getTradeProcess() != null
                            ? entity.getTradeProcess().getProcessName() : null)
                    // ────────────────────────────────────────────────────────
                    .idx(entity.getIdx())
                    .name(entity.getName())
                    .parentWorkPlanId(entity.getParentWorkPlan() != null
                            ? entity.getParentWorkPlan().getIdx() : null)
                    .parentWorkPlanName(entity.getParentWorkPlan() != null
                            ? entity.getParentWorkPlan().getName() : null)
                    .trade(entity.getTrade() == null ? null : entity.getTrade().getLabel())
                    .location(entity.getLocation())
                    .planType(entity.getPlanType() == null ? null : entity.getPlanType().getLabel())
                    .status(entity.getStatus() == null ? null : entity.getStatus().getLabel())
                    .period(period)
                    .startDate(entity.getStartDate())
                    .endDate(entity.getEndDate())
                    .actualStart(entity.getActualStart())
                    .effectiveEnd(entity.effectiveEndDate())
                    .requiredCount(entity.getRequiredCount())
                    .partner(entity.getPartner())
                    .manager(entity.getManager())
                    .contact(entity.getContact())
                    .note(entity.getNote())
                    .workers(workerDto)
                    .workersDisplay(entity.workersDisplay())
                    .equipment(equipmentDto)
                    .equipmentDisplay(entity.equipmentDisplay())
                    .extension(ExtRes.from(entity.getExtension()))
                    .build();
        }
    }

    @Getter @NoArgsConstructor @AllArgsConstructor @Builder
    public static class workPlanRes {
        // ── 1단계 추가 ───────────────────────────────────────────────────────
        private Long tradeProcessId;
        private String tradeProcessName;
        private Long parentWorkPlanId;
        private String parentWorkPlanName;
        // ────────────────────────────────────────────────────────────────────
        private Long idx;
        private String name;
        private String trade;
        private String location;
        private String planType;
        private String status;
        private String period;
        private LocalDate startDate;
        private LocalDate endDate;
        private LocalDate effectiveEnd;
        private Integer requiredCount;
        private String workersDisplay;
        private String equipmentDisplay;
        private Integer addedDays;
        private String partner;
        private String manager;
        private String contact;
        private String note;
        private BigDecimal actualProgressPct;

        public static workPlanRes from(WorkPlan entity) {
            return from(entity, entity.getActualProgressPct());
        }

        public static workPlanRes from(WorkPlan entity, BigDecimal actualProgressPct) {
            String period = "";
            if (entity.getStartDate() != null && entity.getEndDate() != null) {
                period = entity.getStartDate().format(DATE_FORMATTER)
                        + " ~ " + entity.getEndDate().format(DATE_FORMATTER);
            }
            Integer addedDays = entity.getExtension() != null
                    ? entity.getExtension().getAddedDays() : null;

            return workPlanRes.builder()
                    // ── 1단계 추가 ──────────────────────────────────────────
                    .tradeProcessId(entity.getTradeProcess() != null
                            ? entity.getTradeProcess().getIdx() : null)
                    .tradeProcessName(entity.getTradeProcess() != null
                            ? entity.getTradeProcess().getProcessName() : null)
                    .parentWorkPlanId(entity.getParentWorkPlan() != null
                            ? entity.getParentWorkPlan().getIdx() : null)
                    .parentWorkPlanName(entity.getParentWorkPlan() != null
                            ? entity.getParentWorkPlan().getName() : null)
                    // ────────────────────────────────────────────────────────
                    .idx(entity.getIdx())
                    .name(entity.getName())
                    .trade(entity.getTrade() == null ? null : entity.getTrade().getLabel())
                    .location(entity.getLocation())
                    .planType(entity.getPlanType() == null ? null : entity.getPlanType().getLabel())
                    .status(entity.getStatus() == null ? null : entity.getStatus().getLabel())
                    .period(period)
                    .startDate(entity.getStartDate())
                    .endDate(entity.getEndDate())
                    .effectiveEnd(entity.effectiveEndDate())
                    .requiredCount(entity.getRequiredCount())
                    .workersDisplay(entity.workersDisplay())
                    .equipmentDisplay(entity.equipmentDisplay())
                    .addedDays(addedDays)
                    .partner(entity.getPartner())
                    .manager(entity.getManager())
                    .contact(entity.getContact())
                    .note(entity.getNote())
                    .actualProgressPct(actualProgressPct != null ? actualProgressPct : BigDecimal.ZERO)
                    .build();
        }
    }

    @Getter @NoArgsConstructor @AllArgsConstructor @Builder
    public static class ExtRes {
        private LocalDate extendedEnd;
        private Integer addedDays;
        private String reason;
        private String decidedAt;

        public static ExtRes from(WorkPlanExtension entity) {
            if (entity == null) return null;
            return ExtRes.builder()
                    .extendedEnd(entity.getExtendedEnd())
                    .addedDays(entity.getAddedDays())
                    .reason(entity.getReason())
                    .decidedAt(entity.getDecidedAt() != null
                            ? entity.getDecidedAt().format(DATE_FORMATTER) : "")
                    .build();
        }
    }
}
