package org.example.dndncore.esg;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.example.dndncore.esg.event.EsgDashboardDataChangedEventPublisher;
import org.example.dndncore.esg.model.EsgDailySnapshot;
import org.example.dndncore.esg.model.EsgMetricInput;
import org.example.dndncore.esg.model.EsgZoneDailySnapshot;
import org.example.dndncore.project.model.entity.Project;
import org.example.dndncore.project.repository.ProjectRepository;
import org.example.dndncore.report.DailyReportRepository;
import org.example.dndncore.report.model.DailyReport;
import org.example.dndncore.weather.WeatherInfoRepository;
import org.example.dndncore.weather.model.WeatherInfo;
import org.example.dndncore.weather.model.WeatherInfoDto;
import org.example.dndncore.workorder.WorkOrderRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.sql.Date;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
@Transactional
public class EsgSnapshotRefreshService {

    private static final double SITE_FLOOR_POINT = 300.0;
    private static final double ZONE_FLOOR_POINT = 500.0;
    private static final double WHEEL_WASH_REPLENISHMENT_LITERS_PER_EQUIPMENT = 3.0;
    private static final double CATEGORY_E_WEIGHT = 50.0;
    private static final double CATEGORY_S_WEIGHT = 30.0;
    private static final double CATEGORY_G_WEIGHT = 20.0;

    private static final List<Double> METRIC_WEIGHTS = List.of(40.0, 30.0, 20.0, 10.0);

    private static final List<String> HIGH_RISK_EQUIPMENT_KEYWORDS = List.of(
            "타워크레인",
            "크레인",
            "리프트",
            "펌프카",
            "덤프트럭",
            "고소작업대",
            "고소작업차",
            "굴착기"
    );

    private static final List<String> DUST_WORK_KEYWORDS = List.of(
            "굴착",
            "토사",
            "반출",
            "절단",
            "연마",
            "외부",
            "마감",
            "도장"
    );

    private static final List<CarbonFactor> CARBON_FACTORS = List.of(
            new CarbonFactor(List.of("콘크리트펌프카", "펌프카"), 2.2),
            new CarbonFactor(List.of("굴착기"), 2.0),
            new CarbonFactor(List.of("덤프트럭"), 2.0),
            new CarbonFactor(List.of("타워크레인", "크레인"), 1.7),
            new CarbonFactor(List.of("카고트럭"), 1.6),
            new CarbonFactor(List.of("고소작업차", "고소작업대", "리프트"), 1.5),
            new CarbonFactor(List.of("지게차"), 1.2)
    );

    private final ProjectRepository projectRepository;
    private final WorkOrderRepository workOrderRepository;
    private final DailyReportRepository dailyReportRepository;
    private final WeatherInfoRepository weatherInfoRepository;
    private final EsgDailySnapshotRepository esgDailySnapshotRepository;
    private final EsgZoneDailySnapshotRepository esgZoneDailySnapshotRepository;
    private final EsgMetricInputRepository esgMetricInputRepository;
    private final EsgDashboardDataChangedEventPublisher esgDashboardDataChangedEventPublisher;

    private final ObjectMapper objectMapper = new ObjectMapper();

    public void refreshProjectDate(Long projectId, LocalDate reportDate) {
        if (projectId == null || reportDate == null) {
            return;
        }

        if (!isToday(reportDate)) {
            esgDashboardDataChangedEventPublisher.publishProjectDate(projectId, reportDate);
            return;
        }

        projectRepository.findById(projectId).ifPresent(project -> {
            refreshProject(project, reportDate);
            esgDashboardDataChangedEventPublisher.publishProjectDate(project.getIdx(), reportDate);
        });
    }

    public void refreshDate(LocalDate reportDate) {
        if (reportDate == null) {
            return;
        }

        if (!isToday(reportDate)) {
            esgDashboardDataChangedEventPublisher.publishDate(reportDate);
            return;
        }

        List<Project> activeProjects = projectRepository.findAll().stream()
                .filter(project -> isActiveProject(project, reportDate))
                .sorted(Comparator.comparing(Project::getIdx))
                .toList();

        for (Project project : activeProjects) {
            refreshProject(project, reportDate);
        }

        esgDashboardDataChangedEventPublisher.publishDate(reportDate);
    }

    private void refreshProject(Project project, LocalDate reportDate) {
        if (project == null || project.getIdx() == null || reportDate == null) {
            return;
        }

        List<EquipmentRow> equipments = loadEquipments(project.getIdx(), reportDate);
        List<DailyReport> reports = loadReports(project.getIdx(), reportDate);
        WeatherContext weather = loadWeather(reportDate);

        List<EsgMetricInput> metricInputs = esgMetricInputRepository
                .findAllByProject_IdxAndReportDate(project.getIdx(), reportDate);
        Map<String, EsgMetricInput> metricInputByZone = metricInputs.stream()
                .filter(input -> !isBlank(input.getZoneName()))
                .collect(Collectors.toMap(
                        input -> normalizeText(input.getZoneName()),
                        Function.identity(),
                        (left, right) -> right,
                        LinkedHashMap::new
                ));

        List<EsgZoneDailySnapshot> currentDateZoneSnapshots = esgZoneDailySnapshotRepository
                .findAllByProject_IdxAndReportDate(project.getIdx(), reportDate);
        Map<String, EsgZoneDailySnapshot> currentZoneSnapshotByName = currentDateZoneSnapshots.stream()
                .filter(snapshot -> !isBlank(snapshot.getZoneName()))
                .collect(Collectors.toMap(
                        snapshot -> normalizeText(snapshot.getZoneName()),
                        Function.identity(),
                        (left, right) -> right,
                        LinkedHashMap::new
                ));

        int missionRate = calculateMissionRate(equipments, reportDate);
        List<ZoneRefreshModel> refreshZones = buildRefreshZones(
                project,
                reportDate,
                equipments,
                reports,
                metricInputByZone,
                currentZoneSnapshotByName,
                weather,
                missionRate
        );

        if (refreshZones.isEmpty()) {
            log.debug("[ESG 스냅샷] 계산 가능한 구역이 없어 저장을 건너뜁니다. - projectId={}, reportDate={}",
                    project.getIdx(),
                    reportDate);
            return;
        }

        EsgDailySnapshot previousSiteSnapshot = esgDailySnapshotRepository
                .findTopByProject_IdxAndReportDateBeforeOrderByReportDateDesc(project.getIdx(), reportDate)
                .orElse(null);
        Map<String, EsgZoneDailySnapshot> previousZoneSnapshotByName =
                loadPreviousZoneSnapshotMap(project, previousSiteSnapshot);

        saveOrUpdateZoneSnapshots(
                project,
                reportDate,
                refreshZones,
                currentZoneSnapshotByName,
                previousZoneSnapshotByName
        );
        saveOrUpdateSiteSnapshot(
                project,
                reportDate,
                refreshZones,
                previousSiteSnapshot
        );

        log.info("[ESG 스냅샷] 이벤트 기반 점수 갱신 완료 - projectId={}, reportDate={}, zoneCount={}",
                project.getIdx(),
                reportDate,
                refreshZones.size());
    }

    private List<ZoneRefreshModel> buildRefreshZones(
            Project project,
            LocalDate reportDate,
            List<EquipmentRow> equipments,
            List<DailyReport> reports,
            Map<String, EsgMetricInput> metricInputByZone,
            Map<String, EsgZoneDailySnapshot> currentZoneSnapshotByName,
            WeatherContext weather,
            int missionRate
    ) {
        List<ZoneRefreshModel> zones = new ArrayList<>();

        EsgMetricInput washMetricInput = metricInputByZone.get("세척장");
        EsgMetricInput complaintMetricInput = metricInputByZone.get("민원 구역");

        ZoneMetrics washBaseMetrics = buildMetrics(
                equipments,
                reports,
                "세척장",
                washMetricInput,
                currentZoneSnapshotByName.get("세척장"),
                weather,
                missionRate
        );
        ZoneMetrics complaintBaseMetrics = buildMetrics(
                equipments,
                reports,
                "민원 구역",
                complaintMetricInput,
                currentZoneSnapshotByName.get("민원 구역"),
                weather,
                missionRate
        );

        zones.add(buildWashZone(project, washBaseMetrics, weather, missionRate, hasMeaningfulSupportMetricInput(washMetricInput, "세척장")));
        zones.add(buildComplaintZone(project, complaintBaseMetrics, weather, missionRate, hasMeaningfulSupportMetricInput(complaintMetricInput, "민원 구역")));

        Map<String, List<EquipmentRow>> equipmentByWorkLocation = equipments.stream()
                .collect(Collectors.groupingBy(
                        row -> firstNonBlank(row.workLocation(), "작업구역 미지정"),
                        LinkedHashMap::new,
                        Collectors.toList()
                ));

        int sequence = 1;
        for (Map.Entry<String, List<EquipmentRow>> entry : equipmentByWorkLocation.entrySet()) {
            String zoneName = entry.getKey();
            ZoneMetrics metrics = buildMetrics(
                    entry.getValue(),
                    reports,
                    zoneName,
                    metricInputByZone.get(zoneName),
                    currentZoneSnapshotByName.get(zoneName),
                    weather,
                    missionRate
            );
            zones.add(buildWorkZone(project, sequence++, zoneName, entry.getValue(), metrics));
        }

        return rankZones(zones);
    }

