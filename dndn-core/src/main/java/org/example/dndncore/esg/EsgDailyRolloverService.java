package org.example.dndncore.esg;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.example.dndncore.esg.event.EsgDashboardDataChangedEventPublisher;
import org.example.dndncore.esg.model.EsgDailySnapshot;
import org.example.dndncore.esg.model.EsgMetricInput;
import org.example.dndncore.esg.model.EsgZoneDailySnapshot;
import org.example.dndncore.project.model.entity.Project;
import org.example.dndncore.project.repository.ProjectRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.Comparator;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
@Transactional
public class EsgDailyRolloverService {

    private static final double SITE_FLOOR_POINT = 300.0;
    private static final double ZONE_FLOOR_POINT = 500.0;

    private final ProjectRepository projectRepository;
    private final EsgDailySnapshotRepository esgDailySnapshotRepository;
    private final EsgZoneDailySnapshotRepository esgZoneDailySnapshotRepository;
    private final EsgMetricInputRepository esgMetricInputRepository;
    private final EsgDashboardDataChangedEventPublisher esgDashboardDataChangedEventPublisher;

    public RolloverResult rolloverToday() {
        return rollover(LocalDate.now());
    }

    public RolloverResult rollover(LocalDate targetDate) {
        LocalDate today = targetDate != null ? targetDate : LocalDate.now();
        List<Project> activeProjects = projectRepository.findAll().stream()
                .filter(project -> isActiveProject(project, today))
                .sorted(Comparator.comparing(Project::getIdx))
                .toList();

        int createdSiteSnapshotCount = 0;
        int createdZoneSnapshotCount = 0;
        int createdMetricInputCount = 0;
        int skippedProjectCount = 0;

        for (Project project : activeProjects) {
            if (hasCurrentDateSnapshot(project, today)) {
                skippedProjectCount++;
                continue;
            }

            EsgDailySnapshot previousSnapshot = esgDailySnapshotRepository
                    .findTopByProject_IdxAndReportDateBeforeOrderByReportDateDesc(project.getIdx(), today)
                    .orElse(null);
            if (previousSnapshot == null) {
                skippedProjectCount++;
                continue;
            }

            List<EsgZoneDailySnapshot> previousZoneSnapshots = esgZoneDailySnapshotRepository
                    .findAllByProject_IdxAndReportDate(project.getIdx(), previousSnapshot.getReportDate());
            List<EsgZoneDailySnapshot> currentDateZoneSnapshots = esgZoneDailySnapshotRepository
                    .findAllByProject_IdxAndReportDate(project.getIdx(), today);
            List<EsgMetricInput> currentDateMetricInputs = esgMetricInputRepository
                    .findAllByProject_IdxAndReportDate(project.getIdx(), today);

            saveOpeningSiteSnapshot(project, today, previousSnapshot, previousZoneSnapshots.size());
            createdSiteSnapshotCount++;

            createdZoneSnapshotCount += saveOpeningZoneSnapshots(
                    project,
                    today,
                    previousZoneSnapshots,
                    currentDateZoneSnapshots
            );
            createdMetricInputCount += saveResetMetricInputs(
                    project,
                    today,
                    previousZoneSnapshots,
                    currentDateMetricInputs
            );
        }

        if (createdSiteSnapshotCount > 0 || createdZoneSnapshotCount > 0 || createdMetricInputCount > 0) {
            esgDashboardDataChangedEventPublisher.publishDate(today);
        }

        return new RolloverResult(
                activeProjects.size(),
                createdSiteSnapshotCount,
                createdZoneSnapshotCount,
                createdMetricInputCount,
                skippedProjectCount
        );
    }

    private boolean hasCurrentDateSnapshot(Project project, LocalDate today) {
        return esgDailySnapshotRepository.findByProject_IdxAndReportDate(project.getIdx(), today).isPresent();
    }

