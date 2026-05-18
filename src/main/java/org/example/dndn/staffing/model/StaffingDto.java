package org.example.dndn.staffing.model;

import lombok.*;
import org.example.dndn.worker.model.entity.Worker;
import org.example.dndn.worker.model.enums.AffiliationKind;
import org.example.dndn.worker.model.enums.EmploymentKind;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

public class StaffingDto {

    // STAFFING_005 요청 — 상세 구역(ZoneSub) 제목 및 직종별 필요 인원 전체 교체 표현
    @Getter
    @Setter
    @NoArgsConstructor
    @AllArgsConstructor
    @Builder
    public static class ZoneUpdateReq {
        private String title;
        private List<TradeNeedReq> tradeNeeds;
    }

    // STAFFING_005 필요 직종 1건 (동일 trade 중복 요청 시 need 합산)
    @Getter
    @Setter
    @NoArgsConstructor
    @AllArgsConstructor
    @Builder
    public static class TradeNeedReq {
        private Trade trade;
        private int need;

        public TradeNeed toEntity(ZoneSub zoneSub) {
            return TradeNeed.builder()
                    .zoneSub(zoneSub)
                    .trade(trade)
                    .need(Math.max(0, need))
                    .build();
        }
    }

    // STAFFING_007 — 상세 구역에 미투입 작업자 수동 배치 (subZoneIdx 는 경로변수와 동기화 가능)
    @Getter
    @Setter
    @NoArgsConstructor
    @AllArgsConstructor
    @Builder
    public static class AssignReq {
        private Long subZoneIdx;
        private List<Long> workerIds;
    }

    // STAFFING_008 — 우측 패널 검색 (소속 DIRECT/PARTNER, 이름·협력사 키워드, 미배치만)
    @Getter
    @Setter
    @NoArgsConstructor
    @AllArgsConstructor
    @Builder
    public static class PoolSearchReq {
        private String siteCode;
        /** null 이면 소속 필터 없음 */
        private AffiliationKind affiliationKind;
        private String keyword;
        /** true 이면 배치(staffing_assignment) 없는 명단만 */
        private boolean unassignedOnly;
    }

    // STAFFING_003 — 기본 구역 정보 조회 응답
    @Getter
    @NoArgsConstructor
    @AllArgsConstructor
    @Builder
    public static class ZoneMainRes {
        private Long idx;
        private String title;
        private String source;
        private int totalAssigned;
        private int totalRequired;
        private List<ZoneSubSummaryRes> subZones;

        public static ZoneMainRes from(ZoneMain zm) {
            int assigned =
                    zm.getZoneSubs().stream().mapToInt(zs -> zs.getAssignments().size()).sum();
            int required = zm.getZoneSubs().stream().mapToInt(ZoneSub::getRequired).sum();
            return ZoneMainRes.builder()
                    .idx(zm.getIdx())
                    .title(zm.getTitle())
                    .source(zm.isScheduleGenerated() ? "WORK_PLAN" : "MANUAL")
                    .totalAssigned(assigned)
                    .totalRequired(required)
                    .subZones(zm.getZoneSubs().stream().map(ZoneSubSummaryRes::from).toList())
                    .build();
        }
    }

    // STAFFING_003 응답 안의 상세 구역(ZoneSub) 요약 1건
    @Getter
    @NoArgsConstructor
    @AllArgsConstructor
    @Builder
    public static class ZoneSubSummaryRes {
        private Long idx;
        private Long workPlanId;
        private String title;
        private String location;
        private String tradeName;
        private String workTime;
        private LocalDate workDate;
        private int required;
        private int assignedCount;

        public static ZoneSubSummaryRes from(ZoneSub zs) {
            return ZoneSubSummaryRes.builder()
                    .idx(zs.getIdx())
                    .workPlanId(zs.getWorkPlanIdx())
                    .title(zs.getTitle())
                    .location(zs.getLocation())
                    .tradeName(zs.getTradeName())
                    .workTime(zs.getWorkTime())
                    .workDate(zs.getWorkDate())
                    .required(zs.getRequired())
                    .assignedCount(zs.getAssignments().size())
                    .build();
        }
    }

    // STAFFING_004 — 상세 구역(ZoneSub) 단건 및 직종별 필요/충원
    @Getter
    @NoArgsConstructor
    @AllArgsConstructor
    @Builder
    public static class ZoneSubRes {
        private Long idx;
        private Long zoneMainIdx;
        private Long workPlanId;
        private String title;
        private String location;
        private String tradeName;
        private String workTime;
        private LocalDate workDate;
        private int required;
        private int assignedCount;
        private List<TradeNeedRes> tradeNeeds;

