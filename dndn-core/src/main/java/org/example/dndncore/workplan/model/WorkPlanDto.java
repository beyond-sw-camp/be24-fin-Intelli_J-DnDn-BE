package org.example.dndncore.workplan.model;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.*;
import org.example.dndncore.workplan.model.entity.WorkPlan;
import org.example.dndncore.workplan.model.entity.WorkPlanEquipment;
import org.example.dndncore.workplan.model.entity.WorkPlanExtension;
import org.example.dndncore.workplan.model.entity.WorkPlanWorker;
import org.example.dndncore.workplan.model.enums.*;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;

public class WorkPlanDto {

    private static final DateTimeFormatter DATE_FORMATTER = DateTimeFormatter.ofPattern("yyyy.MM.dd");

    // feat : 인력 항목 - 직종 + 인원수
    @Getter @Setter @AllArgsConstructor @NoArgsConstructor @Builder
    @Schema(description = "feat : 작업 인력 정보")
    public static class WorkerEntry {
        @Schema(description = "직종", example = "전공")
        private String trade;
        @Schema(description = "인원수", example = "4")
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

    // feat : 장비 항목 - 장비 종류 + 수량
    @Getter @Setter @AllArgsConstructor @NoArgsConstructor @Builder
    @Schema(description = "feat : 작업 장비 정보")
    public static class EquipmentEntry {
        @Schema(description = "장비 종류", example = "타워크레인")
        private String type;
        @Schema(description = "수량", example = "1")
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
    @Schema(description = "feat : 작업 계획 생성/수정 요청 정보")
    public static class Req {
        @Schema(description = "상위 공정 ID", example = "123")
        private Long tradeProcessId;
        @Schema(description = "상위 작업 계획 ID", example = "456")
        private Long parentWorkPlanId;
        @Schema(description = "작업명", example = "기초 공사")
        private String name;
        @Schema(description = "공종", example = "토공")
        private String trade;
        @Schema(description = "작업 위치/구역", example = "A동 1층")
        private String location;
        @Schema(description = "계획 유형", example = "월간")
        private String planType;
        @Schema(description = "상태", example = "진행 예정")
        private String status;
        @Schema(description = "계획 시작일", example = "2026-05-27")
        private LocalDate startDate;
        @Schema(description = "계획 종료일", example = "2026-06-27")
        private LocalDate endDate;
        @Schema(description = "협력사명", example = "ABC 건설")
        private String partner;
        @Schema(description = "담당자명", example = "홍길동")
        private String manager;
        @Schema(description = "담당자 연락처", example = "010-1234-5678")
        private String contact;
        @Schema(description = "비고", example = "날씨에 따라 일정 조정 가능")
        private String note;
        @Schema(description = "인력 목록")
        private List<WorkerEntry> workers;
        @Schema(description = "장비 목록")
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
    @Schema(description = "feat : 일정 연장 요청 정보")
    public static class ExtReq {
        @Schema(description = "연장된 종료일", example = "2026-07-27")
        private LocalDate extendedEnd;
        @Schema(description = "연장 일수", example = "30")
        private Integer addedDays;
        @Schema(description = "연장 사유", example = "날씨 지연")
        private String reason;
    }

    @Getter @Setter @AllArgsConstructor @NoArgsConstructor @Builder
    @Schema(description = "feat : 파일 업로드 추출 결과")
    public static class UploadExtractRes {
        @Schema(description = "공정 ID", example = "123")
        private Long tradeProcessId;
        @Schema(description = "공정명", example = "기초 공사")
        private String tradeProcessName;
        @Schema(description = "공종", example = "토공")
        private String trade;
        @Schema(description = "작업명", example = "파일에서 추출된 작업명")
        private String name;
        @Schema(description = "작업 위치", example = "A동")
        private String location;
        @Schema(description = "계획 유형", example = "월간")
        private String planType;
        @Schema(description = "시작일", example = "2026-05-27")
        private LocalDate startDate;
        @Schema(description = "종료일", example = "2026-06-27")
        private LocalDate endDate;
        @Schema(description = "비고", example = "파일 추출 비고")
        private String note;
        @Schema(description = "오류 여부", example = "null")
        private String issue;
    }

