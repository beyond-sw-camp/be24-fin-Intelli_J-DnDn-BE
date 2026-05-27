package org.example.dndncore.analysis.model;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.*;

import java.time.LocalDate;
import java.util.List;

public class AnalysisDto {

    // ── 공정 진척률 비교 응답 ──────────────────────────────────────────────

    @Getter @NoArgsConstructor @AllArgsConstructor @Builder
    @Schema(description = "공정 진척률 비교 응답")
    public static class ProcessProgressRes {
        @Schema(description = "공정 단계 ID", example = "1001")
        private Long tradeProcessId;
        @Schema(description = "대표 공종명", example = "철근콘크리트공사")
        private String tradeName;       // 대표 공종명
        @Schema(description = "공정명", example = "기초 철근 배근")
        private String name;            // 공정명
        @Schema(description = "협력사명", example = "동서건설")
        private String partner;         // 협력사명
        @Schema(description = "계획 시작일", example = "2026-05-01")
        private LocalDate plannedStart;
        @Schema(description = "계획 종료일", example = "2026-05-15")
        private LocalDate plannedEnd;
        @Schema(description = "실제 시작일", example = "2026-05-02")
        private LocalDate actualStart;
        @Schema(description = "예상 종료일", example = "2026-05-18")
        private LocalDate forecastEnd;  // 예상 종료일 (실적 기반 추정)
        @Schema(description = "계획 진척률", example = "45.0")
        private Double plannedPct;      // 계획 진척률 (날짜 비율)
        @Schema(description = "실제 진척률", example = "30.0")
        private Double actualPct;       // 실제 진척률 (DailyReport 누적)
        @Schema(description = "실제 진척률 산출 근거", example = "DAILY_REPORT")
        private String actualSource;    // DAILY_REPORT | NONE
        @Schema(description = "최신 보고 일자", example = "2026-05-10")
        private LocalDate latestReportDate;
        @Schema(description = "분석 기준일", example = "2026-05-27")
        private LocalDate analysisDate;
        @Schema(description = "진척률 차이", example = "15.0")
        private Double diff;            // plannedPct - actualPct (양수 = 지연)
        @Schema(description = "상태", example = "지연 위험")
        private String status;          // 정상 / 주의 / 지연 위험 / 지연
        @Schema(description = "위험도", example = "높음")
        private String risk;            // 낮음 / 보통 / 높음 / 매우 높음
        @Schema(description = "실제 투입 인원", example = "8")
        private Integer actualWorkers;  // 최근 실제 투입 인원
    }

    // ── 지연 위험 작업 응답 (월간 공정 레벨) ────────────────────────────────

    @Getter @NoArgsConstructor @AllArgsConstructor @Builder
    @Schema(description = "지연 위험 작업 응답")
    public static class DelayRiskRes {
        @Schema(description = "공정 단계 ID", example = "1001")
        private Long tradeProcessId;
        @Schema(description = "월간 작업계획 ID", example = "2001")
        private Long workPlanId;            // 월간 WorkPlan ID
        @Schema(description = "공정명", example = "철근콘크리트공사")
        private String process;             // 공종명
        @Schema(description = "작업명", example = "기초 철근 배근")
        private String name;                // 작업명
        @Schema(description = "작업 위치", example = "B1 구역")
        private String location;            // 작업 위치
        @Schema(description = "협력사명", example = "동서건설")
        private String partner;             // 협력사명
        @Schema(description = "계획 진척률", example = "45.0")
        private Double plannedPct;
        @Schema(description = "실제 진척률", example = "30.0")
        private Double actualPct;
        @Schema(description = "진척률 차이", example = "15.0")
        private Double diff;
        @Schema(description = "예상 지연일", example = "3")
        private Integer expectedDelayDays;  // 예상 지연일
        @Schema(description = "위험도", example = "높음")
        private String risk;
        @Schema(description = "지연 원인", example = "자재 수급 지연")
        private String cause;               // 지연 원인
        @Schema(description = "후속 공정 영향", example = "다음 공정 착수 지연 가능")
        private String followEffect;        // 후속 공정 영향 (추후 확장)
        @Schema(description = "임계 공정 여부", example = "true")
        private Boolean isCritical;         // 임계 공정 여부
        @Schema(description = "원래 종료일", example = "2026-05-15")
        private LocalDate originalEnd;
        @Schema(description = "실제 투입 인원", example = "8")
        private Integer actualWorkers;

