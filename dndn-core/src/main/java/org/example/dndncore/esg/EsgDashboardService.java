package org.example.dndncore.esg;

import lombok.RequiredArgsConstructor;
import org.example.dndncore.auth.model.entity.SystemUser;
import org.example.dndncore.auth.security.AuthAccessService;
import org.example.dndncore.redis.cache.RedisCacheNames;
import org.example.dndncore.esg.event.EsgDashboardDataChangedEventPublisher;
import org.example.dndncore.esg.model.EsgDailySnapshot;
import org.example.dndncore.esg.model.EsgDashboardDto;
import org.example.dndncore.esg.model.EsgMetricInput;
import org.example.dndncore.esg.model.EsgZoneDailySnapshot;
import org.example.dndncore.project.model.entity.Project;
import org.example.dndncore.project.repository.ProjectRepository;
import org.example.dndncore.weather.WeatherInfoRepository;
import org.example.dndncore.weather.model.WeatherInfo;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.time.LocalDate;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.function.Function;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class EsgDashboardService {

    private static final Pattern SITE_CODE_PATTERN = Pattern.compile("^\\s*\\[([^\\]]+)]");
    private static final double SITE_FLOOR_POINT = 300.0;
    private static final double ZONE_FLOOR_POINT = 500.0;

    private final ProjectRepository projectRepository;
    private final EsgDailySnapshotRepository esgDailySnapshotRepository;
    private final EsgZoneDailySnapshotRepository esgZoneDailySnapshotRepository;
    private final EsgMetricInputRepository esgMetricInputRepository;
    private final WeatherInfoRepository weatherInfoRepository;
    private final AuthAccessService authAccessService;
    private final EsgDashboardDataChangedEventPublisher esgDashboardDataChangedEventPublisher;

    @Cacheable(cacheNames = RedisCacheNames.ESG_DASHBOARD, key = "@redisCacheKeyProvider.esgDashboardKey(#reportDate, #projectId)")
    public EsgDashboardDto.DashboardResponseDto readDashboard(LocalDate reportDate, Long projectId) {
        LocalDate targetDate = resolveReportDate(reportDate);
        List<Project> projects = projectRepository.findAll();
        List<Project> accessibleProjects = findAccessibleProjects(projects);
        List<Project> rankingProjects = findRankingProjects(projects, targetDate);
        Project currentProject = resolveCurrentProject(accessibleProjects, projectId);
        Map<Long, EsgDailySnapshot> snapshotMap = buildRankingSnapshotMap(rankingProjects, targetDate);
        Double storedFineDustValue = resolveStoredFineDustValue(targetDate);
        EsgDailySnapshot currentDateSnapshot = esgDailySnapshotRepository
                .findByProject_IdxAndReportDate(currentProject.getIdx(), targetDate)
                .orElse(null);
        List<EsgZoneDailySnapshot> currentZoneSnapshots = esgZoneDailySnapshotRepository
                .findAllByProject_IdxAndReportDate(currentProject.getIdx(), targetDate);
        List<EsgMetricInput> currentMetricInputs = esgMetricInputRepository
                .findAllByProject_IdxAndReportDate(currentProject.getIdx(), targetDate);

        List<EsgDashboardDto.RankingResponseDto> rankings = rankingProjects.stream()
                .map(project -> EsgDashboardDto.RankingResponseDto.from(
                        project,
                        snapshotMap.get(project.getIdx()),
                        targetDate
                ))
                .sorted(Comparator
                        .comparing(EsgDashboardDto.RankingResponseDto::getSnapshotSaved).reversed()
                        .thenComparing(EsgDashboardDto.RankingResponseDto::getLevel, Comparator.nullsLast(Comparator.reverseOrder()))
                        .thenComparing(EsgDashboardDto.RankingResponseDto::getScore, Comparator.nullsLast(Comparator.reverseOrder()))
                        .thenComparing(EsgDashboardDto.RankingResponseDto::getProjectId))
                .toList();

        return EsgDashboardDto.DashboardResponseDto.builder()
                .currentProject(EsgDashboardDto.ProjectResponseDto.from(currentProject, targetDate))
                .currentSnapshot(EsgDashboardDto.SnapshotResponseDto.from(
                        currentDateSnapshot,
                        currentZoneSnapshots
                ))
                .currentZoneSnapshots(currentZoneSnapshots.stream()
                        .map(EsgDashboardDto.ZoneSnapshotResponseDto::from)
                        .toList())
                .currentMetricInputs(currentMetricInputs.stream()
                        .map(input -> EsgDashboardDto.MetricInputResponseDto.from(input, storedFineDustValue))
                        .toList())
                .projects(accessibleProjects.stream()
                        .map(project -> EsgDashboardDto.ProjectResponseDto.from(project, targetDate))
                        .toList())
                .rankings(rankings)
                .build();
    }

    @Transactional
    public EsgDashboardDto.SnapshotResponseDto createOrUpdateSnapshot(
            EsgDashboardDto.SaveSnapshotRequestDto request
    ) {
        if (request == null || request.getProjectId() == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "현장 ID는 필수입니다.");
        }

        LocalDate targetDate = resolveReportDate(request.getReportDate());
        validateSnapshotWritableDate(targetDate);
        authAccessService.assertProjectAccess(request.getProjectId());
        Project project = projectRepository.findById(request.getProjectId())
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "현장을 찾을 수 없습니다."));

        EsgDailySnapshot snapshot = esgDailySnapshotRepository
                .findByProject_IdxAndReportDate(project.getIdx(), targetDate)
                .orElseGet(() -> EsgDailySnapshot.builder()
                        .project(project)
                        .reportDate(targetDate)
                        .build());

        EsgDailySnapshot previousSnapshot = esgDailySnapshotRepository
                .findTopByProject_IdxAndReportDateBeforeOrderByReportDateDesc(project.getIdx(), targetDate)
                .orElse(null);
        ScoreProgress siteProgress = advanceFloorProgress(
                previousSnapshot == null ? null : previousSnapshot.getTotalScore(),
                previousSnapshot == null ? null : previousSnapshot.getLevel(),
                request.getTotalScore(),
                SITE_FLOOR_POINT
        );

        snapshot.update(
                normalizeDailyScore(request.getEnvironmentScore()),
                normalizeDailyScore(request.getSocialScore()),
                normalizeDailyScore(request.getGovernanceScore()),
                siteProgress.pointScore(),
                siteProgress.level(),
                normalizePositiveDouble(request.getCarbonKg()),
                normalizePositiveDouble(request.getPowerSavingKwh()),
                normalizePositiveInteger(request.getRiskCount()),
                normalizePercent(request.getMissionRate()),
                normalizePositiveInteger(request.getSafetyDays()),
                normalizePositiveInteger(request.getZoneCount()),
                request.getSnapshotJson()
        );

        EsgDailySnapshot savedSnapshot = esgDailySnapshotRepository.save(snapshot);
        List<EsgZoneDailySnapshot> savedZoneSnapshots = replaceZoneSnapshots(
                project,
                targetDate,
                request.getZones(),
                previousSnapshot
        );

        esgDashboardDataChangedEventPublisher.publishProjectDate(project.getIdx(), targetDate);
        return EsgDashboardDto.SnapshotResponseDto.from(savedSnapshot, savedZoneSnapshots);
    }

    private List<EsgZoneDailySnapshot> replaceZoneSnapshots(
            Project project,
            LocalDate targetDate,
            List<EsgDashboardDto.SaveZoneSnapshotRequestDto> requests,
            EsgDailySnapshot previousSnapshot
    ) {
        List<EsgZoneDailySnapshot> currentDateSnapshots = esgZoneDailySnapshotRepository
                .findAllByProject_IdxAndReportDate(project.getIdx(), targetDate);

        if (requests == null || requests.isEmpty()) {
            return currentDateSnapshots;
        }

        Map<String, EsgZoneDailySnapshot> currentDateSnapshotMap = currentDateSnapshots.stream()
                .collect(Collectors.toMap(
                        snapshot -> normalizeText(snapshot.getZoneName(), ""),
                        Function.identity(),
                        (left, right) -> right
                ));
        Map<String, EsgZoneDailySnapshot> previousZoneSnapshotMap = buildPreviousZoneSnapshotMap(project, previousSnapshot);

        List<EsgZoneDailySnapshot> snapshots = requests.stream()
                .filter(request -> request.getZoneName() != null && !request.getZoneName().isBlank())
                .map(request -> {
                    String zoneName = request.getZoneName().trim();
                    EsgZoneDailySnapshot currentDateSnapshot = currentDateSnapshotMap.get(zoneName);

                    if (shouldKeepCurrentSupportSnapshot(currentDateSnapshot, request)) {
                        return currentDateSnapshot;
                    }

                    EsgZoneDailySnapshot previousZoneSnapshot = previousZoneSnapshotMap.get(zoneName);
                    ScoreProgress zoneProgress = shouldResetZoneProgress(request)
                            ? new ScoreProgress(0.0, 0)
                            : advanceFloorProgress(
                            previousZoneSnapshot == null ? null : previousZoneSnapshot.getTotalScore(),
                            previousZoneSnapshot == null ? null : previousZoneSnapshot.getLevel(),
                            request.getTotalScore(),
                            ZONE_FLOOR_POINT
                    );

                    EsgZoneDailySnapshot snapshot = currentDateSnapshot != null
                            ? currentDateSnapshot
                            : EsgZoneDailySnapshot.builder()
                            .project(project)
                            .reportDate(targetDate)
                            .zoneName(zoneName)
                            .build();
                    snapshot.update(
                            zoneName,
                            normalizeText(request.getZoneType(), "work"),
                            normalizeDailyScore(request.getEnvironmentScore()),
                            normalizeDailyScore(request.getSocialScore()),
                            normalizeDailyScore(request.getGovernanceScore()),
                            zoneProgress.pointScore(),
                            zoneProgress.level(),
                            normalizePositiveDouble(request.getCarbonKg()),
                            normalizePositiveDouble(request.getPowerSavingKwh()),
                            normalizePositiveInteger(request.getRiskCount()),
                            normalizePercent(request.getMissionRate()),
                            normalizePositiveInteger(request.getEquipmentCount()),
                            normalizePositiveInteger(request.getHighRiskEquipmentCount()),
                            normalizePercentDouble(request.getContributionWeight()),
                            normalizeDailyScore(request.getContributionScore()),
                            request.getSnapshotJson()
                    );
                    return snapshot;
                })
                .filter(Objects::nonNull)
                .toList();

        esgZoneDailySnapshotRepository.saveAll(snapshots);
        return esgZoneDailySnapshotRepository.findAllByProject_IdxAndReportDate(project.getIdx(), targetDate);
    }


    private boolean shouldKeepCurrentSupportSnapshot(
            EsgZoneDailySnapshot currentDateSnapshot,
            EsgDashboardDto.SaveZoneSnapshotRequestDto request
    ) {
        if (!isSupportZoneRequest(request) || !isInactiveSupportRequest(request)) {
            return false;
        }

        return currentDateSnapshot == null || isActiveSupportSnapshot(currentDateSnapshot);
    }

    private boolean isSupportZoneRequest(EsgDashboardDto.SaveZoneSnapshotRequestDto request) {
        if (request == null) {
            return false;
        }
        String zoneType = normalizeText(request.getZoneType(), "").toLowerCase();
        String zoneName = normalizeText(request.getZoneName(), "");
        return "support".equals(zoneType)
                || "outdoor".equals(zoneType)
                || "세척장".equals(zoneName)
                || "민원 구역".equals(zoneName)
                || "민원구역".equals(zoneName);
    }

    private boolean isInactiveSupportRequest(EsgDashboardDto.SaveZoneSnapshotRequestDto request) {
        return normalizeDailyScore(request.getTotalScore()) <= 0.0
                && normalizeDailyScore(request.getEnvironmentScore()) <= 0.0
                && normalizeDailyScore(request.getSocialScore()) <= 0.0
                && normalizeDailyScore(request.getGovernanceScore()) <= 0.0
                && normalizePositiveDouble(request.getCarbonKg()) <= 0.0
                && normalizePositiveDouble(request.getPowerSavingKwh()) <= 0.0
                && normalizePositiveInteger(request.getEquipmentCount()) <= 0
                && normalizePositiveInteger(request.getHighRiskEquipmentCount()) <= 0
                && normalizePositiveInteger(request.getRiskCount()) <= 0
                && normalizePercent(request.getMissionRate()) <= 0;
    }

    private boolean isActiveSupportSnapshot(EsgZoneDailySnapshot snapshot) {
        if (snapshot == null) {
            return false;
        }
        return normalizeCumulativeScore(snapshot.getTotalScore()) > 0.0
                || normalizeDailyScore(snapshot.getEnvironmentScore()) > 0.0
                || normalizeDailyScore(snapshot.getSocialScore()) > 0.0
                || normalizeDailyScore(snapshot.getGovernanceScore()) > 0.0
                || normalizePositiveDouble(snapshot.getCarbonKg()) > 0.0
                || normalizePositiveDouble(snapshot.getPowerSavingKwh()) > 0.0
                || normalizePositiveInteger(snapshot.getEquipmentCount()) > 0
                || normalizePositiveInteger(snapshot.getHighRiskEquipmentCount()) > 0
                || normalizePositiveInteger(snapshot.getRiskCount()) > 0
                || normalizePercent(snapshot.getMissionRate()) > 0;
    }


    private boolean shouldResetZoneProgress(EsgDashboardDto.SaveZoneSnapshotRequestDto request) {
        if (request == null) {
            return true;
        }
        String zoneType = normalizeText(request.getZoneType(), "").toLowerCase();
        String zoneName = normalizeText(request.getZoneName(), "");
        boolean supportZone = "support".equals(zoneType)
                || "outdoor".equals(zoneType)
                || "세척장".equals(zoneName)
                || "민원 구역".equals(zoneName)
                || "민원구역".equals(zoneName);
        if (!supportZone) {
            return false;
        }
        return normalizeDailyScore(request.getTotalScore()) <= 0.0
                && normalizePositiveInteger(request.getEquipmentCount()) <= 0
                && normalizePositiveInteger(request.getHighRiskEquipmentCount()) <= 0
                && normalizePositiveInteger(request.getRiskCount()) <= 0
                && normalizePercent(request.getMissionRate()) <= 0;
    }

    private Map<Long, EsgDailySnapshot> buildRankingSnapshotMap(List<Project> rankingProjects, LocalDate targetDate) {
        if (rankingProjects == null || rankingProjects.isEmpty()) {
            return Map.of();
        }

        List<Long> projectIds = rankingProjects.stream()
                .map(Project::getIdx)
                .toList();

        return esgDailySnapshotRepository
                .findLatestByProjectIdsAndReportDateLessThanEqual(projectIds, targetDate)
                .stream()
                .collect(Collectors.toMap(
                        snapshot -> snapshot.getProject().getIdx(),
                        Function.identity(),
                        (left, right) -> right
                ));
    }

    private Map<String, EsgZoneDailySnapshot> buildPreviousZoneSnapshotMap(
            Project project,
            EsgDailySnapshot previousSnapshot
    ) {
        if (previousSnapshot == null) {
            return Map.of();
        }

        return esgZoneDailySnapshotRepository
                .findAllByProject_IdxAndReportDate(project.getIdx(), previousSnapshot.getReportDate())
                .stream()
                .collect(Collectors.toMap(
                        snapshot -> normalizeText(snapshot.getZoneName(), ""),
                        Function.identity(),
                        (left, right) -> right
                ));
    }

    private List<Project> findAccessibleProjects(List<Project> projects) {
        return projects.stream()
                .filter(project -> authAccessService.canAccessProjectId(project.getIdx()))
                .sorted(Comparator.comparing(Project::getIdx))
                .toList();
    }

    private List<Project> findRankingProjects(List<Project> projects, LocalDate reportDate) {
        return projects.stream()
                .filter(project -> project.getEndDate() == null || !project.getEndDate().isBefore(reportDate))
                .sorted(Comparator.comparing(Project::getIdx))
                .toList();
    }

    private Double resolveStoredFineDustValue(LocalDate targetDate) {
        return weatherInfoRepository.findByReportDate(targetDate)
                .map(WeatherInfo::getFineDustValue)
                .map(this::parseFineDustValue)
                .orElse(null);
    }

    private Double parseFineDustValue(String fineDustValue) {
        if (fineDustValue == null || fineDustValue.isBlank()) {
            return null;
        }

        try {
            return Double.parseDouble(fineDustValue.trim());
        } catch (NumberFormatException ignored) {
            return null;
        }
    }

    private Project resolveCurrentProject(List<Project> projects, Long requestedProjectId) {
        if (projects.isEmpty()) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "조회 가능한 현장이 없습니다.");
        }

        if (requestedProjectId != null) {
            authAccessService.assertProjectAccess(requestedProjectId);
            return projects.stream()
                    .filter(project -> requestedProjectId.equals(project.getIdx()))
                    .findFirst()
                    .orElseThrow(() -> new ResponseStatusException(HttpStatus.FORBIDDEN, "조회 권한이 없는 현장입니다."));
        }

        return authAccessService.currentUser()
                .map(SystemUser::getSiteCode)
                .filter(siteCode -> siteCode != null && !siteCode.isBlank())
                .flatMap(siteCode -> projects.stream()
                        .filter(project -> siteCode.trim().equalsIgnoreCase(extractSiteCode(project)))
                        .findFirst())
                .orElse(projects.get(0));
    }

    private String extractSiteCode(Project project) {
        String name = project.getName();
        if (name == null) {
            return "";
        }
        Matcher matcher = SITE_CODE_PATTERN.matcher(name);
        return matcher.find() ? matcher.group(1).trim() : "";
    }

    private LocalDate resolveReportDate(LocalDate reportDate) {
        return reportDate != null ? reportDate : LocalDate.now();
    }

    private void validateSnapshotWritableDate(LocalDate targetDate) {
        if (targetDate.isBefore(LocalDate.now())) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "지난 날짜 ESG 스냅샷은 수정할 수 없습니다.");
        }
    }

    private String normalizeText(String value, String fallback) {
        if (value == null || value.isBlank()) {
            return fallback;
        }
        return value.trim();
    }

    private Double normalizeDailyScore(Double value) {
        if (value == null || value.isNaN() || value.isInfinite()) {
            return 0.0;
        }
        return Math.max(0.0, Math.min(100.0, Math.round(value * 10.0) / 10.0));
    }

    private Double normalizeCumulativeScore(Double value) {
        if (value == null || value.isNaN() || value.isInfinite()) {
            return 0.0;
        }
        return Math.max(0.0, Math.round(value * 10.0) / 10.0);
    }

    private Double roundOne(Double value) {
        return Math.max(0.0, Math.round(normalizeCumulativeScore(value) * 10.0) / 10.0);
    }

    private ScoreProgress advanceFloorProgress(
            Double storedPointScore,
            Integer storedLevel,
            Double earnedScore,
            double floorPoint
    ) {
        double safeFloorPoint = floorPoint > 0 ? floorPoint : SITE_FLOOR_POINT;
        double currentPointScore = normalizeFloorPointScore(storedPointScore, safeFloorPoint);
        int currentLevel = resolveStoredLevel(storedPointScore, storedLevel, safeFloorPoint);
        double dailyScore = normalizeDailyScore(earnedScore);
        double mergedPointScore = currentPointScore + dailyScore;
        int increasedLevel = (int) Math.floor(mergedPointScore / safeFloorPoint);
        double nextPointScore = roundOne(mergedPointScore % safeFloorPoint);
        return new ScoreProgress(nextPointScore, currentLevel + increasedLevel);
    }

    private double normalizeFloorPointScore(Double value, double floorPoint) {
        double score = normalizeCumulativeScore(value);
        if (floorPoint <= 0) {
            return score;
        }
        return roundOne(score % floorPoint);
    }

    private Integer resolveStoredLevel(Double storedPointScore, Integer storedLevel, double floorPoint) {
        int normalizedLevel = normalizeLevel(storedLevel);
        double normalizedScore = normalizeCumulativeScore(storedPointScore);
        if (normalizedLevel == 0 && floorPoint > 0 && normalizedScore >= floorPoint) {
            return (int) Math.floor(normalizedScore / floorPoint);
        }
        return normalizedLevel;
    }

    private Integer normalizeLevel(Integer value) {
        if (value == null) {
            return 0;
        }
        return Math.max(0, value);
    }

    private record ScoreProgress(
            Double pointScore,
            Integer level
    ) {
    }

    private Double normalizePositiveDouble(Double value) {
        if (value == null || value.isNaN() || value.isInfinite()) {
            return 0.0;
        }
        return Math.max(0.0, Math.round(value * 10.0) / 10.0);
    }

    private Integer normalizePositiveInteger(Integer value) {
        if (value == null) {
            return 0;
        }
        return Math.max(0, value);
    }

    private Integer normalizePercent(Integer value) {
        if (value == null) {
            return 0;
        }
        return Math.max(0, Math.min(100, value));
    }

    private Double normalizePercentDouble(Double value) {
        if (value == null || value.isNaN() || value.isInfinite()) {
            return 0.0;
        }
        return Math.max(0.0, Math.min(100.0, Math.round(value * 10.0) / 10.0));
    }
}
