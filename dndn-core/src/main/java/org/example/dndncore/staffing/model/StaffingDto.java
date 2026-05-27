package org.example.dndncore.staffing.model;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.*;
import org.example.dndncore.worker.model.entity.Worker;
import org.example.dndncore.worker.model.enums.AffiliationKind;
import org.example.dndncore.worker.model.enums.EmploymentKind;

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
    @Schema(description = "상세 구역 수정 요청 DTO")
    public static class ZoneUpdateReq {
        @Schema(description = "상세 구역 제목", example = "A동 3층 거푸집")
        private String title;
        @Schema(description = "직종별 필요 인원 목록")
        private List<TradeNeedReq> tradeNeeds;
    }

    // STAFFING_005 필요 직종 1건 (동일 trade 중복 요청 시 need 합산)
    @Getter
    @Setter
    @NoArgsConstructor
    @AllArgsConstructor
    @Builder
    @Schema(description = "직종별 필요 인원 요청 DTO")
    public static class TradeNeedReq {
        @Schema(description = "직종", example = "CARPENTER")
        private Trade trade;
        @Schema(description = "필요 인원 수", example = "3")
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
    @Schema(description = "작업자 수동 배치 요청 DTO")
    public static class AssignReq {
        @Schema(description = "상세 구역 ID", example = "10")
        private Long subZoneIdx;
        @Schema(description = "배치할 작업자 ID 목록", example = "[101,102]")
        private List<Long> workerIds;
    }

    // STAFFING_008 — 우측 패널 검색 (소속 DIRECT/PARTNER, 이름·협력사 키워드, 미배치만)
    @Getter
    @Setter
    @NoArgsConstructor
    @AllArgsConstructor
    @Builder
    @Schema(description = "작업자 현황 검색 조건 DTO")
    public static class PoolSearchReq {
        @Schema(description = "현장 코드", example = "SITE-001")
        private String siteCode;
        /** null 이면 소속 필터 없음 */
        @Schema(description = "소속 구분", example = "DIRECT", nullable = true)
        private AffiliationKind affiliationKind;
        @Schema(description = "이름/협력사 검색어", example = "김")
        private String keyword;
        /** true 이면 배치(staffing_assignment) 없는 명단만 */
        @Schema(description = "미배치 작업자만 조회 여부", example = "false")
        private boolean unassignedOnly;
    }

    /** STAFFING board — 상세 구역 1건 (요약 + 직종별 필요 + 배치 작업자) */
    @Getter
    @NoArgsConstructor
    @AllArgsConstructor
    @Builder
    @Schema(description = "상세 구역 보드 응답 DTO")
    public static class ZoneSubBoardRes {
        @Schema(description = "상세 구역 ID", example = "10")
        private Long idx;
        @Schema(description = "작업 계획 ID", example = "1001")
        private Long workPlanId;
        @Schema(description = "상세 구역명", example = "A동 3층 거푸집")
        private String title;
        @Schema(description = "작업 위치", example = "A동 3층")
        private String location;
        @Schema(description = "공종명", example = "철근")
        private String tradeName;
        @Schema(description = "작업 시간", example = "08:00-17:00")
        private String workTime;
        @Schema(description = "작업 일자", example = "2026-05-27")
        private LocalDate workDate;
        @Schema(description = "필요 인원", example = "8")
        private int required;
        @Schema(description = "배치 인원", example = "6")
        private int assignedCount;
        @Schema(description = "직종별 필요 인원 목록")
        private List<TradeNeedRes> tradeNeeds;
        @Schema(description = "배치된 작업자 목록")
        private List<AssignedWorkerRes> workers;
    }

    /** STAFFING board — 기본 구역 + 상세 구역 전체 */
    @Getter
    @NoArgsConstructor
    @AllArgsConstructor
    @Builder
    @Schema(description = "기본 구역 보드 응답 DTO")
    public static class ZoneMainBoardRes {
        @Schema(description = "기본 구역 ID", example = "1")
        private Long idx;
        @Schema(description = "기본 구역명", example = "골조 공정")
        private String title;
        @Schema(description = "데이터 소스", example = "WORK_PLAN")
        private String source;
        @Schema(description = "총 배치 인원", example = "16")
        private int totalAssigned;
        @Schema(description = "총 필요 인원", example = "20")
        private int totalRequired;
        @Schema(description = "하위 상세 구역 목록")
        private List<ZoneSubBoardRes> subZones;
    }

    /** STAFFING board — 인력 배치 보드 좌측 구역 트리 일괄 응답 */
    @Getter
    @NoArgsConstructor
    @AllArgsConstructor
    @Builder
    @Schema(description = "인력 배치 보드 응답 DTO")
    public static class BoardRes {
        @Schema(description = "기본 구역 목록")
        private List<ZoneMainBoardRes> zoneMains;
    }

    // STAFFING_003 — 기본 구역 정보 조회 응답
    @Getter
    @NoArgsConstructor
    @AllArgsConstructor
    @Builder
    @Schema(description = "기본 구역 조회 응답 DTO")
    public static class ZoneMainRes {
        @Schema(description = "기본 구역 ID", example = "1")
        private Long idx;
        @Schema(description = "기본 구역명", example = "골조 공정")
        private String title;
        @Schema(description = "데이터 소스", example = "WORK_PLAN")
        private String source;
        @Schema(description = "총 배치 인원", example = "16")
        private int totalAssigned;
        @Schema(description = "총 필요 인원", example = "20")
        private int totalRequired;
        @Schema(description = "상세 구역 요약 목록")
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
    @Schema(description = "상세 구역 요약 DTO")
    public static class ZoneSubSummaryRes {
        @Schema(description = "상세 구역 ID", example = "10")
        private Long idx;
        @Schema(description = "작업 계획 ID", example = "1001")
        private Long workPlanId;
        @Schema(description = "상세 구역명", example = "A동 3층 거푸집")
        private String title;
        @Schema(description = "작업 위치", example = "A동 3층")
        private String location;
        @Schema(description = "공종명", example = "철근")
        private String tradeName;
        @Schema(description = "작업 시간", example = "08:00-17:00")
        private String workTime;
        @Schema(description = "작업 일자", example = "2026-05-27")
        private LocalDate workDate;
        @Schema(description = "필요 인원", example = "8")
        private int required;
        @Schema(description = "배치 인원", example = "6")
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
    @Schema(description = "상세 구역 상세 응답 DTO")
    public static class ZoneSubRes {
        @Schema(description = "상세 구역 ID", example = "10")
        private Long idx;
        @Schema(description = "기본 구역 ID", example = "1")
        private Long zoneMainIdx;
        @Schema(description = "작업 계획 ID", example = "1001")
        private Long workPlanId;
        @Schema(description = "상세 구역명", example = "A동 3층 거푸집")
        private String title;
        @Schema(description = "작업 위치", example = "A동 3층")
        private String location;
        @Schema(description = "공종명", example = "철근")
        private String tradeName;
        @Schema(description = "작업 시간", example = "08:00-17:00")
        private String workTime;
        @Schema(description = "작업 일자", example = "2026-05-27")
        private LocalDate workDate;
        @Schema(description = "필요 인원", example = "8")
        private int required;
        @Schema(description = "배치 인원", example = "6")
        private int assignedCount;
        @Schema(description = "직종별 필요/충원 정보")
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
    @Schema(description = "직종별 필요/충원 응답 DTO")
    public static class TradeNeedRes {
        @Schema(description = "직종 필요 인원 ID", example = "1")
        private Long idx;
        @Schema(description = "직종", example = "CARPENTER")
        private Trade trade;
        @Schema(description = "필요 인원 수", example = "3")
        private int need;
        @Schema(description = "현재 충원 인원 수", example = "2")
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
    @Schema(description = "작업자 배치/현황 행 DTO")
    public static class AssignedWorkerRes {
        @Schema(description = "작업자 ID", example = "101")
        private Long workerIdx;
        @Schema(description = "작업자 이름", example = "홍길동")
        private String name;
        @Schema(description = "소속 구분", example = "DIRECT")
        private AffiliationKind affiliationKind;
        /** 소속 구분 + 공종 표시 (예: "본사 / 철근공", "협력사 / 목공") */
        @Schema(description = "소속 및 공종 표시", example = "본사 / 직영")
        private String affiliationLine;
        /** 당일 명단 근태 기준 고용구분(REGULAR 상용 · DAILY 일용); STAFFING_006 에서 선택적으로 null */
        @Schema(description = "고용 구분", example = "REGULAR", nullable = true)
        private EmploymentKind employmentKind;
        /** 피로도 점수(0–100) — 마지막 산정 스냅샷(STAFFING_008). 프로필 조회 시 갱신될 수 있다. */
        @Schema(description = "피로도 점수", example = "42")
        private int fatigueScore;
        // 기본구역 · 상세구역 배치 문자열 ("미투입" 허용)
        @Schema(description = "배치 위치 텍스트", example = "골조 공정 · A동 3층 거푸집")
        private String placement;
        @Schema(description = "배치 여부", example = "true")
        private boolean assigned;
        @Schema(description = "안전교육 이수 여부", example = "true")
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
    @Schema(description = "작업자 현황 응답 DTO")
    public static class WorkerPoolRes {
        @Schema(description = "총 조회 건수", example = "24")
        private int totalCount;
        @Schema(description = "작업자 행 목록")
        private List<AssignedWorkerRes> rows;
    }

    /** staffing_log 기준 확정 배치 근무자 1행 */
    @Getter
    @NoArgsConstructor
    @AllArgsConstructor
    @Builder
    @Schema(description = "확정 배치 근무자 응답 DTO")
    public static class ConfirmedWorkerRes {
        @Schema(description = "작업자 ID", example = "101")
        private Long workerIdx;
        @Schema(description = "작업자 이름", example = "홍길동")
        private String name;
        @Schema(description = "소속 구분", example = "DIRECT")
        private AffiliationKind affiliationKind;
        /** "본사 / 직영" or "협력사 / 공종명" */
        @Schema(description = "소속 및 공종 표시", example = "본사 / 직영")
        private String affiliationLine;
        @Schema(description = "기본 구역명", example = "골조 공정")
        private String zoneMainTitle;
        @Schema(description = "상세 구역명", example = "A동 3층 거푸집")
        private String zoneSubTitle;
        /** zoneMainTitle + " · " + zoneSubTitle */
        @Schema(description = "배치 위치 텍스트", example = "골조 공정 · A동 3층 거푸집")
        private String placement;
        @Schema(description = "공종명", example = "철근")
        private String tradeName;
        /** staffing_log.created_at — 가장 최근 확정 시각 */
        @Schema(description = "최근 확정 시각", example = "2026-05-27T09:30:00")
        private LocalDateTime confirmedAt;
    }

    /** STAFFING_001 자동 추천 배치 결과 요약 */
    @Getter
    @NoArgsConstructor
    @AllArgsConstructor
    @Builder
    @Schema(description = "자동 추천/최종 저장 결과 요약 DTO")
    public static class SaveSummaryRes {
        /** 이번 호출로 새로 배치한 본사(DIRECT)·WORKER 인원 수 */
        @Schema(description = "새로 배치된 인원 수", example = "12")
        private int assignedCount;
        /**
         * 자동 배치 대상이었으나 구역 정원 부족 등으로 미배치된 본사(DIRECT)·WORKER 인원 수
         */
        @Schema(description = "미배치 인원 수", example = "3")
        private int unassignedCount;
    }
}