    private void saveOpeningSiteSnapshot(
            Project project,
            LocalDate today,
            EsgDailySnapshot previousSnapshot,
            int zoneCount
    ) {
        ScoreProgress carriedSiteProgress = carryFloorProgress(
                previousSnapshot.getTotalScore(),
                previousSnapshot.getLevel(),
                SITE_FLOOR_POINT
        );
        EsgDailySnapshot openingSnapshot = EsgDailySnapshot.builder()
                .project(project)
                .reportDate(today)
                .build();

        openingSnapshot.update(
                0.0,
                0.0,
                0.0,
                carriedSiteProgress.pointScore(),
                carriedSiteProgress.level(),
                0.0,
                0.0,
                0,
                0,
                resolveSafetyDays(project, previousSnapshot, today),
                Math.max(0, zoneCount),
                null
        );

        esgDailySnapshotRepository.save(openingSnapshot);
    }

    private int saveOpeningZoneSnapshots(
            Project project,
            LocalDate today,
            List<EsgZoneDailySnapshot> previousZoneSnapshots,
            List<EsgZoneDailySnapshot> currentDateZoneSnapshots
    ) {
        if (previousZoneSnapshots == null || previousZoneSnapshots.isEmpty()) {
            return 0;
        }

        Set<String> existingZoneNames = currentDateZoneSnapshots == null
                ? Set.of()
                : currentDateZoneSnapshots.stream()
                .map(snapshot -> normalizeText(snapshot.getZoneName(), ""))
                .filter(zoneName -> !zoneName.isBlank())
                .collect(Collectors.toSet());

        List<EsgZoneDailySnapshot> openingZoneSnapshots = previousZoneSnapshots.stream()
                .filter(previousZoneSnapshot -> !existingZoneNames.contains(normalizeText(previousZoneSnapshot.getZoneName(), "")))
                .map(previousZoneSnapshot -> {
                    ScoreProgress carriedZoneProgress = carryFloorProgress(
                            previousZoneSnapshot.getTotalScore(),
                            previousZoneSnapshot.getLevel(),
                            ZONE_FLOOR_POINT
                    );
                    EsgZoneDailySnapshot openingZoneSnapshot = EsgZoneDailySnapshot.builder()
                            .project(project)
                            .reportDate(today)
                            .zoneName(previousZoneSnapshot.getZoneName())
                            .build();

                    openingZoneSnapshot.update(
                            normalizeText(previousZoneSnapshot.getZoneName(), "미지정 구역"),
                            normalizeText(previousZoneSnapshot.getZoneType(), "work"),
                            0.0,
                            0.0,
                            0.0,
                            carriedZoneProgress.pointScore(),
                            carriedZoneProgress.level(),
                            0.0,
                            0.0,
                            0,
                            0,
                            0,
                            0,
                            normalizePercentDouble(previousZoneSnapshot.getContributionWeight()),
                            0.0,
                            null
                    );
                    return openingZoneSnapshot;
                })
                .toList();

        esgZoneDailySnapshotRepository.saveAll(openingZoneSnapshots);
        return openingZoneSnapshots.size();
    }

