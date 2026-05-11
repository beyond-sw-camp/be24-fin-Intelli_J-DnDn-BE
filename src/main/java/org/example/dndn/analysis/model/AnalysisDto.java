package org.example.dndn.analysis.model;

import lombok.*;

import java.time.LocalDate;
import java.util.List;

public class AnalysisDto {

    // ── 공정 진척률 비교 응답 ──────────────────────────────────────────────

    @Getter @NoArgsConstructor @AllArgsConstructor @Builder
    public static class ProcessProgressRes {
        private Long tradeProcessId;
        private String tradeName;       // 대표 공종명
        private String name;            // 공정명
        private String partner;         // 협력사명
        private LocalDate plannedStart;
        private LocalDate plannedEnd;
        private LocalDate actualStart;
        private LocalDate forecastEnd;  // 예상 종료일 (실적 기반 추정)
        private Double plannedPct;      // 계획 진척률 (날짜 비율)
        private Double actualPct;       // 실제 진척률 (DailyReport 누적)
        private String actualSource;    // DAILY_REPORT | NONE
        private LocalDate latestReportDate;
        private LocalDate analysisDate;
        private Double diff;            // plannedPct - actualPct (양수 = 지연)
        private String status;          // 정상 / 주의 / 지연 위험 / 지연
        private String risk;            // 낮음 / 보통 / 높음 / 매우 높음
        private Integer actualWorkers;  // 최근 실제 투입 인원
    }

    // ── 지연 위험 작업 응답 (월간 공정 레벨) ────────────────────────────────

    @Getter @NoArgsConstructor @AllArgsConstructor @Builder
    public static class DelayRiskRes {
        private Long tradeProcessId;
        private Long workPlanId;            // 월간 WorkPlan ID
        private String process;             // 공종명
        private String name;                // 작업명
        private String location;            // 작업 위치
        private String partner;             // 협력사명
        private Double plannedPct;
        private Double actualPct;
        private Double diff;
        private Integer expectedDelayDays;  // 예상 지연일
        private String risk;
        private String cause;               // 지연 원인
        private String followEffect;        // 후속 공정 영향 (추후 확장)
        private Boolean isCritical;         // 임계 공정 여부
        private LocalDate originalEnd;
        private Integer actualWorkers;

        // ── 세부 작업 (WEEKLY WorkPlan) ──────────────────────────────────
        private List<DelayRiskDetailRes> weeklyItems;
    }

    // ── 지연 위험 세부 작업 응답 (주간/일별 WorkPlan 레벨) ──────────────────

    @Getter @NoArgsConstructor @AllArgsConstructor @Builder
    public static class DelayRiskDetailRes {
        private Long workPlanId;            // WEEKLY WorkPlan ID
        private Long tradeProcessId;
        private String process;             // 대표 공종명
        private String tradeName;           // 원본 공종명
        private String name;                // 세부 작업명
        private String location;            // 작업 구역
        private String partner;             // 협력사명
        private LocalDate date;             // 작업일 (startDate = endDate)
        private LocalDate plannedStart;
        private LocalDate plannedEnd;
        private LocalDate originalEnd;      // 원래 종료일
        private LocalDate effectiveEnd;     // 연장 포함 종료일
        private Double plannedPct;
        private Double actualPct;
        private String actualSource;    // DAILY_REPORT | NONE
        private LocalDate latestReportDate;
        private Long dailyReportId;
        private LocalDate analysisDate;
        private Double diff;
        private String status;
        private String risk;
        private Integer expectedDelayDays;
        private String cause;               // 최근 DailyReport.issue
        private String followEffect;
        private Boolean isCritical;
        private String workersDisplay;      // "전공 4명, 보통공 2명"
        private String equipmentDisplay;    // "타워크레인 1대"
        private Integer actualWorkers;      // 실제 투입 인원
        private Boolean hasReport;          // DailyReport 제출 여부
    }
}