    private ZoneRefreshModel buildWashZone(
            Project project,
            ZoneMetrics base,
            WeatherContext weather,
            int missionRate,
            boolean hasMetricInput
    ) {
        int washRisk = clampInt(Math.round(
                base.washTargetCount * 1.1
                        + base.washWaterDemandRisk * 0.7
                        + base.gateCongestionRisk * 0.45
                        + base.weatherRiskCount * 0.45
        ), 0, 10);

        ZoneMetrics metrics = base.copy();
        metrics.environmentScore = clampScore(
                base.environmentScore - washRisk * 0.7 + base.waterScore * 0.12
        );
        metrics.socialScore = clampScore(base.socialScore - washRisk * 0.35);
        metrics.governanceScore = clampScore(
                base.governanceScore - Math.max(0, washRisk - 4) * 0.9
        );
        metrics.operatingRisk = washRisk;

        boolean hasLinkedOperationData = hasEsgOperationData(base, hasMetricInput);
        if (!hasLinkedOperationData) {
            metrics.resetScoreFields();
        } else {
            metrics.totalScore = buildTotalScore(metrics);
        }

        String equipmentSummary = base.totalEquipmentCount > 0
                ? "금일 입차 장비 " + base.totalEquipmentCount + "대 · 세척 관리 " + base.washTargetCount + "대"
                : "금일 입차 장비 0대";
        String gateSummary = base.gateCount > 0
                ? base.gateCount + "개 게이트 입차 기준"
                : "게이트 0개";

        return new ZoneRefreshModel(
                "wash-zone",
                "세척장",
                "상시 관리구역",
                "support",
                metrics.totalScore,
                metrics,
                metrics.estimatedCarbonKg,
                metrics.powerSavingKwh,
                hasLinkedOperationData ? washRisk : 0,
                hasLinkedOperationData ? missionRate : 0,
                base.totalEquipmentCount,
                base.highRiskEquipmentCount,
                equipmentSummary,
                gateSummary,
                hasLinkedOperationData ? resolveZoneStatus(washRisk) : "대기"
        );
    }

    private ZoneRefreshModel buildComplaintZone(
            Project project,
            ZoneMetrics base,
            WeatherContext weather,
            int missionRate,
            boolean hasMetricInput
    ) {
        int complaintRisk = clampInt(Math.round(
                base.highRiskEquipmentCount * 1.1
                        + base.workLocationCount
                        + base.fineDustRiskLevel * 1.5
                        + base.complaintRisk * 0.9
                        + (base.windSpeed >= 8.0 ? 1.0 : 0.0)
                        + base.weatherRiskCount * 0.4
        ), 0, 10);

        ZoneMetrics metrics = base.copy();
        metrics.environmentScore = clampScore(
                base.environmentScore - base.fineDustRiskLevel * 1.8
        );
        metrics.socialScore = clampScore(base.socialScore - complaintRisk * 0.75);
        metrics.governanceScore = clampScore(
                base.governanceScore
                        - Math.max(0, complaintRisk - 3) * 0.8
                        - Math.max(0, 100.0 - base.complaintResolutionRate) * 0.08
        );
        metrics.operatingRisk = complaintRisk;

        boolean hasLinkedOperationData = hasEsgOperationData(base, hasMetricInput);
        if (!hasLinkedOperationData) {
            metrics.resetScoreFields();
        } else {
            metrics.totalScore = buildTotalScore(metrics);
        }

        String equipmentSummary = base.totalEquipmentCount > 0
                ? "작업구역 " + Math.max(1, base.workLocationCount) + "곳 · 장비 " + base.totalEquipmentCount + "대 영향권"
                : "금일 작업지시 장비 0대";
        String gateSummary = base.fineDustValue > 0
                ? "PM10 " + Math.round(base.fineDustValue) + "㎍/㎥ 기준"
                : "기상관제 저장 미세먼지 기준";

        return new ZoneRefreshModel(
                "complaint-zone",
                "민원 구역",
                "상시 관리구역",
                "support",
                metrics.totalScore,
                metrics,
                metrics.estimatedCarbonKg,
                metrics.powerSavingKwh,
                hasLinkedOperationData ? complaintRisk : 0,
                hasLinkedOperationData ? missionRate : 0,
                base.totalEquipmentCount,
                base.highRiskEquipmentCount,
                equipmentSummary,
                gateSummary,
                hasLinkedOperationData ? resolveZoneStatus(complaintRisk) : "대기"
        );
    }

    private ZoneRefreshModel buildWorkZone(
            Project project,
            int sequence,
            String zoneName,
            List<EquipmentRow> zoneEquipments,
            ZoneMetrics metrics
    ) {
        int risk = clampInt(Math.round(
                metrics.weatherRiskCount
                        + Math.max(0, metrics.totalEquipmentCount - 1)
                        + metrics.highRiskEquipmentCount
                        + metrics.fineDustRiskLevel * 0.7
                        + metrics.washWaterDemandRisk * 0.25
        ), 0, 10);

        metrics.operatingRisk = risk;
        metrics.socialScore = clampScore(metrics.socialScore - risk * 0.35);
        metrics.governanceScore = clampScore(
                metrics.governanceScore - Math.max(0, risk - 3) * 0.7
        );
        metrics.totalScore = buildTotalScore(metrics);

        String equipmentSummary = buildEquipmentSummary(zoneEquipments);
        String gateSummary = buildGateSummary(zoneEquipments);
        String type = buildWorkZoneType(zoneEquipments, equipmentSummary);

        return new ZoneRefreshModel(
                "work-zone-" + sequence,
                zoneName,
                type,
                "work",
                metrics.totalScore,
                metrics,
                metrics.estimatedCarbonKg,
                metrics.powerSavingKwh,
                risk,
                metrics.missionRate,
                metrics.totalEquipmentCount,
                metrics.highRiskEquipmentCount,
                equipmentSummary,
                gateSummary,
                resolveZoneStatus(risk)
        );
    }