    @Getter @Setter @AllArgsConstructor @NoArgsConstructor @Builder
    @Schema(description = "feat : 주간 계획 일괄 제출 요청")
    public static class WeeklySubmitReq {
        @Schema(description = "공정 ID", example = "123")
        private Long tradeProcessId;
        @Schema(description = "상위 작업 계획 ID", example = "456")
        private Long parentWorkPlanId;
        @Schema(description = "협력사명", example = "ABC 건설")
        private String partner;
        @Schema(description = "담당자명", example = "홍길동")
        private String manager;
        @Schema(description = "담당자 연락처", example = "010-1234-5678")
        private String contact;
        @Schema(description = "주간 시작일", example = "2026-05-27")
        private LocalDate weekStart;
        @Schema(description = "주간 작업 항목 목록")
        private List<WeeklyItemReq> items;
    }

    @Getter @Setter @AllArgsConstructor @NoArgsConstructor @Builder
    @Schema(description = "feat : 주간 계획 개별 작업 항목")
    public static class WeeklyItemReq {
        @Schema(description = "작업일", example = "2026-05-27")
        private LocalDate date;
        @Schema(description = "공정명", example = "기초 공사")
        private String processName;
        @Schema(description = "작업구역", example = "A동 1층")
        private String zone;
        @Schema(description = "인력 목록")
        private List<WorkerEntry> workers;
        @Schema(description = "장비 목록")
        private List<EquipmentEntry> equipment;
        @Schema(description = "비고", example = "특수 안전 조치 필요")
        private String note;
    }

