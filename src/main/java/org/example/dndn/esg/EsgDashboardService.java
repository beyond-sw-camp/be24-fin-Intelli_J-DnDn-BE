package org.example.dndn.esg;

import lombok.RequiredArgsConstructor;
import org.example.dndn.auth.model.entity.SystemUser;
import org.example.dndn.auth.security.AuthAccessService;
import org.example.dndn.esg.model.EsgDailySnapshot;
import org.example.dndn.esg.model.EsgDashboardDto;
import org.example.dndn.esg.model.EsgMetricInput;
import org.example.dndn.esg.model.EsgZoneDailySnapshot;
import org.example.dndn.project.model.entity.Project;
import org.example.dndn.project.repository.ProjectRepository;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.time.LocalDate;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
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
    private final AuthAccessService authAccessService;

    public EsgDashboardDto.DashboardResponseDto readDashboard(LocalDate reportDate, Long projectId) {
        LocalDate targetDate = resolveReportDate(reportDate);
        List<Project> accessibleProjects = findAccessibleProjects();
        List<Project> rankingProjects = findRankingProjects(targetDate);
        Project currentProject = resolveCurrentProject(accessibleProjects, projectId);
        Map<Long, EsgDailySnapshot> snapshotMap = buildRankingSnapshotMap(rankingProjects, targetDate);
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
                        .thenComparing(EsgDashboardDto.RankingResponseDto::getScore, Comparator.reverseOrder())
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
                        .map(EsgDashboardDto.MetricInputResponseDto::from)
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
        Double previousSiteScore = previousSnapshot == null ? 0.0 : normalizeCumulativeScore(previousSnapshot.getTotalScore());
        Double dailySiteScore = normalizeDailyScore(request.getTotalScore());
        Double cumulativeSiteScore = roundOne(previousSiteScore + dailySiteScore);

        snapshot.update(
                normalizeDailyScore(request.getEnvironmentScore()),
                normalizeDailyScore(request.getSocialScore()),
                normalizeDailyScore(request.getGovernanceScore()),
                cumulativeSiteScore,
                resolveSiteFloor(cumulativeSiteScore),
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
                    EsgZoneDailySnapshot previousZoneSnapshot = previousZoneSnapshotMap.get(zoneName);
                    Double previousZoneScore = previousZoneSnapshot == null
                            ? 0.0
                            : normalizeCumulativeScore(previousZoneSnapshot.getTotalScore());
                    Double dailyZoneScore = normalizeDailyScore(request.getTotalScore());
                    Double cumulativeZoneScore = roundOne(previousZoneScore + dailyZoneScore);

                    EsgZoneDailySnapshot snapshot = currentDateSnapshotMap.getOrDefault(
                            zoneName,
                            EsgZoneDailySnapshot.builder()
                                    .project(project)
                                    .reportDate(targetDate)
                                    .zoneName(zoneName)
                                    .build()
                    );
                    snapshot.update(
                            zoneName,
                            normalizeText(request.getZoneType(), "work"),
                            normalizeDailyScore(request.getEnvironmentScore()),
                            normalizeDailyScore(request.getSocialScore()),
                            normalizeDailyScore(request.getGovernanceScore()),
                            cumulativeZoneScore,
                            resolveZoneFloor(cumulativeZoneScore),
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
                .toList();

        esgZoneDailySnapshotRepository.saveAll(snapshots);
        return esgZoneDailySnapshotRepository.findAllByProject_IdxAndReportDate(project.getIdx(), targetDate);
    }


    private Map<Long, EsgDailySnapshot> buildRankingSnapshotMap(List<Project> rankingProjects, LocalDate targetDate) {
        return rankingProjects.stream()
                .map(project -> esgDailySnapshotRepository
                        .findTopByProject_IdxAndReportDateLessThanEqualOrderByReportDateDesc(project.getIdx(), targetDate)
                        .map(snapshot -> Map.entry(project.getIdx(), snapshot))
                        .orElse(null))
                .filter(entry -> entry != null)
                .collect(Collectors.toMap(Map.Entry::getKey, Map.Entry::getValue));
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

    private List<Project> findAccessibleProjects() {
        return projectRepository.findAll().stream()
                .filter(project -> authAccessService.canAccessProjectId(project.getIdx()))
                .sorted(Comparator.comparing(Project::getIdx))
                .toList();
    }

    private List<Project> findRankingProjects(LocalDate reportDate) {
        return projectRepository.findAll().stream()
                .filter(project -> project.getEndDate() == null || !project.getEndDate().isBefore(reportDate))
                .sorted(Comparator.comparing(Project::getIdx))
                .toList();
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

    private Integer resolveSiteFloor(Double cumulativeScore) {
        double score = normalizeCumulativeScore(cumulativeScore);
        return (int) Math.floor(score / SITE_FLOOR_POINT);
    }

    private Integer resolveZoneFloor(Double cumulativeScore) {
        double score = normalizeCumulativeScore(cumulativeScore);
        return (int) Math.floor(score / ZONE_FLOOR_POINT);
    }

    private Integer normalizeLevel(Integer value) {
        if (value == null) {
            return 0;
        }
        return Math.max(0, value);
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