    private ZoneMetrics buildMetrics(
            List<EquipmentRow> equipmentRows,
            List<DailyReport> reports,
            String zoneName,
            EsgMetricInput metricInput,
            EsgZoneDailySnapshot currentSnapshot,
            WeatherContext weather,
            int missionRate
    ) {
        List<EquipmentRow> equipmentList = equipmentRows != null ? equipmentRows : List.of();
        List<DailyReport> reportList = reports != null ? reports : List.of();
        StoredZoneMetricHints storedHints = readStoredHints(currentSnapshot);

        ZoneMetrics metrics = new ZoneMetrics();
        metrics.missionRate = missionRate;

        metrics.totalEquipmentCount = equipmentList.stream()
                .mapToInt(row -> normalizeCount(row.equipmentCount()))
                .sum();
        metrics.highRiskEquipmentCount = equipmentList.stream()
                .filter(row -> isHighRiskEquipment(row.equipmentName()))
                .mapToInt(row -> normalizeCount(row.equipmentCount()))
                .sum();
        metrics.washTargetCount = metrics.totalEquipmentCount;
        metrics.carbonLoadIndex = roundOne(equipmentList.stream()
                .mapToDouble(row -> getEquipmentCarbonFactor(row.equipmentName()) * normalizeCount(row.equipmentCount()))
                .sum());

        metrics.workLocationCount = (int) equipmentList.stream()
                .map(EquipmentRow::workLocation)
                .filter(value -> !isBlank(value))
                .map(this::normalizeText)
                .distinct()
                .count();

        Set<Integer> gateSet = equipmentList.stream()
                .map(EquipmentRow::gateIdx)
                .filter(Objects::nonNull)
                .collect(Collectors.toCollection(LinkedHashSet::new));
        metrics.gateCount = gateSet.size();

        Map<Integer, Integer> gateEquipmentCounts = new LinkedHashMap<>();
        for (EquipmentRow row : equipmentList) {
            if (row.gateIdx() == null) {
                continue;
            }
            gateEquipmentCounts.merge(row.gateIdx(), normalizeCount(row.equipmentCount()), Integer::sum);
        }

        metrics.assignedGateEquipmentCount = gateEquipmentCounts.values().stream()
                .mapToInt(Integer::intValue)
                .sum();
        metrics.maxGateEquipmentCount = gateEquipmentCounts.values().stream()
                .mapToInt(Integer::intValue)
                .max()
                .orElse(0);
        metrics.gateConcentrationRate = metrics.assignedGateEquipmentCount > 0
                ? (int) Math.round((metrics.maxGateEquipmentCount / (double) metrics.assignedGateEquipmentCount) * 100.0)
                : 0;

        double idealGateShare = metrics.gateCount > 0 ? 100.0 / metrics.gateCount : 100.0;
        double singleGatePenalty = metrics.gateCount <= 1 && metrics.assignedGateEquipmentCount >= 4
                ? Math.min(40.0, (metrics.assignedGateEquipmentCount - 3) * 5.0)
                : 0.0;
        double gateImbalancePenalty = metrics.gateCount >= 2
                ? Math.max(0.0, metrics.gateConcentrationRate - idealGateShare - 15.0) * 0.8
                : 0.0;
        metrics.gateCongestionRisk = clampInt(Math.round((singleGatePenalty + gateImbalancePenalty) / 10.0), 0, 10);
        metrics.idleReductionScore = clampScore(100.0 - singleGatePenalty - gateImbalancePenalty);
        metrics.dustWorkCount = countDustWorkOrders(equipmentList);

        metrics.weatherRiskCount = weather.weatherRiskCount();
        metrics.rainPercent = weather.precipitationProbability();
        metrics.windSpeed = weather.maxWindSpeed();
        metrics.fineDustValue = firstFinite(
                weather.fineDustValue(),
                metricInput != null ? metricInput.getFineDustValue() : null,
                storedHints.fineDustValue
        );
        if (metrics.fineDustValue == null) {
            metrics.fineDustValue = 0.0;
        }

        metrics.estimatedWashWaterLiters = Math.round(
                metrics.washTargetCount * WHEEL_WASH_REPLENISHMENT_LITERS_PER_EQUIPMENT
        );
        metrics.washWaterDemandRisk = clampInt(Math.round(
                metrics.washTargetCount * 0.55
                        + (metrics.rainPercent >= 60.0 ? 1.0 : 0.0)
                        + (metrics.fineDustValue >= 80.0 ? 1.0 : 0.0)
        ), 0, 10);
        metrics.fineDustRiskLevel = metrics.fineDustValue >= 151.0 ? 4
                : metrics.fineDustValue >= 81.0 ? 3
                : metrics.fineDustValue >= 31.0 ? 1
                : 0;

        metrics.complaintCount = clampInt(Math.round(firstFinite(
                metricInput != null ? metricInput.getComplaintCount() : null,
                storedHints.complaintCount,
                0.0
        )), 0, Integer.MAX_VALUE);
        metrics.complaintResolvedCount = clampInt(Math.round(firstFinite(
                metricInput != null ? metricInput.getComplaintResolvedCount() : null,
                storedHints.complaintResolvedCount,
                0.0
        )), 0, metrics.complaintCount);
        metrics.unresolvedComplaintCount = Math.max(0, metrics.complaintCount - metrics.complaintResolvedCount);
        metrics.complaintResolutionRate = metrics.complaintCount > 0
                ? clampScore((metrics.complaintResolvedCount / (double) metrics.complaintCount) * 100.0)
                : 100.0;
        metrics.complaintRisk = clampInt(Math.round(
                metrics.unresolvedComplaintCount * 1.5
                        + metrics.complaintCount * 0.35
                        + metrics.fineDustRiskLevel * 0.8
        ), 0, 10);

        metrics.powerPeakRisk = clampInt(Math.round(
                metrics.gateCount * 0.8
                        + Math.max(0, metrics.totalEquipmentCount - 4) * 0.6
                        + (metrics.rainPercent >= 60.0 || metrics.fineDustValue >= 80.0 ? 1.0 : 0.0)
                        + metrics.washTargetCount * 0.25
        ), 0, 10);

        Double actualCarbonKg = firstFinite(
                metricInput != null ? metricInput.getCarbonKg() : null,
                storedHints.estimatedCarbonKg
        );
        metrics.estimatedCarbonKg = actualCarbonKg != null
                ? Math.max(0.0, Math.round(actualCarbonKg))
                : Math.max(0.0, Math.round(metrics.carbonLoadIndex * 6.8 + metrics.highRiskEquipmentCount * 2.5));

        Double actualPowerSavingKwh = firstFinite(
                metricInput != null ? metricInput.getPowerSavingKwh() : null,
                storedHints.powerSavingKwh
        );
        metrics.powerSavingKwh = actualPowerSavingKwh != null
                ? Math.max(0.0, Math.round(actualPowerSavingKwh))
                : metrics.totalEquipmentCount > 0
                ? Math.max(0.0, Math.round(42.0 + metrics.gateCount * 7.0 + Math.max(0, 10 - metrics.powerPeakRisk) * 3.0))
                : 0.0;

        metrics.carbonScore = clampScore(100.0 - Math.min(40.0, metrics.carbonLoadIndex * 4.0));
        metrics.waterScore = clampScore(100.0 - Math.min(35.0, metrics.washWaterDemandRisk * 3.5));
        metrics.fineDustScore = clampScore(100.0 - metrics.fineDustRiskLevel * 12.0 - metrics.dustWorkCount * 2.0);
        metrics.powerScore = clampScore(100.0 - Math.min(25.0, metrics.powerPeakRisk * 2.5));
        metrics.environmentScore = buildWeightedMetricScore(
                metrics.carbonScore,
                metrics.waterScore,
                metrics.fineDustScore,
                metrics.idleReductionScore
        );

        metrics.staffingRate = firstFinite(
                metricInput != null ? metricInput.getStaffingRate() : null,
                storedHints.staffingRate,
                metrics.totalEquipmentCount > 0 ? (double) missionRate : 0.0
        );
        metrics.safetyEducationRate = firstFinite(
                metricInput != null ? metricInput.getSafetyEducationRate() : null,
                storedHints.safetyEducationRate,
                metrics.totalEquipmentCount > 0 ? (double) missionRate : 0.0
        );
        metrics.workerCount = storedHints.workerCount != null ? storedHints.workerCount : 0;
        metrics.assignedWorkerCount = storedHints.assignedWorkerCount != null ? storedHints.assignedWorkerCount : 0;
        metrics.requiredWorkerCount = storedHints.requiredWorkerCount != null ? storedHints.requiredWorkerCount : 0;
        metrics.trainedWorkerCount = storedHints.trainedWorkerCount != null ? storedHints.trainedWorkerCount : 0;

        metrics.weatherProtectionScore = clampScore(
                100.0 - metrics.weatherRiskCount * 6.0
                        - metrics.fineDustRiskLevel * 4.0
                        - (metrics.windSpeed >= 8.0 ? 6.0 : 0.0)
        );
        metrics.routeSafetyScore = clampScore(
                100.0 - Math.min(35.0, metrics.highRiskEquipmentCount * 4.0 + metrics.gateCount * 1.5)
        );
        metrics.socialScore = buildWeightedMetricScore(
                metrics.safetyEducationRate,
                metrics.staffingRate,
                metrics.weatherProtectionScore,
                metrics.routeSafetyScore
        );

        metrics.reportCount = scopeReportsByZone(reportList, zoneName).size();
        ReportMetrics reportMetrics = buildReportMetrics(reportList, equipmentList, zoneName, metrics.weatherRiskCount);
        metrics.reportRate = clampScore(firstFinite(
                metricInput != null ? metricInput.getReportRate() : null,
                reportMetrics.reportRate(),
                storedHints.reportRate,
                0.0
        ));
        metrics.actionTrackingRate = clampScore(firstFinite(
                metricInput != null ? metricInput.getActionTrackingRate() : null,
                reportMetrics.actionTrackingRate(),
                storedHints.actionTrackingRate,
                0.0
        ));
        metrics.dataLinkRate = clampScore(firstFinite(
                metricInput != null ? metricInput.getDataLinkRate() : null,
                storedHints.dataLinkRate,
                buildDefaultDataLinkRate(weather, equipmentList, reportList, metrics)
        ));
        metrics.missingCheckCount = Math.max(
                0,
                (int) Math.round((100.0 - metrics.reportRate) / 25.0) + Math.max(0, metrics.weatherRiskCount - 2)
        );
        metrics.checkScore = clampScore(100.0 - metrics.missingCheckCount * 8.0);
        metrics.governanceScore = buildWeightedMetricScore(
                metrics.reportRate,
                metrics.actionTrackingRate,
                metrics.dataLinkRate,
                metrics.checkScore
        );

        metrics.totalScore = buildTotalScore(metrics);
        return metrics;
    }