    @Getter @NoArgsConstructor @AllArgsConstructor @Builder
    @Schema(description = "feat : 작업 계획 상세 조회 응답")
    public static class Res {
        @Schema(description = "공정 ID", example = "123")
        private Long tradeProcessId;
        @Schema(description = "공정명", example = "기초 공사")
        private String tradeProcessName;
        @Schema(description = "상위 공종명", example = "토공사")
        private String tradeProcessTradeName;
        @Schema(description = "상위 작업 계획 ID", example = "456")
        private Long parentWorkPlanId;
        @Schema(description = "상위 작업 계획명", example = "상위 계획")
        private String parentWorkPlanName;
        @Schema(description = "작업 계획 ID", example = "1")
        private Long idx;
        @Schema(description = "작업명", example = "기초 공사")
        private String name;
        @Schema(description = "공종", example = "토공")
        private String trade;
        @Schema(description = "작업 위치", example = "A동 1층")
        private String location;
        @Schema(description = "계획 유형", example = "월간")
        private String planType;
        @Schema(description = "상태", example = "진행 예정")
        private String status;
        @Schema(description = "기간", example = "2026.05.27 ~ 2026.06.27")
        private String period;
        @Schema(description = "시작일", example = "2026-05-27")
        private LocalDate startDate;
        @Schema(description = "종료일", example = "2026-06-27")
        private LocalDate endDate;
        @Schema(description = "실제 시작일", example = "2026-05-28")
        private LocalDate actualStart;
        @Schema(description = "유효 종료일 (연장 가능)", example = "2026-06-27")
        private LocalDate effectiveEnd;
        @Schema(description = "필요 인원", example = "6")
        private Integer requiredCount;
        @Schema(description = "협력사명", example = "ABC 건설")
        private String partner;
        @Schema(description = "담당자명", example = "홍길동")
        private String manager;
        @Schema(description = "담당자 연락처", example = "010-1234-5678")
        private String contact;
        @Schema(description = "비고", example = "날씨에 따라 조정 가능")
        private String note;
        @Schema(description = "인력 항목 목록")
        private List<WorkerEntry> workers;
        @Schema(description = "인력 표시용 문자열", example = "전공 4명, 보통공 2명")
        private String workersDisplay;
        @Schema(description = "장비 항목 목록")
        private List<EquipmentEntry> equipment;
        @Schema(description = "장비 표시용 문자열", example = "타워크레인 1대, 펌프카 1대")
        private String equipmentDisplay;
        @Schema(description = "일정 연장 정보")
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
                    .tradeProcessId(entity.getTradeProcess() != null
                            ? entity.getTradeProcess().getIdx() : null)
                    .tradeProcessName(entity.getTradeProcess() != null
                            ? entity.getTradeProcess().getProcessName() : null)
                    .tradeProcessTradeName(entity.getTradeProcess() != null
                            ? entity.getTradeProcess().getTradeName() : null)
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
    @Schema(description = "feat : 작업 계획 목록 조회 응답")
    public static class workPlanRes {
        @Schema(description = "공정 ID", example = "123")
        private Long tradeProcessId;
        @Schema(description = "공정명", example = "기초 공사")
        private String tradeProcessName;
        @Schema(description = "상위 공종명", example = "토공사")
        private String tradeProcessTradeName;
        @Schema(description = "상위 작업 계획 ID", example = "456")
        private Long parentWorkPlanId;
        @Schema(description = "상위 작업 계획명", example = "상위 계획")
        private String parentWorkPlanName;
        @Schema(description = "작업 계획 ID", example = "1")
        private Long idx;
        @Schema(description = "작업명", example = "기초 공사")
        private String name;
        @Schema(description = "공종", example = "토공")
        private String trade;
        @Schema(description = "작업 위치", example = "A동 1층")
        private String location;
        @Schema(description = "계획 유형", example = "월간")
        private String planType;
        @Schema(description = "상태", example = "진행 예정")
        private String status;
        @Schema(description = "기간", example = "2026.05.27 ~ 2026.06.27")
        private String period;
        @Schema(description = "시작일", example = "2026-05-27")
        private LocalDate startDate;
        @Schema(description = "종료일", example = "2026-06-27")
        private LocalDate endDate;
        @Schema(description = "유효 종료일 (연장 가능)", example = "2026-06-27")
        private LocalDate effectiveEnd;
        @Schema(description = "필요 인원", example = "6")
        private Integer requiredCount;
        @Schema(description = "인력 표시용 문자열", example = "전공 4명, 보통공 2명")
        private String workersDisplay;
        @Schema(description = "장비 표시용 문자열", example = "타워크레인 1대, 펌프카 1대")
        private String equipmentDisplay;
        @Schema(description = "연장 일수", example = "30")
        private Integer addedDays;
        @Schema(description = "협력사명", example = "ABC 건설")
        private String partner;
        @Schema(description = "담당자명", example = "홍길동")
        private String manager;
        @Schema(description = "담당자 연락처", example = "010-1234-5678")
        private String contact;
        @Schema(description = "비고", example = "날씨에 따라 조정 가능")
        private String note;
        @Schema(description = "실제 진행률", example = "75.5")
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
                    .tradeProcessId(entity.getTradeProcess() != null
                            ? entity.getTradeProcess().getIdx() : null)
                    .tradeProcessName(entity.getTradeProcess() != null
                            ? entity.getTradeProcess().getProcessName() : null)
                    .tradeProcessTradeName(entity.getTradeProcess() != null
                            ? entity.getTradeProcess().getTradeName() : null)
                    .parentWorkPlanId(entity.getParentWorkPlan() != null
                            ? entity.getParentWorkPlan().getIdx() : null)
                    .parentWorkPlanName(entity.getParentWorkPlan() != null
                            ? entity.getParentWorkPlan().getName() : null)
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
    @Schema(description = "feat : 일정 연장 응답 정보")
    public static class ExtRes {
        @Schema(description = "연장된 종료일", example = "2026-07-27")
        private LocalDate extendedEnd;
        @Schema(description = "연장 일수", example = "30")
        private Integer addedDays;
        @Schema(description = "연장 사유", example = "날씨 지연")
        private String reason;
        @Schema(description = "반영일", example = "2026.06.27")
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