        // 직종별 현재 해당 ZoneSub 에 투입된 인원 수(마스터 공종으로 분류)
        public static ZoneSubRes from(ZoneSub zs, Map<Trade, Integer> filledByTrade) {
            return ZoneSubRes.builder()
                    .idx(zs.getIdx())
                    .zoneMainIdx(zs.getZoneMain().getIdx())
                    .workPlanId(zs.getWorkPlanIdx())
                    .title(zs.getTitle())
                    .location(zs.getLocation())
                    .tradeName(zs.getTradeName())
                    .workTime(zs.getWorkTime())
                    .workDate(zs.getWorkDate())
                    .required(zs.getRequired())
                    .assignedCount(zs.getAssignments().size())
                    .tradeNeeds(zs.getTradeNeeds().stream()
                            .map(tn -> TradeNeedRes.from(
                                    tn, filledByTrade.getOrDefault(tn.getTrade(), 0)))
                            .toList())
                    .build();
        }
    }

    // STAFFING_004 내 직종별 필요 인원 1건
    @Getter
    @NoArgsConstructor
    @AllArgsConstructor
    @Builder
    public static class TradeNeedRes {
        private Long idx;
        private Trade trade;
        private int need;
        private int filled;

        public static TradeNeedRes from(TradeNeed t, int filled) {
            return TradeNeedRes.builder()
                    .idx(t.getIdx())
                    .trade(t.getTrade())
                    .need(t.getNeed())
                    .filled(filled)
                    .build();
        }
    }

    // STAFFING_006 해당 ZoneSub 에 배치된 작업자 1행 / STAFFING_008 작업자 현황 1행
    @Getter
    @NoArgsConstructor
    @AllArgsConstructor
    @Builder
    public static class AssignedWorkerRes {
        private Long workerIdx;
        private String name;
        private AffiliationKind affiliationKind;
        /** 소속 구분 + 공종 표시 (예: "본사 / 철근공", "협력사 / 목공") */
        private String affiliationLine;
        /** 당일 명단 근태 기준 고용구분(REGULAR 상용 · DAILY 일용); STAFFING_006 에서 선택적으로 null */
        private EmploymentKind employmentKind;
        /** 피로도 점수(0–100) — 마지막 산정 스냅샷(STAFFING_008). 프로필 조회 시 갱신될 수 있다. */
        private int fatigueScore;
        // 기본구역 · 상세구역 배치 문자열 ("미투입" 허용)
        private String placement;
        private boolean assigned;
        private boolean safetyEducationCompleted;

        public static AssignedWorkerRes from(
                Worker worker,
                StaffingAssignment assignment,
                EmploymentKind rosterEmploymentKind,
                boolean safetyEducationCompleted) {
            String placementText;
            boolean isAssigned = assignment != null;
            if (assignment != null) {
                placementText = assignment.getZoneSub().getZoneMain().getTitle()
                        + " · "
                        + assignment.getZoneSub().getTitle();
            } else {
                placementText = "미투입";
            }

            String affiliationLabel = worker.getAffiliationKind() == AffiliationKind.DIRECT ? "본사" : "협력사";
            String sub = worker.getAffiliationKind() == AffiliationKind.DIRECT ? "직영" : worker.getTrade();
            String line = affiliationLabel + " / " + (sub == null ? "" : sub.trim());

            return AssignedWorkerRes.builder()
                    .workerIdx(worker.getIdx())
                    .name(worker.getName())
                    .affiliationKind(worker.getAffiliationKind())
                    .employmentKind(rosterEmploymentKind)
                    .affiliationLine(line)
                    .fatigueScore(worker.getFatigueScoreTotal())
                    .placement(placementText)
                    .assigned(isAssigned)
                    .safetyEducationCompleted(safetyEducationCompleted)
                    .build();
        }
    }

    // STAFFING_008 우측 작업자 현황 응답
    @Getter
    @NoArgsConstructor
    @AllArgsConstructor
    @Builder
    public static class WorkerPoolRes {
        private int totalCount;
        private List<AssignedWorkerRes> rows;
    }

    /** staffing_log 기준 확정 배치 근무자 1행 */
    @Getter
    @NoArgsConstructor
    @AllArgsConstructor
    @Builder
    public static class ConfirmedWorkerRes {
        private Long workerIdx;
        private String name;
        private AffiliationKind affiliationKind;
        /** "본사 / 직영" or "협력사 / 공종명" */
        private String affiliationLine;
        private String zoneMainTitle;
        private String zoneSubTitle;
        /** zoneMainTitle + " · " + zoneSubTitle */
        private String placement;
        private String tradeName;
        /** staffing_log.created_at — 가장 최근 확정 시각 */
        private LocalDateTime confirmedAt;
    }

    /** STAFFING_001 자동 추천 배치 결과 요약 */
    @Getter
    @NoArgsConstructor
    @AllArgsConstructor
    @Builder
    public static class SaveSummaryRes {
        /** 이번 호출로 새로 배치한 본사(DIRECT)·WORKER 인원 수 */
        private int assignedCount;
        /**
         * 자동 배치 대상이었으나 구역 정원 부족 등으로 미배치된 본사(DIRECT)·WORKER 인원 수
         */
        private int unassignedCount;
    }
}