    private void saveOrUpdateZoneSnapshots(
            Project project,
            LocalDate reportDate,
            List<ZoneRefreshModel> refreshZones,
            Map<String, EsgZoneDailySnapshot> currentZoneSnapshotByName,
            Map<String, EsgZoneDailySnapshot> previousZoneSnapshotByName
    ) {
        int targetCount = refreshZones.size();
        double contributionWeight = targetCount > 0 ? roundOne(100.0 / targetCount) : 0.0;

        List<EsgZoneDailySnapshot> snapshots = refreshZones.stream()
                .map(zone -> {
                    EsgZoneDailySnapshot currentZoneSnapshot = currentZoneSnapshotByName.get(zone.zoneName());
                    if (shouldKeepCurrentSupportSnapshot(currentZoneSnapshot, zone)) {
                        return currentZoneSnapshot;
                    }

                    EsgZoneDailySnapshot previousZoneSnapshot = previousZoneSnapshotByName.get(zone.zoneName());
                    ScoreProgress progress = shouldResetZoneProgress(zone)
                            ? new ScoreProgress(0.0, 0)
                            : advanceFloorProgress(
                                    previousZoneSnapshot == null ? null : previousZoneSnapshot.getTotalScore(),
                                    previousZoneSnapshot == null ? null : previousZoneSnapshot.getLevel(),
                                    zone.dailyScore(),
                                    ZONE_FLOOR_POINT
                            );

                    EsgZoneDailySnapshot snapshot = currentZoneSnapshot != null
                            ? currentZoneSnapshot
                            : EsgZoneDailySnapshot.builder()
                                    .project(project)
                                    .reportDate(reportDate)
                                    .zoneName(zone.zoneName())
                                    .build();

                    snapshot.update(
                            zone.zoneName(),
                            zone.zoneType(),
                            roundOne(zone.metrics().environmentScore),
                            roundOne(zone.metrics().socialScore),
                            roundOne(zone.metrics().governanceScore),
                            progress.pointScore(),
                            progress.level(),
                            roundOne(zone.carbonKg()),
                            roundOne(zone.powerSavingKwh()),
                            zone.riskCount(),
                            clampInt(zone.missionRate(), 0, 100),
                            zone.equipmentCount(),
                            zone.highRiskEquipmentCount(),
                            contributionWeight,
                            targetCount > 0 ? roundOne(zone.dailyScore() / targetCount) : 0.0,
                            toZoneSnapshotJson(zone)
                    );
                    return snapshot;
                })
                .toList();

        esgZoneDailySnapshotRepository.saveAll(snapshots);
    }

    private void saveOrUpdateSiteSnapshot(
            Project project,
            LocalDate reportDate,
            List<ZoneRefreshModel> refreshZones,
            EsgDailySnapshot previousSiteSnapshot
    ) {
        int zoneCount = refreshZones.size();
        List<ZoneRefreshModel> siteScoreZones = selectSiteScoreZones(refreshZones);
        double environmentScore = average(siteScoreZones, zone -> zone.metrics().environmentScore);
        double socialScore = average(siteScoreZones, zone -> zone.metrics().socialScore);
        double governanceScore = average(siteScoreZones, zone -> zone.metrics().governanceScore);
        double dailySiteScore = average(siteScoreZones, ZoneRefreshModel::dailyScore);
        double carbonKg = refreshZones.stream().mapToDouble(ZoneRefreshModel::carbonKg).sum();
        double powerSavingKwh = refreshZones.stream().mapToDouble(ZoneRefreshModel::powerSavingKwh).sum();
        int riskCount = refreshZones.stream().mapToInt(ZoneRefreshModel::riskCount).sum();
        int missionRate = !siteScoreZones.isEmpty()
                ? (int) Math.round(siteScoreZones.stream().mapToInt(ZoneRefreshModel::missionRate).average().orElse(0.0))
                : 0;

        ScoreProgress siteProgress = advanceFloorProgress(
                previousSiteSnapshot == null ? null : previousSiteSnapshot.getTotalScore(),
                previousSiteSnapshot == null ? null : previousSiteSnapshot.getLevel(),
                dailySiteScore,
                SITE_FLOOR_POINT
        );

        EsgDailySnapshot snapshot = esgDailySnapshotRepository
                .findByProject_IdxAndReportDate(project.getIdx(), reportDate)
                .orElseGet(() -> EsgDailySnapshot.builder()
                        .project(project)
                        .reportDate(reportDate)
                        .build());

        snapshot.update(
                roundOne(environmentScore),
                roundOne(socialScore),
                roundOne(governanceScore),
                siteProgress.pointScore(),
                siteProgress.level(),
                roundOne(carbonKg),
                roundOne(powerSavingKwh),
                riskCount,
                clampInt(missionRate, 0, 100),
                calculateSafetyDays(project, reportDate),
                zoneCount,
                toSiteSnapshotJson(project, reportDate, refreshZones, dailySiteScore)
        );

        esgDailySnapshotRepository.save(snapshot);
    }

    private List<EquipmentRow> loadEquipments(Long projectId, LocalDate reportDate) {
        if (projectId == null || reportDate == null) {
            return List.of();
        }

        return workOrderRepository.findGateEquipmentsByTargetDate(reportDate, projectId, false).stream()
                .map(this::toEquipmentRow)
                .toList();
    }

    private List<DailyReport> loadReports(Long projectId, LocalDate reportDate) {
        return dailyReportRepository.findByReportDate(reportDate).stream()
                .filter(report -> Objects.equals(resolveProjectId(report), projectId))
                .toList();
    }

    private Long resolveProjectId(DailyReport report) {
        if (report == null
                || report.getWorkPlan() == null
                || report.getWorkPlan().getTradeProcess() == null
                || report.getWorkPlan().getTradeProcess().getMasterSchedule() == null
                || report.getWorkPlan().getTradeProcess().getMasterSchedule().getProject() == null) {
            return null;
        }

        return report.getWorkPlan()
                .getTradeProcess()
                .getMasterSchedule()
                .getProject()
                .getIdx();
    }

    private EquipmentRow toEquipmentRow(Object[] row) {
        return new EquipmentRow(
                toLong(row, 0),
                toLong(row, 1),
                toStringValue(row, 2),
                toStringValue(row, 3),
                toStringValue(row, 4),
                toLocalDate(row, 5),
                firstNonBlank(toStringValue(row, 6), "작업구역 미지정"),
                toStringValue(row, 7),
                toInteger(row, 8),
                firstNonBlank(toStringValue(row, 9), "장비 미지정"),
                defaultInteger(toInteger(row, 10), 1),
                toStringValue(row, 11),
                toLong(row, 12)
        );
    }

    private WeatherContext loadWeather(LocalDate reportDate) {
        WeatherInfo weatherInfo = weatherInfoRepository.findByReportDate(reportDate).orElse(null);
        if (weatherInfo == null) {
            return WeatherContext.empty();
        }

        try {
            WeatherInfoDto.DashboardRes dashboard = objectMapper.readValue(
                    weatherInfo.getDashboardJson(),
                    WeatherInfoDto.DashboardRes.class
            );
            WeatherInfoDto.WeatherAnalysis analysis = dashboard.getAnalysis();
            double precipitationProbability = analysis != null
                    ? safeDouble(analysis.getPrecipitationProbability())
                    : parseDouble(weatherInfo.getPrecipitationProbability());
            double maxWindSpeed = analysis != null
                    ? safeDouble(analysis.getMaxWindSpeed())
                    : 0.0;
            double fineDustValue = parseDouble(weatherInfo.getFineDustValue());
            if (fineDustValue <= 0.0 && analysis != null && analysis.getFineDustValue() != null) {
                fineDustValue = safeDouble(analysis.getFineDustValue());
            }
            if (fineDustValue <= 0.0 && dashboard.getAirQuality() != null && dashboard.getAirQuality().getValue() != null) {
                fineDustValue = safeDouble(dashboard.getAirQuality().getValue());
            }

            int weatherRiskCount = sizeOf(dashboard.getEquipmentRisks()) + sizeOf(dashboard.getPlanRisks());
            return new WeatherContext(
                    true,
                    precipitationProbability,
                    maxWindSpeed,
                    fineDustValue,
                    weatherRiskCount
            );
        } catch (Exception ignored) {
            return new WeatherContext(
                    false,
                    parseDouble(weatherInfo.getPrecipitationProbability()),
                    0.0,
                    parseDouble(weatherInfo.getFineDustValue()),
                    0
            );
        }
    }

    private Map<String, EsgZoneDailySnapshot> loadPreviousZoneSnapshotMap(
            Project project,
            EsgDailySnapshot previousSiteSnapshot
    ) {
        if (project == null || previousSiteSnapshot == null) {
            return Map.of();
        }

        return esgZoneDailySnapshotRepository
                .findAllByProject_IdxAndReportDate(project.getIdx(), previousSiteSnapshot.getReportDate())
                .stream()
                .filter(snapshot -> !isBlank(snapshot.getZoneName()))
                .collect(Collectors.toMap(
                        snapshot -> normalizeText(snapshot.getZoneName()),
                        Function.identity(),
                        (left, right) -> right,
                        LinkedHashMap::new
                ));
    }

    private StoredZoneMetricHints readStoredHints(EsgZoneDailySnapshot snapshot) {
        if (snapshot == null || isBlank(snapshot.getSnapshotJson())) {
            return StoredZoneMetricHints.empty();
        }

        try {
            JsonNode root = objectMapper.readTree(snapshot.getSnapshotJson());
            JsonNode metrics = root.path("metrics");
            if (metrics.isMissingNode() || metrics.isNull()) {
                return StoredZoneMetricHints.empty();
            }
            return new StoredZoneMetricHints(
                    nullableDouble(metrics, "fineDustValue"),
                    nullableDouble(metrics, "estimatedCarbonKg"),
                    nullableDouble(metrics, "powerSavingKwh"),
                    nullableDouble(metrics, "staffingRate"),
                    nullableDouble(metrics, "safetyEducationRate"),
                    nullableDouble(metrics, "reportRate"),
                    nullableDouble(metrics, "actionTrackingRate"),
                    nullableDouble(metrics, "dataLinkRate"),
                    nullableInt(metrics, "complaintCount"),
                    nullableInt(metrics, "complaintResolvedCount"),
                    nullableInt(metrics, "workerCount"),
                    nullableInt(metrics, "assignedWorkerCount"),
                    nullableInt(metrics, "requiredWorkerCount"),
                    nullableInt(metrics, "trainedWorkerCount")
            );
        } catch (Exception ignored) {
            return StoredZoneMetricHints.empty();
        }
    }

