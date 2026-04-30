package org.example.dndn.workplan.model;

import lombok.*;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;

public class WorkPlanDto {

    private static final DateTimeFormatter DATE_FORMATTER = DateTimeFormatter.ofPattern("yyyy.MM.dd");

    /**
     * 인력 항목 - 직종 + 인원수.
     * 요청/응답 공용.
     */
    @Getter
    @Setter
    @AllArgsConstructor
    @NoArgsConstructor
    @Builder
    public static class WorkerEntry {
        private String trade;     // 직종 라벨 (예: "전공", "보통공")
        private Integer count;    // 인원수

        public WorkPlanWorker toEntity() {
            WorkerTrade tradeEnum = WorkerTrade.fromLabel(this.trade);

            if (tradeEnum == null) {
                throw new IllegalArgumentException("직종은 필수입니다.");
            }

            if (this.count == null || this.count < 1) {
                throw new IllegalArgumentException("인원수는 1명 이상이어야 합니다.");
            }

            return WorkPlanWorker.builder()
                    .trade(tradeEnum)
                    .count(this.count)
                    .build();
        }

        public static WorkerEntry from(WorkPlanWorker entity) {
            return WorkerEntry.builder()
                    .trade(entity.getTrade() == null ? null : entity.getTrade().getLabel())
                    .count(entity.getCount())
                    .build();
        }
    }

    /**
     * 장비 항목 - 장비 종류 + 수량.
     * 요청/응답 공용.
     */
    @Getter
    @Setter
    @AllArgsConstructor
    @NoArgsConstructor
    @Builder
    public static class EquipmentEntry {
        private String type;      // 장비 라벨 (예: "타워크레인")
        private Integer count;    // 수량

        public WorkPlanEquipment toEntity() {
            EquipmentType typeEnum = EquipmentType.fromLabel(this.type);

            if (typeEnum == null) {
                throw new IllegalArgumentException("장비 종류는 필수입니다.");
            }

            if (this.count == null || this.count < 1) {
                throw new IllegalArgumentException("수량은 1대 이상이어야 합니다.");
            }

            return WorkPlanEquipment.builder()
                    .type(typeEnum)
                    .count(this.count)
                    .build();
        }

        public static EquipmentEntry from(WorkPlanEquipment entity) {
            return EquipmentEntry.builder()
                    .type(entity.getType() == null ? null : entity.getType().getLabel())
                    .count(entity.getCount())
                    .build();
        }
    }

    @Getter
    @Setter
    @AllArgsConstructor
    @NoArgsConstructor
    @Builder
    public static class Req {
        private String name;
        private String trade;            // 공종 라벨
        private String location;
        private String planType;         // "연간" | "월간" | "주간"
        private String status;           // "계획" | "검토 중" | "확정" | "진행 중"
        private LocalDate startDate;
        private LocalDate endDate;
        private String partner;
        private String manager;
        private String contact;
        private String note;
        private List<WorkerEntry> workers;       // 직종별 인력
        private List<EquipmentEntry> equipment;   // 장비별 수량

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
                List<WorkPlanWorker> workerList = this.workers.stream()
                        .filter(w -> w != null && w.getTrade() != null && !w.getTrade().isBlank())
                        .map(WorkerEntry::toEntity)
                        .toList();

                plan.replaceWorkers(workerList);
            }

            if (this.equipment != null) {
                List<WorkPlanEquipment> equipmentList = this.equipment.stream()
                        .filter(e -> e != null && e.getType() != null && !e.getType().isBlank())
                        .map(EquipmentEntry::toEntity)
                        .toList();

                plan.replaceEquipment(equipmentList);
            }

            return plan;
        }
    }

    @Getter
    @Setter
    @AllArgsConstructor
    @NoArgsConstructor
    @Builder
    public static class ExtReq {
        private LocalDate extendedEnd;
        private Integer addedDays;
        private String reason;
    }

    @Getter
    @Setter
    @AllArgsConstructor
    @NoArgsConstructor
    @Builder
    public static class WeeklySubmitReq {
        private String partner;
        private String manager;
        private String contact;
        private LocalDate weekStart;
        private List<WeeklyItemReq> items;
    }

    @Getter
    @Setter
    @AllArgsConstructor
    @NoArgsConstructor
    @Builder
    public static class WeeklyItemReq {
        private LocalDate date;
        private String processName;             // 공정명 → name 매핑
        private String zone;                     // 작업구역 → location 매핑
        private List<WorkerEntry> workers;       // 직종별 인력
        private List<EquipmentEntry> equipment;  // 장비별 수량
        private String note;
    }

    @Getter
    @NoArgsConstructor
    @AllArgsConstructor
    @Builder
    public static class Res {
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
        private List<WorkerEntry> workers;        // 구조화 데이터
        private String workersDisplay;             // 표시용 ("전공 4명, 보통공 2명")
        private List<EquipmentEntry> equipment;    // 구조화 데이터
        private String equipmentDisplay;           // 표시용 ("타워크레인 1대, 펌프카 1대")
        private ExtRes extension;

        public static Res from(WorkPlan entity) {
            String period = "";

            if (entity.getStartDate() != null && entity.getEndDate() != null) {
                period = entity.getStartDate().format(DATE_FORMATTER) + " ~ "
                        + entity.getEndDate().format(DATE_FORMATTER);
            }

            List<WorkerEntry> workerDto = new ArrayList<>();

            if (entity.getWorkers() != null) {
                workerDto = entity.getWorkers().stream()
                        .map(WorkerEntry::from)
                        .toList();
            }

            List<EquipmentEntry> equipmentDto = new ArrayList<>();

            if (entity.getEquipment() != null) {
                equipmentDto = entity.getEquipment().stream()
                        .map(EquipmentEntry::from)
                        .toList();
            }

            return Res.builder()
                    .idx(entity.getIdx())
                    .name(entity.getName())
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

    @Getter
    @NoArgsConstructor
    @AllArgsConstructor
    @Builder
    public static class workPlanRes {
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
        private String workersDisplay;    // 목록 표시용 ("전공 4명, 보통공 2명")
        private String equipmentDisplay;  // 목록 표시용 ("타워크레인 1대, 펌프카 1대")
        private Integer addedDays;
        private String partner;
        private String manager;
        private String contact;
        private String note;

        public static workPlanRes from(WorkPlan entity) {
            String period = "";

            if (entity.getStartDate() != null && entity.getEndDate() != null) {
                period = entity.getStartDate().format(DATE_FORMATTER) + " ~ "
                        + entity.getEndDate().format(DATE_FORMATTER);
            }

            Integer addedDays = null;

            if (entity.getExtension() != null) {
                addedDays = entity.getExtension().getAddedDays();
            }

            return workPlanRes.builder()
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
                    .build();
        }
    }

    @Getter
    @NoArgsConstructor
    @AllArgsConstructor
    @Builder
    public static class ExtRes {
        private LocalDate extendedEnd;
        private Integer addedDays;
        private String reason;
        private String decidedAt;

        public static ExtRes from(WorkPlanExtension entity) {
            if (entity == null) {
                return null;
            }

            String decidedAt = entity.getDecidedAt() != null
                    ? entity.getDecidedAt().format(DATE_FORMATTER)
                    : "";

            return ExtRes.builder()
                    .extendedEnd(entity.getExtendedEnd())
                    .addedDays(entity.getAddedDays())
                    .reason(entity.getReason())
                    .decidedAt(decidedAt)
                    .build();
        }
    }
}