    private int saveResetMetricInputs(
            Project project,
            LocalDate today,
            List<EsgZoneDailySnapshot> previousZoneSnapshots,
            List<EsgMetricInput> currentDateMetricInputs
    ) {
        if (previousZoneSnapshots == null || previousZoneSnapshots.isEmpty()) {
            return 0;
        }

        Set<String> existingMetricZoneNames = currentDateMetricInputs == null
                ? Set.of()
                : currentDateMetricInputs.stream()
                .map(input -> normalizeText(input.getZoneName(), ""))
                .filter(zoneName -> !zoneName.isBlank())
                .collect(Collectors.toSet());

        List<EsgMetricInput> resetInputs = previousZoneSnapshots.stream()
                .filter(previousZoneSnapshot -> !existingMetricZoneNames.contains(normalizeText(previousZoneSnapshot.getZoneName(), "")))
                .map(previousZoneSnapshot -> EsgMetricInput.builder()
                        .project(project)
                        .reportDate(today)
                        .zoneName(normalizeText(previousZoneSnapshot.getZoneName(), "미지정 구역"))
                        .carbonKg(0.0)
                        .powerUsageKwh(0.0)
                        .powerSavingKwh(0.0)
                        .washWaterLiters(0.0)
                        .wastewaterLiters(0.0)
                        .wastewaterRecoveryRate(0.0)
                        .fineDustValue(0.0)
                        .noiseDb(0.0)
                        .complaintCount(0)
                        .complaintResolvedCount(0)
                        .safetyEducationRate(0.0)
                        .staffingRate(0.0)
                        .reportRate(0.0)
                        .actionTrackingRate(0.0)
                        .dataLinkRate(0.0)
                        .memo("00시 ESG 일일 초기화")
                        .build())
                .toList();

        esgMetricInputRepository.saveAll(resetInputs);
        return resetInputs.size();
    }

    private boolean isActiveProject(Project project, LocalDate targetDate) {
        if (project == null || targetDate == null) {
            return false;
        }
        boolean started = project.getStartDate() == null || !project.getStartDate().isAfter(targetDate);
        boolean notFinished = project.getEndDate() == null || !project.getEndDate().isBefore(targetDate);
        return started && notFinished;
    }

    private Integer resolveSafetyDays(Project project, EsgDailySnapshot previousSnapshot, LocalDate today) {
        int previousSafetyDays = normalizePositiveInteger(previousSnapshot.getSafetyDays());
        int calendarSafetyDays = calculateSafetyDays(project.getStartDate(), today);
        return Math.max(calendarSafetyDays, previousSafetyDays + 1);
    }

    private int calculateSafetyDays(LocalDate startDate, LocalDate reportDate) {
        if (startDate == null || reportDate == null || reportDate.isBefore(startDate)) {
            return 1;
        }
        long days = ChronoUnit.DAYS.between(startDate, reportDate) + 1;
        return (int) Math.max(1, days);
    }

    private String normalizeText(String value, String fallback) {
        if (value == null || value.isBlank()) {
            return fallback;
        }
        return value.trim();
    }

    private Double normalizeCumulativeScore(Double value) {
        if (value == null || value.isNaN() || value.isInfinite()) {
            return 0.0;
        }
        return Math.max(0.0, Math.round(value * 10.0) / 10.0);
    }

    private ScoreProgress carryFloorProgress(Double storedPointScore, Integer storedLevel, double floorPoint) {
        double safeFloorPoint = floorPoint > 0 ? floorPoint : SITE_FLOOR_POINT;
        double currentPointScore = normalizeFloorPointScore(storedPointScore, safeFloorPoint);
        int currentLevel = resolveStoredLevel(storedPointScore, storedLevel, safeFloorPoint);
        return new ScoreProgress(currentPointScore, currentLevel);
    }

    private double normalizeFloorPointScore(Double value, double floorPoint) {
        double score = normalizeCumulativeScore(value);
        if (floorPoint <= 0) {
            return score;
        }
        return Math.max(0.0, Math.round((score % floorPoint) * 10.0) / 10.0);
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

    private Integer normalizePositiveInteger(Integer value) {
        if (value == null) {
            return 0;
        }
        return Math.max(0, value);
    }

    private Double normalizePercentDouble(Double value) {
        if (value == null || value.isNaN() || value.isInfinite()) {
            return 0.0;
        }
        return Math.max(0.0, Math.min(100.0, Math.round(value * 10.0) / 10.0));
    }

    private record ScoreProgress(
            Double pointScore,
            Integer level
    ) {
    }

    public record RolloverResult(
            int targetProjectCount,
            int createdSiteSnapshotCount,
            int createdZoneSnapshotCount,
            int createdMetricInputCount,
            int skippedProjectCount
    ) {
    }
}