    private String toZoneSnapshotJson(ZoneRefreshModel zone) {
        Map<String, Object> json = new LinkedHashMap<>();
        json.put("id", zone.id());
        json.put("name", zone.zoneName());
        json.put("type", zone.type());
        json.put("zoneType", zone.zoneType());
        json.put("rank", zone.rank());
        json.put("status", zone.status());
        json.put("equipmentSummary", zone.equipmentSummary());
        json.put("gateSummary", zone.gateSummary());
        json.put("metrics", zone.metrics().toMap());
        return writeJson(json);
    }

    private String toSiteSnapshotJson(
            Project project,
            LocalDate reportDate,
            List<ZoneRefreshModel> zones,
            double dailySiteScore
    ) {
        Map<String, Object> json = new LinkedHashMap<>();
        json.put("projectId", project != null ? project.getIdx() : null);
        json.put("reportDate", reportDate);
        List<ZoneRefreshModel> scoreTargetZones = selectSiteScoreZones(zones);
        json.put("dailySiteScore", roundOne(dailySiteScore));
        json.put("scoreTargetZoneCount", scoreTargetZones.size());
        json.put("zoneCount", zones.size());
        json.put("zones", zones.stream().map(zone -> {
            Map<String, Object> row = new LinkedHashMap<>();
            row.put("id", zone.id());
            row.put("name", zone.zoneName());
            row.put("zoneType", zone.zoneType());
            row.put("dailyScore", roundOne(zone.dailyScore()));
            row.put("rank", zone.rank());
            return row;
        }).toList());
        return writeJson(json);
    }

    private String writeJson(Object source) {
        try {
            return objectMapper.writeValueAsString(source);
        } catch (Exception ignored) {
            return "{}";
        }
    }

    private List<ZoneRefreshModel> rankZones(List<ZoneRefreshModel> zones) {
        List<ZoneRefreshModel> ranked = zones.stream()
                .sorted(Comparator.comparing(ZoneRefreshModel::dailyScore).reversed())
                .toList();

        if (ranked.isEmpty()) {
            return ranked;
        }

        double leaderScore = ranked.get(0).dailyScore();
        List<ZoneRefreshModel> result = new ArrayList<>();
        for (int index = 0; index < ranked.size(); index++) {
            ZoneRefreshModel zone = ranked.get(index);
            result.add(zone.withRank(index + 1, roundOne(index == 0 ? 0.0 : zone.dailyScore() - leaderScore)));
        }
        return result;
    }

    private List<ZoneRefreshModel> selectSiteScoreZones(List<ZoneRefreshModel> zones) {
        if (zones == null || zones.isEmpty()) {
            return List.of();
        }
        return zones.stream()
                .filter(this::isSiteScoreZone)
                .toList();
    }

    private boolean isSiteScoreZone(ZoneRefreshModel zone) {
        if (zone == null) {
            return false;
        }
        String zoneType = normalizeText(zone.zoneType()).toLowerCase(Locale.ROOT);
        if ("support".equals(zoneType) || "outdoor".equals(zoneType)) {
            return false;
        }
        String zoneName = normalizeText(zone.zoneName());
        if ("세척장".equals(zoneName) || "민원 구역".equals(zoneName) || "민원구역".equals(zoneName)) {
            return false;
        }
        return "work".equals(zoneType) || zone.equipmentCount() > 0;
    }

    private boolean shouldResetZoneProgress(ZoneRefreshModel zone) {
        if (zone == null) {
            return true;
        }
        String zoneType = normalizeText(zone.zoneType()).toLowerCase(Locale.ROOT);
        if (!("support".equals(zoneType) || "outdoor".equals(zoneType))) {
            return false;
        }
        return zone.dailyScore() <= 0.0
                && zone.equipmentCount() <= 0
                && zone.highRiskEquipmentCount() <= 0
                && zone.riskCount() <= 0
                && zone.missionRate() <= 0
                && zone.metrics().reportCount <= 0
                && zone.metrics().complaintCount <= 0
                && zone.metrics().complaintResolvedCount <= 0;
    }

    private boolean shouldKeepCurrentSupportSnapshot(
            EsgZoneDailySnapshot currentDateSnapshot,
            ZoneRefreshModel refreshZone
    ) {
        if (currentDateSnapshot == null || refreshZone == null) {
            return false;
        }
        return isSupportRefreshZone(refreshZone)
                && isInactiveSupportRefreshZone(refreshZone)
                && isActiveSupportSnapshot(currentDateSnapshot);
    }

    private boolean isSupportRefreshZone(ZoneRefreshModel zone) {
        if (zone == null) {
            return false;
        }
        String zoneType = normalizeText(zone.zoneType()).toLowerCase(Locale.ROOT);
        String zoneName = normalizeText(zone.zoneName());
        return "support".equals(zoneType)
                || "outdoor".equals(zoneType)
                || "세척장".equals(zoneName)
                || "민원 구역".equals(zoneName)
                || "민원구역".equals(zoneName);
    }

    private boolean isInactiveSupportRefreshZone(ZoneRefreshModel zone) {
        if (zone == null) {
            return true;
        }
        return zone.dailyScore() <= 0.0
                && zone.equipmentCount() <= 0
                && zone.highRiskEquipmentCount() <= 0
                && zone.riskCount() <= 0
                && zone.missionRate() <= 0
                && zone.metrics().environmentScore <= 0.0
                && zone.metrics().socialScore <= 0.0
                && zone.metrics().governanceScore <= 0.0
                && zone.metrics().totalScore <= 0.0
                && zone.metrics().reportCount <= 0
                && zone.metrics().complaintCount <= 0
                && zone.metrics().complaintResolvedCount <= 0
                && zone.metrics().workerCount <= 0
                && zone.metrics().assignedWorkerCount <= 0
                && zone.metrics().requiredWorkerCount <= 0
                && zone.metrics().trainedWorkerCount <= 0;
    }

    private boolean isActiveSupportSnapshot(EsgZoneDailySnapshot snapshot) {
        if (snapshot == null) {
            return false;
        }
        return normalizeCumulativeSnapshotScore(snapshot.getTotalScore()) > 0.0
                || normalizeCumulativeSnapshotScore(snapshot.getEnvironmentScore()) > 0.0
                || normalizeCumulativeSnapshotScore(snapshot.getSocialScore()) > 0.0
                || normalizeCumulativeSnapshotScore(snapshot.getGovernanceScore()) > 0.0
                || normalizeCumulativeSnapshotScore(snapshot.getCarbonKg()) > 0.0
                || normalizeCumulativeSnapshotScore(snapshot.getPowerSavingKwh()) > 0.0
                || snapshotInteger(snapshot.getEquipmentCount()) > 0
                || snapshotInteger(snapshot.getHighRiskEquipmentCount()) > 0
                || snapshotInteger(snapshot.getRiskCount()) > 0
                || snapshotInteger(snapshot.getMissionRate()) > 0
                || hasActiveSupportMetricInSnapshotJson(snapshot.getSnapshotJson());
    }

    private boolean hasActiveSupportMetricInSnapshotJson(String snapshotJson) {
        if (isBlank(snapshotJson)) {
            return false;
        }
        try {
            JsonNode root = objectMapper.readTree(snapshotJson);
            JsonNode metrics = root.path("metrics");
            if (metrics.isMissingNode() || metrics.isNull()) {
                return false;
            }
            return metrics.path("supportOperationActive").asBoolean(false)
                    || positiveJsonNumber(metrics, "totalEquipmentCount")
                    || positiveJsonNumber(metrics, "highRiskEquipmentCount")
                    || positiveJsonNumber(metrics, "operatingRisk")
                    || positiveJsonNumber(metrics, "weatherRiskCount")
                    || positiveJsonNumber(metrics, "missionRate")
                    || positiveJsonNumber(metrics, "complaintCount")
                    || positiveJsonNumber(metrics, "complaintResolvedCount")
                    || positiveJsonNumber(metrics, "workerCount")
                    || positiveJsonNumber(metrics, "assignedWorkerCount")
                    || positiveJsonNumber(metrics, "requiredWorkerCount")
                    || positiveJsonNumber(metrics, "trainedWorkerCount")
                    || positiveJsonNumber(metrics, "environmentScore")
                    || positiveJsonNumber(metrics, "socialScore")
                    || positiveJsonNumber(metrics, "governanceScore")
                    || positiveJsonNumber(metrics, "totalScore");
        } catch (Exception ignored) {
            return false;
        }
    }

    private boolean positiveJsonNumber(JsonNode node, String fieldName) {
        JsonNode value = node.path(fieldName);
        return value.isNumber() && value.asDouble() > 0.0;
    }

