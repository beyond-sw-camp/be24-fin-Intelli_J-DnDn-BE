package org.example.dndncore.esg.model;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.example.dndncore.project.model.entity.Project;

import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class EsgDashboardDto {

    private static final Pattern SITE_CODE_PATTERN = Pattern.compile("^\\s*\\[([^\\]]+)]\\s*(.*)$");

    @Getter
    @Setter
    @NoArgsConstructor
    @AllArgsConstructor
    @Builder
    @Schema(description = "ESG 스냅샷 저장 요청")
    public static class SaveSnapshotRequestDto {
        @Schema(description = "프로젝트 ID", example = "1")
        @NotNull(message = "projectId is required")
        private Long projectId;

        @Schema(description = "보고일", example = "2026-05-27")
        @NotNull(message = "reportDate is required")
        private LocalDate reportDate;

        @Schema(description = "환경 점수", example = "82.5")
        @NotNull(message = "environmentScore is required")
        private Double environmentScore;

        @Schema(description = "사회 점수", example = "78.2")
        @NotNull(message = "socialScore is required")
        private Double socialScore;

        @Schema(description = "지배구조 점수", example = "80.1")
        @NotNull(message = "governanceScore is required")
        private Double governanceScore;

        @Schema(description = "총점", example = "80.3")
        @NotNull(message = "totalScore is required")
        private Double totalScore;

        @Schema(description = "등급 레벨", example = "3")
        @NotNull(message = "level is required")
        private Integer level;

        @Schema(description = "탄소 배출량(kg)", example = "123.4")
        private Double carbonKg;
        @Schema(description = "절감 전력량(kWh)", example = "45.6")
        private Double powerSavingKwh;
        @Schema(description = "리스크 건수", example = "2")
        private Integer riskCount;
        @Schema(description = "미션 달성률", example = "85")
        private Integer missionRate;
        @Schema(description = "무재해 일수", example = "120")
        private Integer safetyDays;
        @Schema(description = "구역 수", example = "6")
        private Integer zoneCount;
        @Schema(description = "스냅샷 원본 JSON")
        private String snapshotJson;

        @Schema(description = "구역별 스냅샷 목록")
        @Valid
        private List<SaveZoneSnapshotRequestDto> zones;
    }

    @Getter
    @Setter
    @NoArgsConstructor
    @AllArgsConstructor
    @Builder
    @Schema(description = "구역별 ESG 스냅샷 저장 요청")
    public static class SaveZoneSnapshotRequestDto {
        @Schema(description = "구역명", example = "A구역")
        private String zoneName;
        @Schema(description = "구역 유형", example = "OUTDOOR")
        private String zoneType;
        @Schema(description = "환경 점수", example = "82.5")
        private Double environmentScore;
        @Schema(description = "사회 점수", example = "78.2")
        private Double socialScore;
        @Schema(description = "지배구조 점수", example = "80.1")
        private Double governanceScore;
        @Schema(description = "총점", example = "80.3")
        private Double totalScore;
        @Schema(description = "등급 레벨", example = "3")
        private Integer level;
        @Schema(description = "탄소 배출량(kg)", example = "30.2")
        private Double carbonKg;
        @Schema(description = "절감 전력량(kWh)", example = "11.4")
        private Double powerSavingKwh;
        @Schema(description = "리스크 건수", example = "1")
        private Integer riskCount;
        @Schema(description = "미션 달성률", example = "88")
        private Integer missionRate;
        @Schema(description = "장비 수", example = "15")
        private Integer equipmentCount;
        @Schema(description = "고위험 장비 수", example = "2")
        private Integer highRiskEquipmentCount;
        @Schema(description = "기여 가중치", example = "0.25")
        private Double contributionWeight;
        @Schema(description = "기여 점수", example = "20.1")
        private Double contributionScore;
        @Schema(description = "스냅샷 원본 JSON")
        private String snapshotJson;
    }

    @Getter
    @NoArgsConstructor
    @AllArgsConstructor
    @Builder
    @Schema(description = "ESG 대시보드 응답")
    public static class DashboardResponseDto {
        @Schema(description = "현재 프로젝트")
        private ProjectResponseDto currentProject;
        @Schema(description = "현재 스냅샷")
        private SnapshotResponseDto currentSnapshot;
        @Schema(description = "현재 구역별 스냅샷")
        private List<ZoneSnapshotResponseDto> currentZoneSnapshots;
        @Schema(description = "현재 지표 입력")
        private List<MetricInputResponseDto> currentMetricInputs;
        @Schema(description = "프로젝트 목록")
        private List<ProjectResponseDto> projects;
        @Schema(description = "랭킹 목록")
        private List<RankingResponseDto> rankings;
    }

    @Getter
    @NoArgsConstructor
    @AllArgsConstructor
    @Builder
    @Schema(description = "프로젝트 정보 응답")
    public static class ProjectResponseDto {
        @Schema(description = "프로젝트 ID", example = "1")
        private Long idx;
        @Schema(description = "프로젝트 코드", example = "PJ-001")
        private String code;
        @Schema(description = "프로젝트명", example = "OO현장")
        private String name;
        @Schema(description = "프로젝트 축약명", example = "OO현장")
        private String shortName;
        @Schema(description = "위치", example = "서울")
        private String location;
        @Schema(description = "시작일", example = "2026-01-01")
        private LocalDate startDate;
        @Schema(description = "종료일", example = "2026-12-31")
        private LocalDate endDate;
        @Schema(description = "무재해 일수", example = "120")
        private Integer safetyDays;

        public static ProjectResponseDto from(Project project, LocalDate reportDate) {
            ParsedProjectName parsed = parseProjectName(project.getName());
            return ProjectResponseDto.builder()
                    .idx(project.getIdx())
                    .code(parsed.code())
                    .name(parsed.displayName())
                    .shortName(parsed.displayName())
                    .location(project.getLocation())
                    .startDate(project.getStartDate())
                    .endDate(project.getEndDate())
                    .safetyDays(calculateSafetyDays(project.getStartDate(), reportDate))
                    .build();
        }
    }

    @Getter
    @NoArgsConstructor
    @AllArgsConstructor
    @Builder
    @Schema(description = "일별 ESG 스냅샷 응답")
    public static class SnapshotResponseDto {
        @Schema(description = "스냅샷 ID", example = "1")
        private Long idx;
        @Schema(description = "프로젝트 ID", example = "1")
        private Long projectId;
        @Schema(description = "보고일", example = "2026-05-27")
        private LocalDate reportDate;
        @Schema(description = "환경 점수", example = "82.5")
        private Double environmentScore;
        @Schema(description = "사회 점수", example = "78.2")
        private Double socialScore;
        @Schema(description = "지배구조 점수", example = "80.1")
        private Double governanceScore;
        @Schema(description = "총점", example = "80.3")
        private Double totalScore;
        @Schema(description = "등급 레벨", example = "3")
        private Integer level;
        @Schema(description = "탄소 배출량(kg)", example = "123.4")
        private Double carbonKg;
        @Schema(description = "절감 전력량(kWh)", example = "45.6")
        private Double powerSavingKwh;
        @Schema(description = "리스크 건수", example = "2")
        private Integer riskCount;
        @Schema(description = "미션 달성률", example = "85")
        private Integer missionRate;
        @Schema(description = "무재해 일수", example = "120")
        private Integer safetyDays;
        @Schema(description = "구역 수", example = "6")
        private Integer zoneCount;
        @Schema(description = "스냅샷 원본 JSON")
        private String snapshotJson;
        @Schema(description = "스냅샷 저장 여부", example = "true")
        private Boolean snapshotSaved;
        @Schema(description = "구역별 스냅샷 목록")
        private List<ZoneSnapshotResponseDto> zones;

        public static SnapshotResponseDto from(EsgDailySnapshot snapshot) {
            return from(snapshot, List.of());
        }

        public static SnapshotResponseDto from(EsgDailySnapshot snapshot, List<EsgZoneDailySnapshot> zones) {
            if (snapshot == null) {
                return null;
            }
            return SnapshotResponseDto.builder()
                    .idx(snapshot.getIdx())
                    .projectId(snapshot.getProject().getIdx())
                    .reportDate(snapshot.getReportDate())
                    .environmentScore(snapshot.getEnvironmentScore())
                    .socialScore(snapshot.getSocialScore())
                    .governanceScore(snapshot.getGovernanceScore())
                    .totalScore(snapshot.getTotalScore())
                    .level(snapshot.getLevel())
                    .carbonKg(snapshot.getCarbonKg())
                    .powerSavingKwh(snapshot.getPowerSavingKwh())
                    .riskCount(snapshot.getRiskCount())
                    .missionRate(snapshot.getMissionRate())
                    .safetyDays(snapshot.getSafetyDays())
                    .zoneCount(snapshot.getZoneCount())
                    .snapshotJson(snapshot.getSnapshotJson())
                    .snapshotSaved(true)
                    .zones(zones == null ? List.of() : zones.stream()
                            .map(ZoneSnapshotResponseDto::from)
                            .toList())
                    .build();
        }
    }

    @Getter
    @NoArgsConstructor
    @AllArgsConstructor
    @Builder
    @Schema(description = "구역별 ESG 스냅샷 응답")
    public static class ZoneSnapshotResponseDto {
        @Schema(description = "구역 스냅샷 ID", example = "1")
        private Long idx;
        @Schema(description = "프로젝트 ID", example = "1")
        private Long projectId;
        @Schema(description = "보고일", example = "2026-05-27")
        private LocalDate reportDate;
        @Schema(description = "구역명", example = "A구역")
        private String zoneName;
        @Schema(description = "구역 유형", example = "OUTDOOR")
        private String zoneType;
        @Schema(description = "환경 점수", example = "82.5")
        private Double environmentScore;
        @Schema(description = "사회 점수", example = "78.2")
        private Double socialScore;
        @Schema(description = "지배구조 점수", example = "80.1")
        private Double governanceScore;
        @Schema(description = "총점", example = "80.3")
        private Double totalScore;
        @Schema(description = "등급 레벨", example = "3")
        private Integer level;
        @Schema(description = "탄소 배출량(kg)", example = "30.2")
        private Double carbonKg;
        @Schema(description = "절감 전력량(kWh)", example = "11.4")
        private Double powerSavingKwh;
        @Schema(description = "리스크 건수", example = "1")
        private Integer riskCount;
        @Schema(description = "미션 달성률", example = "88")
        private Integer missionRate;
        @Schema(description = "장비 수", example = "15")
        private Integer equipmentCount;
        @Schema(description = "고위험 장비 수", example = "2")
        private Integer highRiskEquipmentCount;
        @Schema(description = "기여 가중치", example = "0.25")
        private Double contributionWeight;
        @Schema(description = "기여 점수", example = "20.1")
        private Double contributionScore;
        @Schema(description = "스냅샷 원본 JSON")
        private String snapshotJson;

        public static ZoneSnapshotResponseDto from(EsgZoneDailySnapshot snapshot) {
            return ZoneSnapshotResponseDto.builder()
                    .idx(snapshot.getIdx())
                    .projectId(snapshot.getProject().getIdx())
                    .reportDate(snapshot.getReportDate())
                    .zoneName(snapshot.getZoneName())
                    .zoneType(snapshot.getZoneType())
                    .environmentScore(snapshot.getEnvironmentScore())
                    .socialScore(snapshot.getSocialScore())
                    .governanceScore(snapshot.getGovernanceScore())
                    .totalScore(snapshot.getTotalScore())
                    .level(snapshot.getLevel())
                    .carbonKg(snapshot.getCarbonKg())
                    .powerSavingKwh(snapshot.getPowerSavingKwh())
                    .riskCount(snapshot.getRiskCount())
                    .missionRate(snapshot.getMissionRate())
                    .equipmentCount(snapshot.getEquipmentCount())
                    .highRiskEquipmentCount(snapshot.getHighRiskEquipmentCount())
                    .contributionWeight(snapshot.getContributionWeight())
                    .contributionScore(snapshot.getContributionScore())
                    .snapshotJson(snapshot.getSnapshotJson())
                    .build();
        }
    }


    @Getter
    @NoArgsConstructor
    @AllArgsConstructor
    @Builder
    @Schema(description = "ESG 지표 입력 응답")
    public static class MetricInputResponseDto {
        @Schema(description = "지표 입력 ID", example = "1")
        private Long idx;
        @Schema(description = "프로젝트 ID", example = "1")
        private Long projectId;
        @Schema(description = "보고일", example = "2026-05-27")
        private LocalDate reportDate;
        @Schema(description = "구역명", example = "A구역")
        private String zoneName;
        @Schema(description = "탄소 배출량(kg)", example = "123.4")
        private Double carbonKg;
        @Schema(description = "전력 사용량(kWh)", example = "456.7")
        private Double powerUsageKwh;
        @Schema(description = "절감 전력량(kWh)", example = "45.6")
        private Double powerSavingKwh;
        @Schema(description = "세척수 사용량(L)", example = "1200")
        private Double washWaterLiters;
        @Schema(description = "폐수량(L)", example = "900")
        private Double wastewaterLiters;
        @Schema(description = "폐수 회수율", example = "75.0")
        private Double wastewaterRecoveryRate;
        @Schema(description = "미세먼지 값", example = "36.5")
        private Double fineDustValue;
        @Schema(description = "소음(dB)", example = "58.2")
        private Double noiseDb;
        @Schema(description = "민원 건수", example = "1")
        private Integer complaintCount;
        @Schema(description = "민원 처리 건수", example = "1")
        private Integer complaintResolvedCount;
        @Schema(description = "안전교육 이수율", example = "92.0")
        private Double safetyEducationRate;
        @Schema(description = "인력 충원율", example = "88.0")
        private Double staffingRate;
        @Schema(description = "보고율", example = "95.0")
        private Double reportRate;
        @Schema(description = "조치 추적율", example = "90.0")
        private Double actionTrackingRate;
        @Schema(description = "데이터 연계율", example = "85.0")
        private Double dataLinkRate;
        @Schema(description = "메모")
        private String memo;

        public static MetricInputResponseDto from(EsgMetricInput input) {
            return from(input, null);
        }

        public static MetricInputResponseDto from(EsgMetricInput input, Double storedFineDustValue) {
            Double fineDustValue = storedFineDustValue != null ? storedFineDustValue : input.getFineDustValue();

            return MetricInputResponseDto.builder()
                    .idx(input.getIdx())
                    .projectId(input.getProject().getIdx())
                    .reportDate(input.getReportDate())
                    .zoneName(input.getZoneName())
                    .carbonKg(input.getCarbonKg())
                    .powerUsageKwh(input.getPowerUsageKwh())
                    .powerSavingKwh(input.getPowerSavingKwh())
                    .washWaterLiters(input.getWashWaterLiters())
                    .wastewaterLiters(input.getWastewaterLiters())
                    .wastewaterRecoveryRate(input.getWastewaterRecoveryRate())
                    .fineDustValue(fineDustValue)
                    .noiseDb(input.getNoiseDb())
                    .complaintCount(input.getComplaintCount())
                    .complaintResolvedCount(input.getComplaintResolvedCount())
                    .safetyEducationRate(input.getSafetyEducationRate())
                    .staffingRate(input.getStaffingRate())
                    .reportRate(input.getReportRate())
                    .actionTrackingRate(input.getActionTrackingRate())
                    .dataLinkRate(input.getDataLinkRate())
                    .memo(input.getMemo())
                    .build();
        }
    }

    @Getter
    @NoArgsConstructor
    @AllArgsConstructor
    @Builder
    @Schema(description = "ESG 랭킹 응답")
    public static class RankingResponseDto {
        @Schema(description = "프로젝트 ID", example = "1")
        private Long projectId;
        @Schema(description = "프로젝트 코드", example = "PJ-001")
        private String code;
        @Schema(description = "프로젝트명", example = "OO현장")
        private String name;
        @Schema(description = "프로젝트 축약명", example = "OO현장")
        private String shortName;
        @Schema(description = "위치", example = "서울")
        private String location;
        @Schema(description = "점수", example = "80.3")
        private Double score;
        @Schema(description = "등급 레벨", example = "3")
        private Integer level;
        @Schema(description = "탄소 배출량(kg)", example = "123.4")
        private Double carbonKg;
        @Schema(description = "절감 전력량(kWh)", example = "45.6")
        private Double powerSavingKwh;
        @Schema(description = "리스크 건수", example = "2")
        private Integer riskCount;
        @Schema(description = "미션 달성률", example = "85")
        private Integer missionRate;
        @Schema(description = "무재해 일수", example = "120")
        private Integer safetyDays;
        @Schema(description = "스냅샷 저장 여부", example = "true")
        private Boolean snapshotSaved;

        public static RankingResponseDto from(Project project, EsgDailySnapshot snapshot, LocalDate reportDate) {
            ParsedProjectName parsed = parseProjectName(project.getName());
            boolean saved = snapshot != null;
            return RankingResponseDto.builder()
                    .projectId(project.getIdx())
                    .code(parsed.code())
                    .name(parsed.displayName())
                    .shortName(parsed.displayName())
                    .location(project.getLocation())
                    .score(saved ? snapshot.getTotalScore() : 0.0)
                    .level(saved ? snapshot.getLevel() : 0)
                    .carbonKg(saved ? snapshot.getCarbonKg() : 0.0)
                    .powerSavingKwh(saved ? snapshot.getPowerSavingKwh() : 0.0)
                    .riskCount(saved ? snapshot.getRiskCount() : 0)
                    .missionRate(saved ? snapshot.getMissionRate() : 0)
                    .safetyDays(saved ? snapshot.getSafetyDays() : calculateSafetyDays(project.getStartDate(), reportDate))
                    .snapshotSaved(saved)
                    .build();
        }
    }

    private static ParsedProjectName parseProjectName(String rawName) {
        if (rawName == null || rawName.isBlank()) {
            return new ParsedProjectName("", "현장명 미지정");
        }
        Matcher matcher = SITE_CODE_PATTERN.matcher(rawName.trim());
        if (!matcher.find()) {
            return new ParsedProjectName("", rawName.trim());
        }
        return new ParsedProjectName(matcher.group(1).trim(), matcher.group(2).trim());
    }

    private static Integer calculateSafetyDays(LocalDate startDate, LocalDate reportDate) {
        if (startDate == null || reportDate == null || reportDate.isBefore(startDate)) {
            return 1;
        }
        long days = ChronoUnit.DAYS.between(startDate, reportDate) + 1;
        return (int) Math.max(1, days);
    }

    private record ParsedProjectName(String code, String displayName) {
    }
}
