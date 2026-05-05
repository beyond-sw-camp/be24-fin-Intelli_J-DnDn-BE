package org.example.dndn.staffing.model;

import lombok.*;
import org.example.dndn.worker.model.entity.Worker;
import org.example.dndn.worker.model.enums.AffiliationKind;

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

    // STAFFING_003 — 기본 구역 정보 조회 응답
    @Getter
    @NoArgsConstructor
    @AllArgsConstructor
    @Builder
    public static class ZoneMainRes {
        private Long idx;
        private String title;
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
        private String title;
        private int required;
        private int assignedCount;

        public static ZoneSubSummaryRes from(ZoneSub zs) {
            return ZoneSubSummaryRes.builder()
                    .idx(zs.getIdx())
                    .title(zs.getTitle())
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
        private String title;
        private int required;
        private int assignedCount;
        private List<TradeNeedRes> tradeNeeds;

        // 직종별 현재 해당 ZoneSub 에 투입된 인원 수(마스터 공종으로 분류)
        public static ZoneSubRes from(ZoneSub zs, Map<Trade, Integer> filledByTrade) {
            return ZoneSubRes.builder()
                    .idx(zs.getIdx())
                    .zoneMainIdx(zs.getZoneMain().getIdx())
                    .title(zs.getTitle())
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

    // STAFFING_006 해당 ZoneSub 에 배치된 작업자 1행
    @Getter
    @NoArgsConstructor
    @AllArgsConstructor
    @Builder
    public static class AssignedWorkerRes {
        private Long workerIdx;
        private String name;
        private AffiliationKind affiliationKind;
        private String partnerCompany;
        private String affiliationLine;
        private int fatigueScore;
        // 기본구역 · 상세구역 배치 문자열
        private String placement;
        private boolean assigned;

        public static AssignedWorkerRes from(Worker worker, StaffingAssignment assignment) {
            String placementText = assignment.getZoneSub().getZoneMain().getTitle()
                    + " · "
                    + assignment.getZoneSub().getTitle();

            boolean direct = worker.getAffiliationKind() == AffiliationKind.DIRECT;
            String companyLabel = direct
                    ? "본사"
                    : (worker.getPartnerCompany() != null && !worker.getPartnerCompany().isBlank()
                            ? worker.getPartnerCompany()
                            : "협력사");
            String sub = worker.getSubLabel();
            String line = companyLabel + " / " + (sub == null ? "" : sub.trim());

            return AssignedWorkerRes.builder()
                    .workerIdx(worker.getIdx())
                    .name(worker.getName())
                    .affiliationKind(worker.getAffiliationKind())
                    .partnerCompany(worker.getPartnerCompany())
                    .affiliationLine(line)
                    .fatigueScore(0)
                    .placement(placementText)
                    .assigned(true)
                    .build();
        }
    }
}