    private int snapshotInteger(Integer value) {
        return value != null ? Math.max(0, value) : 0;
    }

    private double normalizeCumulativeSnapshotScore(Double value) {
        if (value == null || value.isNaN() || value.isInfinite()) {
            return 0.0;
        }
        return Math.max(0.0, value);
    }

    private boolean hasEsgOperationData(ZoneMetrics metrics, boolean hasMeaningfulMetricInput) {
        return metrics.totalEquipmentCount > 0
                || metrics.reportCount > 0
                || hasMeaningfulMetricInput;
    }

    private boolean hasMeaningfulSupportMetricInput(EsgMetricInput input, String zoneName) {
        if (input == null) {
            return false;
        }

        if ("민원 구역".equals(zoneName)) {
            return positive(input.getComplaintCount())
                    || positive(input.getComplaintResolvedCount());
        }

        if ("세척장".equals(zoneName)) {
            return positive(input.getCarbonKg())
                    || positive(input.getWashWaterLiters())
                    || positive(input.getWastewaterLiters())
                    || positive(input.getWastewaterRecoveryRate())
                    || positive(input.getPowerUsageKwh())
                    || positive(input.getPowerSavingKwh())
                    || positive(input.getSafetyEducationRate())
                    || positive(input.getStaffingRate())
                    || positive(input.getReportRate())
                    || positive(input.getActionTrackingRate())
                    || positive(input.getDataLinkRate());
        }

        return true;
    }

    private boolean positive(Number value) {
        return value != null && Double.isFinite(value.doubleValue()) && value.doubleValue() > 0.0;
    }

    private String buildEquipmentSummary(List<EquipmentRow> equipments) {
        Map<String, Integer> equipmentCounts = new LinkedHashMap<>();
        for (EquipmentRow row : equipments) {
            equipmentCounts.merge(
                    firstNonBlank(row.equipmentName(), "중장비"),
                    normalizeCount(row.equipmentCount()),
                    Integer::sum
            );
        }
        return equipmentCounts.entrySet().stream()
                .map(entry -> entry.getKey() + " " + entry.getValue() + "대")
                .collect(Collectors.joining(", "));
    }

    private String buildGateSummary(List<EquipmentRow> equipments) {
        return equipments.stream()
                .map(EquipmentRow::gateIdx)
                .filter(Objects::nonNull)
                .distinct()
                .sorted()
                .map(gateIdx -> gateIdx + "번 게이트")
                .collect(Collectors.joining(", "));
    }

    private String buildWorkZoneType(List<EquipmentRow> equipments, String equipmentSummary) {
        String titleSummary = equipments.stream()
                .map(EquipmentRow::title)
                .filter(value -> !isBlank(value))
                .distinct()
                .limit(2)
                .collect(Collectors.joining(" · "));
        if (!isBlank(titleSummary)) {
            return titleSummary;
        }

        String detailSummary = equipments.stream()
                .map(EquipmentRow::workDetail)
                .filter(value -> !isBlank(value))
                .findFirst()
                .orElse("");
        if (!isBlank(detailSummary)) {
            return detailSummary;
        }

        return !isBlank(equipmentSummary) ? equipmentSummary : "작업지시서 연동 구역";
    }

    private ReportMetrics buildReportMetrics(
            List<DailyReport> reports,
            List<EquipmentRow> equipments,
            String zoneName,
            int weatherRiskCount
    ) {
        List<DailyReport> scopedReports = scopeReportsByZone(reports, zoneName);

        Set<Long> workOrderRefs = equipments.stream()
                .map(EquipmentRow::workOrderIdx)
                .filter(Objects::nonNull)
                .collect(Collectors.toCollection(LinkedHashSet::new));
        int expectedReportCount = !workOrderRefs.isEmpty() ? workOrderRefs.size() : (!equipments.isEmpty() ? 1 : 0);

        double reportRate = expectedReportCount > 0
                ? Math.min(100.0, Math.round((scopedReports.size() / (double) expectedReportCount) * 100.0))
                : !scopedReports.isEmpty() ? 100.0 : 0.0;

        List<Double> progressValues = scopedReports.stream()
                .map(report -> firstFinite(
                        report.getTodayProgress(),
                        report.getProgressIncrementPct(),
                        report.getActualProgress()
                ))
                .filter(Objects::nonNull)
                .map(this::clampScore)
                .toList();

        double actionTrackingRate = progressValues.isEmpty()
                ? 0.0
                : Math.round(progressValues.stream().mapToDouble(Double::doubleValue).average().orElse(0.0));

        return new ReportMetrics(
                reportRate,
                weatherRiskCount > 0 || !scopedReports.isEmpty() ? actionTrackingRate : 0.0
        );
    }

    private List<DailyReport> scopeReportsByZone(List<DailyReport> reports, String zoneName) {
        List<DailyReport> safeReports = reports != null ? reports : List.of();
        String cleanZone = normalizeText(zoneName);

        if (isBlank(cleanZone)) {
            return safeReports;
        }

        if ("세척장".equals(cleanZone)) {
            return safeReports.stream()
                    .filter(report -> reportMatchesAny(report, List.of("세척장", "세륜", "세척")))
                    .toList();
        }

        if ("민원 구역".equals(cleanZone)) {
            return safeReports.stream()
                    .filter(report -> reportMatchesAny(report, List.of("민원", "주민", "비산", "분진", "소음")))
                    .toList();
        }

        return safeReports.stream()
                .filter(report -> {
                    String location = firstNonBlank(report.getLocation(), "");
                    return cleanZone.equals(location) || reportMatchesAny(report, List.of(cleanZone));
                })
                .toList();
    }

    private boolean reportMatchesAny(DailyReport report, List<String> keywords) {
        String text = String.join(" ",
                safeText(report != null ? report.getLocation() : null),
                safeText(report != null ? report.getIssue() : null),
                safeText(report != null ? report.getTodayWork() : null),
                safeText(report != null ? report.getTomorrowPlan() : null)
        );
        return containsAny(text, keywords);
    }

    private double buildDefaultDataLinkRate(
            WeatherContext weather,
            List<EquipmentRow> equipments,
            List<DailyReport> reports,
            ZoneMetrics metrics
    ) {
        List<Boolean> checks = List.of(
                weather.available(),
                !equipments.isEmpty(),
                !reports.isEmpty(),
                !equipments.isEmpty(),
                metrics.staffingRate > 0.0 || metrics.safetyEducationRate > 0.0
        );
        long linked = checks.stream().filter(Boolean::booleanValue).count();
        return Math.round((linked / (double) checks.size()) * 100.0);
    }

    private int calculateMissionRate(List<EquipmentRow> equipments, LocalDate reportDate) {
        if (equipments == null || equipments.isEmpty()) {
            return 0;
        }
        long working = equipments.stream()
                .filter(row -> "작업중".equals(resolveEquipmentStatus(row.statusCode(), row.workDate(), reportDate)))
                .count();
        return clampInt(Math.round((working / (double) equipments.size()) * 100.0), 0, 100);
    }

    private String resolveEquipmentStatus(String statusCode, LocalDate workDate, LocalDate reportDate) {
        if ("COMPLETED".equalsIgnoreCase(statusCode) || "DONE".equalsIgnoreCase(statusCode)) {
            return "작업중";
        }
        if (workDate != null && reportDate != null && workDate.isBefore(reportDate)) {
            return "작업중";
        }
        return "입차예정";
    }

    private int countDustWorkOrders(List<EquipmentRow> equipments) {
        Set<String> keys = new LinkedHashSet<>();
        int sequence = 0;
        for (EquipmentRow row : equipments) {
            String merged = safeText(row.workDetail()) + " " + safeText(row.title()) + " " + safeText(row.equipmentName());
            if (!containsAny(merged, DUST_WORK_KEYWORDS)) {
                sequence++;
                continue;
            }
            String key = row.workOrderIdx() != null
                    ? String.valueOf(row.workOrderIdx())
                    : safeText(row.workDate()) + "|" + safeText(row.workLocation()) + "|" + safeText(row.title()) + "|" + sequence;
            keys.add(key);
            sequence++;
        }
        return keys.size();
    }

    private boolean isHighRiskEquipment(String equipmentName) {
        String safeName = safeText(equipmentName);
        return HIGH_RISK_EQUIPMENT_KEYWORDS.stream().anyMatch(safeName::contains);
    }

    private double getEquipmentCarbonFactor(String equipmentName) {
        String safeName = safeText(equipmentName);
        return CARBON_FACTORS.stream()
                .filter(factor -> factor.keywords().stream().anyMatch(safeName::contains))
                .mapToDouble(CarbonFactor::factor)
                .findFirst()
                .orElse(1.0);
    }

    private boolean containsAny(String text, Collection<String> keywords) {
        String safeText = safeText(text);
        return keywords.stream()
                .filter(keyword -> !isBlank(keyword))
                .anyMatch(safeText::contains);
    }

    private double buildTotalScore(ZoneMetrics metrics) {
        return clampScore(
                (metrics.environmentScore * CATEGORY_E_WEIGHT
                        + metrics.socialScore * CATEGORY_S_WEIGHT
                        + metrics.governanceScore * CATEGORY_G_WEIGHT) / 100.0
        );
    }