        @Schema(description = "세부 작업 목록")
        private List<DelayRiskDetailRes> weeklyItems;
    }

    // ── 지연 위험 세부 작업 응답 (주간/일별 WorkPlan 레벨) ──────────────────

    @Getter @NoArgsConstructor @AllArgsConstructor @Builder
    @Schema(description = "지연 위험 세부 작업 응답")
    public static class DelayRiskDetailRes {
        @Schema(description = "주간 작업계획 ID", example = "3001")
        private Long workPlanId;            // WEEKLY WorkPlan ID
        @Schema(description = "공정 단계 ID", example = "1001")
        private Long tradeProcessId;
        @Schema(description = "대표 공정명", example = "철근콘크리트공사")
        private String process;             // 대표 공종명
        @Schema(description = "원본 공종명", example = "철근콘크리트")
        private String tradeName;           // 원본 공종명
        @Schema(description = "세부 작업명", example = "기초 철근 배근 1차")
        private String name;                // 세부 작업명
        @Schema(description = "작업 구역", example = "B1 구역")
        private String location;            // 작업 구역
        @Schema(description = "협력사명", example = "동서건설")
        private String partner;             // 협력사명
        @Schema(description = "작업일", example = "2026-05-10")
        private LocalDate date;             // 작업일 (startDate = endDate)
        @Schema(description = "계획 시작일", example = "2026-05-08")
        private LocalDate plannedStart;
        @Schema(description = "계획 종료일", example = "2026-05-10")
        private LocalDate plannedEnd;
        @Schema(description = "원래 종료일", example = "2026-05-10")
        private LocalDate originalEnd;      // 원래 종료일
        @Schema(description = "유효 종료일", example = "2026-05-12")
        private LocalDate effectiveEnd;     // 연장 포함 종료일
        @Schema(description = "계획 진척률", example = "45.0")
        private Double plannedPct;
        @Schema(description = "실제 진척률", example = "30.0")
        private Double actualPct;
        @Schema(description = "실제 진척률 산출 근거", example = "DAILY_REPORT")
        private String actualSource;    // DAILY_REPORT | NONE
        @Schema(description = "최신 보고 일자", example = "2026-05-10")
        private LocalDate latestReportDate;
        @Schema(description = "일일 보고 ID", example = "4001")
        private Long dailyReportId;
        @Schema(description = "분석 기준일", example = "2026-05-27")
        private LocalDate analysisDate;
        @Schema(description = "진척률 차이", example = "15.0")
        private Double diff;
        @Schema(description = "상태", example = "지연 위험")
        private String status;
        @Schema(description = "위험도", example = "높음")
        private String risk;
        @Schema(description = "예상 지연일", example = "3")
        private Integer expectedDelayDays;
        @Schema(description = "지연 원인", example = "자재 수급 지연")
        private String cause;               // 최근 DailyReport.issue
        @Schema(description = "후속 공정 영향", example = "다음 공정 착수 지연 가능")
        private String followEffect;
        @Schema(description = "임계 공정 여부", example = "true")
        private Boolean isCritical;
        @Schema(description = "투입 인원 표시", example = "전공 4명, 보통공 2명")
        private String workersDisplay;      // "전공 4명, 보통공 2명"
        @Schema(description = "장비 표시", example = "타워크레인 1대")
        private String equipmentDisplay;    // "타워크레인 1대"
        @Schema(description = "실제 투입 인원", example = "8")
        private Integer actualWorkers;      // 실제 투입 인원
        @Schema(description = "보고서 존재 여부", example = "true")
        private Boolean hasReport;          // DailyReport 제출 여부
    }
}
