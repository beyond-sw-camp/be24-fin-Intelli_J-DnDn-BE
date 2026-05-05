package org.example.dndn.staffing.model;

import lombok.*;

import java.util.List;

public class StaffingDto {

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
}