    private double buildWeightedMetricScore(double... scores) {
        double weightSum = METRIC_WEIGHTS.stream().mapToDouble(Double::doubleValue).sum();
        if (weightSum <= 0.0) {
            return 0.0;
        }

        double total = 0.0;
        for (int index = 0; index < METRIC_WEIGHTS.size(); index++) {
            double score = index < scores.length ? clampScore(scores[index]) : 0.0;
            total += score * METRIC_WEIGHTS.get(index);
        }
        return clampScore(total / weightSum);
    }

    private double average(List<ZoneRefreshModel> zones, java.util.function.ToDoubleFunction<ZoneRefreshModel> selector) {
        if (zones == null || zones.isEmpty()) {
            return 0.0;
        }
        return clampScore(zones.stream().mapToDouble(selector).average().orElse(0.0));
    }

    private boolean isActiveProject(Project project, LocalDate reportDate) {
        if (project == null || reportDate == null) {
            return false;
        }
        return project.getEndDate() == null || !project.getEndDate().isBefore(reportDate);
    }

    private boolean isToday(LocalDate reportDate) {
        return reportDate != null && reportDate.equals(LocalDate.now());
    }

    private int calculateSafetyDays(Project project, LocalDate reportDate) {
        if (project == null || project.getStartDate() == null || reportDate == null || reportDate.isBefore(project.getStartDate())) {
            return 1;
        }
        long days = ChronoUnit.DAYS.between(project.getStartDate(), reportDate) + 1;
        return (int) Math.max(1, days);
    }

    private ScoreProgress advanceFloorProgress(
            Double storedPointScore,
            Integer storedLevel,
            Double earnedScore,
            double floorPoint
    ) {
        double safeFloorPoint = floorPoint > 0.0 ? floorPoint : SITE_FLOOR_POINT;
        double currentPointScore = normalizeFloorPointScore(storedPointScore, safeFloorPoint);
        int currentLevel = resolveStoredLevel(storedPointScore, storedLevel, safeFloorPoint);
        double dailyScore = clampScore(earnedScore);
        double mergedPointScore = currentPointScore + dailyScore;
        int increasedLevel = (int) Math.floor(mergedPointScore / safeFloorPoint);
        double nextPointScore = roundOne(mergedPointScore % safeFloorPoint);
        return new ScoreProgress(nextPointScore, currentLevel + increasedLevel);
    }

    private double normalizeFloorPointScore(Double value, double floorPoint) {
        double score = normalizeCumulativeScore(value);
        if (floorPoint <= 0.0) {
            return score;
        }
        return roundOne(score % floorPoint);
    }

    private int resolveStoredLevel(Double storedPointScore, Integer storedLevel, double floorPoint) {
        int normalizedLevel = storedLevel == null ? 0 : Math.max(0, storedLevel);
        double normalizedScore = normalizeCumulativeScore(storedPointScore);
        if (normalizedLevel == 0 && floorPoint > 0.0 && normalizedScore >= floorPoint) {
            return (int) Math.floor(normalizedScore / floorPoint);
        }
        return normalizedLevel;
    }

    private double normalizeCumulativeScore(Double value) {
        if (value == null || value.isNaN() || value.isInfinite()) {
            return 0.0;
        }
        return Math.max(0.0, roundOne(value));
    }

    private Double firstFinite(Number... values) {
        if (values == null) {
            return null;
        }
        for (Number value : values) {
            if (value == null) {
                continue;
            }
            double number = value.doubleValue();
            if (Double.isFinite(number)) {
                return number;
            }
        }
        return null;
    }

    private double safeDouble(Number value) {
        Double safe = firstFinite(value);
        return safe != null ? safe : 0.0;
    }

    private double parseDouble(String value) {
        if (isBlank(value)) {
            return 0.0;
        }
        try {
            return Double.parseDouble(value.trim());
        } catch (NumberFormatException ignored) {
            return 0.0;
        }
    }

    private int sizeOf(Collection<?> values) {
        return values != null ? values.size() : 0;
    }

    private int normalizeCount(Integer value) {
        if (value == null || value <= 0) {
            return 1;
        }
        return value;
    }

    private int defaultInteger(Integer value, int fallback) {
        return value != null ? value : fallback;
    }

    private Long toLong(Object[] row, int index) {
        if (row == null || index < 0 || index >= row.length || row[index] == null) {
            return null;
        }
        Object value = row[index];
        if (value instanceof Number number) {
            return number.longValue();
        }
        return Long.parseLong(value.toString());
    }

    private Integer toInteger(Object[] row, int index) {
        if (row == null || index < 0 || index >= row.length || row[index] == null) {
            return null;
        }
        Object value = row[index];
        if (value instanceof Number number) {
            return number.intValue();
        }
        return Integer.parseInt(value.toString());
    }

    private LocalDate toLocalDate(Object[] row, int index) {
        if (row == null || index < 0 || index >= row.length || row[index] == null) {
            return null;
        }
        Object value = row[index];
        if (value instanceof LocalDate localDate) {
            return localDate;
        }
        if (value instanceof Date date) {
            return date.toLocalDate();
        }
        return LocalDate.parse(value.toString());
    }

    private String toStringValue(Object[] row, int index) {
        if (row == null || index < 0 || index >= row.length || row[index] == null) {
            return "";
        }
        return row[index].toString();
    }

    private String resolveZoneStatus(int risk) {
        if (risk >= 7) {
            return "위험";
        }
        if (risk >= 4) {
            return "관리";
        }
        return "우수";
    }

    private double clampScore(Double value) {
        return clampScore(value != null ? value.doubleValue() : 0.0);
    }

    private double clampScore(double value) {
        return Math.max(0.0, Math.min(100.0, roundOne(value)));
    }

    private int clampInt(long value, int min, int max) {
        return (int) Math.max(min, Math.min(max, value));
    }

    private double roundOne(double value) {
        return Math.round(value * 10.0) / 10.0;
    }

    private String firstNonBlank(String... values) {
        if (values == null) {
            return "";
        }
        for (String value : values) {
            if (!isBlank(value)) {
                return value.trim();
            }
        }
        return "";
    }

    private String normalizeText(String value) {
        return value != null ? value.trim() : "";
    }

    private String safeText(Object value) {
        return value != null ? String.valueOf(value) : "";
    }

    private boolean isBlank(String value) {
        return value == null || value.isBlank();
    }

    private Double nullableDouble(JsonNode node, String field) {
        JsonNode value = node.path(field);
        return value.isNumber() ? value.doubleValue() : null;
    }

    private Integer nullableInt(JsonNode node, String field) {
        JsonNode value = node.path(field);
        return value.isNumber() ? value.intValue() : null;
    }

    private record EquipmentRow(
            Long idx,
            Long workOrderIdx,
            String title,
            String tradeType,
            String workDetail,
            LocalDate workDate,
            String workLocation,
            String workPlanName,
            Integer gateIdx,
            String equipmentName,
            Integer equipmentCount,
            String statusCode,
            Long siteIdx
    ) {
    }

    private record CarbonFactor(
            List<String> keywords,
            double factor
    ) {
    }

    private record WeatherContext(
            boolean available,
            double precipitationProbability,
            double maxWindSpeed,
            double fineDustValue,
            int weatherRiskCount
    ) {
        private static WeatherContext empty() {
            return new WeatherContext(false, 0.0, 0.0, 0.0, 0);
        }
    }

    private record ReportMetrics(
            double reportRate,
            double actionTrackingRate
    ) {
    }

    private record ScoreProgress(
            Double pointScore,
            Integer level
    ) {
    }

    private record StoredZoneMetricHints(
            Double fineDustValue,
            Double estimatedCarbonKg,
            Double powerSavingKwh,
            Double staffingRate,
            Double safetyEducationRate,
            Double reportRate,
            Double actionTrackingRate,
            Double dataLinkRate,
            Integer complaintCount,
            Integer complaintResolvedCount,
            Integer workerCount,
            Integer assignedWorkerCount,
            Integer requiredWorkerCount,
            Integer trainedWorkerCount
    ) {
        private static StoredZoneMetricHints empty() {
            return new StoredZoneMetricHints(
                    null, null, null, null, null, null, null, null,
                    null, null, null, null, null, null
            );
        }
    }

