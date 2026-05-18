package org.example.dndncore.esg.model;

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
    public static class SaveSnapshotRequestDto {
        @NotNull(message = "projectId is required")
        private Long projectId;

        @NotNull(message = "reportDate is required")
        private LocalDate reportDate;

        @NotNull(message = "environmentScore is required")
        private Double environmentScore;

        @NotNull(message = "socialScore is required")
        private Double socialScore;

        @NotNull(message = "governanceScore is required")
        private Double governanceScore;

        @NotNull(message = "totalScore is required")
        private Double totalScore;

        @NotNull(message = "level is required")
        private Integer level;

        private Double carbonKg;
        private Double powerSavingKwh;
        private Integer riskCount;
        private Integer missionRate;
        private Integer safetyDays;
        private Integer zoneCount;
        private String snapshotJson;

        @Valid
        private List<SaveZoneSnapshotRequestDto> zones;
    }

    @Getter
    @Setter
    @NoArgsConstructor
    @AllArgsConstructor
    @Builder
    public static class SaveZoneSnapshotRequestDto {
        private String zoneName;
        private String zoneType;
        private Double environmentScore;
        private Double socialScore;
        private Double governanceScore;
        private Double totalScore;
        private Integer level;
        private Double carbonKg;
        private Double powerSavingKwh;
        private Integer riskCount;
        private Integer missionRate;
        private Integer equipmentCount;
        private Integer highRiskEquipmentCount;
        private Double contributionWeight;
        private Double contributionScore;
        private String snapshotJson;
    }

    @Getter
    @NoArgsConstructor
    @AllArgsConstructor
    @Builder
    public static class DashboardResponseDto {
        private ProjectResponseDto currentProject;
        private SnapshotResponseDto currentSnapshot;
        private List<ZoneSnapshotResponseDto> currentZoneSnapshots;
        private List<MetricInputResponseDto> currentMetricInputs;
        private List<ProjectResponseDto> projects;
        private List<RankingResponseDto> rankings;
    }

    @Getter
    @NoArgsConstructor
    @AllArgsConstructor
    @Builder
    public static class ProjectResponseDto {
        private Long idx;
        private String code;
        private String name;
        private String shortName;
        private String location;
        private LocalDate startDate;
        private LocalDate endDate;
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
    public static class SnapshotResponseDto {
        private Long idx;
        private Long projectId;
        private LocalDate reportDate;
        private Double environmentScore;
        private Double socialScore;
        private Double governanceScore;
        private Double totalScore;
        private Integer level;
        private Double carbonKg;
        private Double powerSavingKwh;
        private Integer riskCount;
        private Integer missionRate;
        private Integer safetyDays;
        private Integer zoneCount;
        private String snapshotJson;
        private Boolean snapshotSaved;
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
    public static class ZoneSnapshotResponseDto {
        private Long idx;
        private Long projectId;
        private LocalDate reportDate;
        private String zoneName;
        private String zoneType;
        private Double environmentScore;
        private Double socialScore;
        private Double governanceScore;
        private Double totalScore;
        private Integer level;
        private Double carbonKg;
        private Double powerSavingKwh;
        private Integer riskCount;
        private Integer missionRate;
        private Integer equipmentCount;
        private Integer highRiskEquipmentCount;
        private Double contributionWeight;
        private Double contributionScore;
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
    public static class MetricInputResponseDto {
        private Long idx;
        private Long projectId;
        private LocalDate reportDate;
        private String zoneName;
        private Double carbonKg;
        private Double powerUsageKwh;
        private Double powerSavingKwh;
        private Double washWaterLiters;
        private Double wastewaterLiters;
        private Double wastewaterRecoveryRate;
        private Double fineDustValue;
        private Double noiseDb;
        private Integer complaintCount;
        private Integer complaintResolvedCount;
        private Double safetyEducationRate;
        private Double staffingRate;
        private Double reportRate;
        private Double actionTrackingRate;
        private Double dataLinkRate;
        private String memo;

        public static MetricInputResponseDto from(EsgMetricInput input) {
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
                    .fineDustValue(input.getFineDustValue())
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
    public static class RankingResponseDto {
        private Long projectId;
        private String code;
        private String name;
        private String shortName;
        private String location;
        private Double score;
        private Integer level;
        private Double carbonKg;
        private Double powerSavingKwh;
        private Integer riskCount;
        private Integer missionRate;
        private Integer safetyDays;
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