    private record ZoneRefreshModel(
            String id,
            String zoneName,
            String type,
            String zoneType,
            double dailyScore,
            ZoneMetrics metrics,
            double carbonKg,
            double powerSavingKwh,
            int riskCount,
            int missionRate,
            int equipmentCount,
            int highRiskEquipmentCount,
            String equipmentSummary,
            String gateSummary,
            String status,
            int rank,
            double lead
    ) {
        private ZoneRefreshModel(
                String id,
                String zoneName,
                String type,
                String zoneType,
                double dailyScore,
                ZoneMetrics metrics,
                double carbonKg,
                double powerSavingKwh,
                int riskCount,
                int missionRate,
                int equipmentCount,
                int highRiskEquipmentCount,
                String equipmentSummary,
                String gateSummary,
                String status
        ) {
            this(
                    id,
                    zoneName,
                    type,
                    zoneType,
                    dailyScore,
                    metrics,
                    carbonKg,
                    powerSavingKwh,
                    riskCount,
                    missionRate,
                    equipmentCount,
                    highRiskEquipmentCount,
                    equipmentSummary,
                    gateSummary,
                    status,
                    0,
                    0.0
            );
        }

        private ZoneRefreshModel withRank(int rank, double lead) {
            return new ZoneRefreshModel(
                    id,
                    zoneName,
                    type,
                    zoneType,
                    dailyScore,
                    metrics,
                    carbonKg,
                    powerSavingKwh,
                    riskCount,
                    missionRate,
                    equipmentCount,
                    highRiskEquipmentCount,
                    equipmentSummary,
                    gateSummary,
                    status,
                    rank,
                    lead
            );
        }
    }

    private static final class ZoneMetrics {
        private int totalEquipmentCount;
        private int highRiskEquipmentCount;
        private int washTargetCount;
        private int workLocationCount;
        private int gateCount;
        private int assignedGateEquipmentCount;
        private int maxGateEquipmentCount;
        private int gateConcentrationRate;
        private int gateCongestionRisk;
        private double idleReductionScore;
        private int dustWorkCount;
        private int weatherRiskCount;
        private int missionRate;
        private double rainPercent;
        private double windSpeed;
        private Double fineDustValue;
        private int fineDustRiskLevel;
        private int complaintCount;
        private int complaintResolvedCount;
        private int unresolvedComplaintCount;
        private double complaintResolutionRate;
        private int complaintRisk;
        private double carbonLoadIndex;
        private double estimatedCarbonKg;
        private double carbonScore;
        private long estimatedWashWaterLiters;
        private int washWaterDemandRisk;
        private double waterScore;
        private double fineDustScore;
        private int powerPeakRisk;
        private double powerSavingKwh;
        private double powerScore;
        private double staffingRate;
        private double safetyEducationRate;
        private double weatherProtectionScore;
        private double routeSafetyScore;
        private double reportRate;
        private double actionTrackingRate;
        private double dataLinkRate;
        private int missingCheckCount;
        private double checkScore;
        private int workerCount;
        private int assignedWorkerCount;
        private int requiredWorkerCount;
        private int trainedWorkerCount;
        private double environmentScore;
        private double socialScore;
        private double governanceScore;
        private double totalScore;
        private int operatingRisk;
        private int reportCount;

        private void resetScoreFields() {
            environmentScore = 0.0;
            socialScore = 0.0;
            governanceScore = 0.0;
            totalScore = 0.0;
            carbonScore = 0.0;
            waterScore = 0.0;
            fineDustScore = 0.0;
            powerScore = 0.0;
            idleReductionScore = 0.0;
            weatherProtectionScore = 0.0;
            routeSafetyScore = 0.0;
            reportRate = 0.0;
            actionTrackingRate = 0.0;
            dataLinkRate = 0.0;
            checkScore = 0.0;
            if (complaintCount <= 0) {
                complaintResolutionRate = 0.0;
            }
        }

        private ZoneMetrics copy() {
            ZoneMetrics copy = new ZoneMetrics();
            copy.totalEquipmentCount = totalEquipmentCount;
            copy.highRiskEquipmentCount = highRiskEquipmentCount;
            copy.washTargetCount = washTargetCount;
            copy.workLocationCount = workLocationCount;
            copy.gateCount = gateCount;
            copy.assignedGateEquipmentCount = assignedGateEquipmentCount;
            copy.maxGateEquipmentCount = maxGateEquipmentCount;
            copy.gateConcentrationRate = gateConcentrationRate;
            copy.gateCongestionRisk = gateCongestionRisk;
            copy.idleReductionScore = idleReductionScore;
            copy.dustWorkCount = dustWorkCount;
            copy.weatherRiskCount = weatherRiskCount;
            copy.missionRate = missionRate;
            copy.rainPercent = rainPercent;
            copy.windSpeed = windSpeed;
            copy.fineDustValue = fineDustValue;
            copy.fineDustRiskLevel = fineDustRiskLevel;
            copy.complaintCount = complaintCount;
            copy.complaintResolvedCount = complaintResolvedCount;
            copy.unresolvedComplaintCount = unresolvedComplaintCount;
            copy.complaintResolutionRate = complaintResolutionRate;
            copy.complaintRisk = complaintRisk;
            copy.carbonLoadIndex = carbonLoadIndex;
            copy.estimatedCarbonKg = estimatedCarbonKg;
            copy.carbonScore = carbonScore;
            copy.estimatedWashWaterLiters = estimatedWashWaterLiters;
            copy.washWaterDemandRisk = washWaterDemandRisk;
            copy.waterScore = waterScore;
            copy.fineDustScore = fineDustScore;
            copy.powerPeakRisk = powerPeakRisk;
            copy.powerSavingKwh = powerSavingKwh;
            copy.powerScore = powerScore;
            copy.staffingRate = staffingRate;
            copy.safetyEducationRate = safetyEducationRate;
            copy.weatherProtectionScore = weatherProtectionScore;
            copy.routeSafetyScore = routeSafetyScore;
            copy.reportRate = reportRate;
            copy.actionTrackingRate = actionTrackingRate;
            copy.dataLinkRate = dataLinkRate;
            copy.missingCheckCount = missingCheckCount;
            copy.checkScore = checkScore;
            copy.workerCount = workerCount;
            copy.assignedWorkerCount = assignedWorkerCount;
            copy.requiredWorkerCount = requiredWorkerCount;
            copy.trainedWorkerCount = trainedWorkerCount;
            copy.environmentScore = environmentScore;
            copy.socialScore = socialScore;
            copy.governanceScore = governanceScore;
            copy.totalScore = totalScore;
            copy.operatingRisk = operatingRisk;
            copy.reportCount = reportCount;
            return copy;
        }

        private Map<String, Object> toMap() {
            Map<String, Object> metrics = new LinkedHashMap<>();
            metrics.put("totalEquipmentCount", totalEquipmentCount);
            metrics.put("highRiskEquipmentCount", highRiskEquipmentCount);
            metrics.put("washTargetCount", washTargetCount);
            metrics.put("workLocationCount", workLocationCount);
            metrics.put("gateCount", gateCount);
            metrics.put("assignedGateEquipmentCount", assignedGateEquipmentCount);
            metrics.put("maxGateEquipmentCount", maxGateEquipmentCount);
            metrics.put("gateConcentrationRate", gateConcentrationRate);
            metrics.put("gateCongestionRisk", gateCongestionRisk);
            metrics.put("idleReductionScore", idleReductionScore);
            metrics.put("dustWorkCount", dustWorkCount);
            metrics.put("weatherRiskCount", weatherRiskCount);
            metrics.put("missionRate", missionRate);
            metrics.put("rainPercent", rainPercent);
            metrics.put("windSpeed", windSpeed);
            metrics.put("fineDustValue", fineDustValue != null ? fineDustValue : 0.0);
            metrics.put("fineDustRiskLevel", fineDustRiskLevel);
            metrics.put("complaintCount", complaintCount);
            metrics.put("complaintResolvedCount", complaintResolvedCount);
            metrics.put("unresolvedComplaintCount", unresolvedComplaintCount);
            metrics.put("complaintResolutionRate", complaintResolutionRate);
            metrics.put("complaintRisk", complaintRisk);
            metrics.put("carbonLoadIndex", carbonLoadIndex);
            metrics.put("estimatedCarbonKg", estimatedCarbonKg);
            metrics.put("carbonScore", carbonScore);
            metrics.put("estimatedWashWaterLiters", estimatedWashWaterLiters);
            metrics.put("washWaterDemandRisk", washWaterDemandRisk);
            metrics.put("waterScore", waterScore);
            metrics.put("fineDustScore", fineDustScore);
            metrics.put("powerPeakRisk", powerPeakRisk);
            metrics.put("powerSavingKwh", powerSavingKwh);
            metrics.put("powerScore", powerScore);
            metrics.put("staffingRate", staffingRate);
            metrics.put("safetyEducationRate", safetyEducationRate);
            metrics.put("weatherProtectionScore", weatherProtectionScore);
            metrics.put("routeSafetyScore", routeSafetyScore);
            metrics.put("reportRate", reportRate);
            metrics.put("actionTrackingRate", actionTrackingRate);
            metrics.put("dataLinkRate", dataLinkRate);
            metrics.put("missingCheckCount", missingCheckCount);
            metrics.put("checkScore", checkScore);
            metrics.put("workerCount", workerCount);
            metrics.put("assignedWorkerCount", assignedWorkerCount);
            metrics.put("requiredWorkerCount", requiredWorkerCount);
            metrics.put("trainedWorkerCount", trainedWorkerCount);
            metrics.put("environmentScore", environmentScore);
            metrics.put("socialScore", socialScore);
            metrics.put("governanceScore", governanceScore);
            metrics.put("totalScore", totalScore);
            metrics.put("operatingRisk", operatingRisk);
            return metrics;
        }
    }
}
