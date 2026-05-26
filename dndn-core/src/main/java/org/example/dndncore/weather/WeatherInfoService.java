package org.example.dndncore.weather;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.RequiredArgsConstructor;
import lombok.Setter;
import lombok.extern.slf4j.Slf4j;
import org.example.dndncore.weather.model.WeatherInfo;
import org.example.dndncore.weather.model.WeatherInfoDto;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.util.UriComponentsBuilder;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.TreeMap;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Slf4j
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class WeatherInfoService {

    private static final DateTimeFormatter BASIC_DATE = DateTimeFormatter.BASIC_ISO_DATE;
    private static final DateTimeFormatter HHMM = DateTimeFormatter.ofPattern("HH:mm");
    private static final int VILLAGE_PAGE_SIZE = 1000;
    private static final int MAX_VILLAGE_PAGE_REQUESTS = 5;
    private static final int MIN_WEEKLY_FORECAST_DAYS = 7;
    private static final int MAX_FORECAST_DISPLAY_DAYS = 14;
    private static final int STALE_FORECAST_FINALIZE_LOOKBACK_DAYS = 14;
    private static final String FORECAST_OUT_OF_RANGE_LABEL = "예보 범위 외";

    private final WeatherInfoRepository weatherInfoRepository;
    private final WeatherSnapshotWriter weatherSnapshotWriter;
    private final AirKoreaClient airKoreaClient;

    @Value("${weather.kma.service-key}")
    private String serviceKey;

    @Value("${weather.kma.village-url}")
    private String villageUrl;

    @Value("${weather.kma.mid-land-url}")
    private String midLandUrl;

    @Value("${weather.kma.mid-temp-url}")
    private String midTempUrl;

    @Value("${weather.kma.warning-url}")
    private String warningUrl;

    @Value("${weather.kma.asos-url}")
    private String asosUrl;

    @Value("${weather.kma.nx}")
    private int nx;

    @Value("${weather.kma.ny}")
    private int ny;

    @Value("${weather.kma.mid-land-reg-id}")
    private String midLandRegId;

    @Value("${weather.kma.mid-temp-reg-id}")
    private String midTempRegId;

    @Value("${weather.kma.stn-id}")
    private String stnId;

    @Value("${weather.kma.location-label}")
    private String locationLabel;

    private final RestTemplate restTemplate = createRestTemplate();
    private final ObjectMapper objectMapper = new ObjectMapper();

    private RestTemplate createRestTemplate() {
        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(10_000);
        factory.setReadTimeout(20_000);
        return new RestTemplate(factory);
    }

    // 기상 관제 대시보드 단건 조회
    public WeatherInfoDto.DashboardRes readDashboard(LocalDate reportDate) {
        LocalDate targetDate = reportDate != null ? reportDate : LocalDate.now();

        // 화면 조회는 어떤 날짜든 DB snapshot만 읽는다.
        // 외부 기상 API 호출은 정기 스케줄러의 당일 갱신 흐름에서만 수행한다.
        return loadSnapshotOrFallback(targetDate);
    }

    // 작업일보 자동 기입
    public WeatherInfoDto.TodaySimpleRes readTodaySimple(LocalDate reportDate) {
        return readDashboard(reportDate).toTodaySimpleRes();
    }


    public boolean hasFreshTodaySnapshot(LocalDate targetDate) {
        LocalDate today = LocalDate.now();
        if (targetDate == null || !targetDate.equals(today)) {
            return false;
        }

        return weatherInfoRepository.findByReportDate(targetDate)
                .map(snapshot -> snapshot.getUpdatedAt() != null
                        && snapshot.getUpdatedAt().toLocalDate().equals(today))
                .orElse(false);
    }

    // 스케줄러용 강제 갱신
    @Transactional(propagation = Propagation.NOT_SUPPORTED)
    public void refreshSnapshot(LocalDate targetDate) {
        if (targetDate == null || !targetDate.equals(LocalDate.now())) {
            return;
        }

        refreshTodaySnapshotAndAvailableForecasts();
    }

    // 스케줄러 전용: 오늘 외부 기상 API는 1회만 호출하고,
    // 그 응답으로 확보한 예보 범위의 미래 날짜 snapshot을 DB에 같이 저장한다.
    @Transactional(propagation = Propagation.NOT_SUPPORTED)
    public void refreshTodaySnapshotAndAvailableForecasts() {
        LocalDate today = LocalDate.now();
        WeatherInfoDto.AirQualityCard todayAirQuality = WeatherInfoDto.AirQualityCard.empty();

        log.info("[기상 스냅샷] 당일 예보 갱신 흐름 시작 - date={}", today);

        try {
            List<WeatherInfoDto.AlertItem> todayAlerts = safeFetchAlerts(today);
            todayAirQuality = safeFetchTodayAirQuality(today);

            Map<LocalDate, DayWeather> villageForecastMap = safeFetchVillageForecastMap(today);
            Map<LocalDate, DayWeather> midForecastMap = safeFetchMidForecastMap(today);

            Map<LocalDate, DayWeather> forecastMap = new TreeMap<>();
            forecastMap.putAll(villageForecastMap);
            forecastMap.putAll(midForecastMap);

            log.info("[기상 스냅샷] 예보 수집 완료 - 단기={}일, 중기={}일, 전체={}일, dates={}",
                    villageForecastMap.size(),
                    midForecastMap.size(),
                    forecastMap.size(),
                    forecastMap.keySet());

            if (forecastMap.isEmpty()) {
                saveTodayFallbackSnapshot(today, todayAirQuality, "저장 가능한 예보 데이터 없음");
                finalizeStalePastForecastSnapshots(today);
                return;
            }

            if (forecastMap.containsKey(today)) {
                refreshForecastSnapshot(today, forecastMap, todayAlerts, todayAirQuality);
            } else {
                saveTodayFallbackSnapshot(today, todayAirQuality, "당일 단기예보 데이터 없음");
            }

            WeatherInfoDto.AirQualityCard forecastAirQuality = isAvailableAirQuality(todayAirQuality)
                    ? todayAirQuality
                    : WeatherInfoDto.AirQualityCard.empty();

            log.info("[AirKorea] 미래 예보 스냅샷 미세먼지 반영 기준 - dateRangeStart={}, available={}, pm10={}, grade={}",
                    today.plusDays(1),
                    forecastAirQuality.isAvailable(),
                    forecastAirQuality.getValue(),
                    forecastAirQuality.getGrade());

            forecastMap.keySet().stream()
                    .filter(date -> date.isAfter(today))
                    .sorted()
                    .forEach(date -> refreshForecastSnapshot(
                            date,
                            forecastMap,
                            new ArrayList<>(),
                            forecastAirQuality
                    ));

            finalizeStalePastForecastSnapshots(today);
        } catch (Exception e) {
            log.warn("[기상 스냅샷] 당일 예보 갱신 흐름 예외 발생 - fallback 저장 진행 - date={}, reason={}",
                    today,
                    summarizeException(e));
            saveTodayFallbackSnapshot(today, todayAirQuality, "당일 갱신 흐름 예외");
            finalizeStalePastForecastSnapshots(today);
        }
    }

    private List<WeatherInfoDto.AlertItem> safeFetchAlerts(LocalDate today) {
        try {
            return fetchAlerts();
        } catch (Exception e) {
            log.warn("[기상 스냅샷] 기상특보 수집 실패 - 빈 특보로 진행 - date={}, reason={}",
                    today,
                    summarizeException(e));
            return new ArrayList<>();
        }
    }

    private WeatherInfoDto.AirQualityCard safeFetchTodayAirQuality(LocalDate today) {
        try {
            WeatherInfoDto.AirQualityCard airQualityCard = airKoreaClient.fetchSidoPm10();
            WeatherInfoDto.AirQualityCard result = airQualityCard != null ? airQualityCard : WeatherInfoDto.AirQualityCard.empty();
            log.info("[AirKorea] 당일 미세먼지 적용값 확인 - date={}, available={}, pm10={}, grade={}",
                    today,
                    result.isAvailable(),
                    result.getValue(),
                    result.getGrade());
            return result;
        } catch (Exception e) {
            log.warn("[AirKorea] 미세먼지 수집 실패 - 빈 대기질로 진행 - date={}, reason={}",
                    today,
                    summarizeException(e));
            return WeatherInfoDto.AirQualityCard.empty();
        }
    }

    private Map<LocalDate, DayWeather> safeFetchVillageForecastMap(LocalDate today) {
        try {
            return fetchVillageForecastMap();
        } catch (Exception e) {
            log.warn("[기상 스냅샷] 단기예보 수집 실패 - fallback 저장 진행 - date={}, reason={}",
                    today,
                    summarizeException(e));
            return new LinkedHashMap<>();
        }
    }

    private Map<LocalDate, DayWeather> safeFetchMidForecastMap(LocalDate today) {
        try {
            return fetchMidForecastMap();
        } catch (Exception e) {
            log.warn("[기상 스냅샷] 중기예보 수집 실패 - 중기예보 없이 진행 - date={}, reason={}",
                    today,
                    summarizeException(e));
            return new LinkedHashMap<>();
        }
    }

    private void saveTodayFallbackSnapshot(
            LocalDate today,
            WeatherInfoDto.AirQualityCard todayAirQuality,
            String reason
    ) {
        WeatherInfoDto.DashboardRes fallback = buildFallbackDashboard(today);
        if (isAvailableAirQuality(todayAirQuality)) {
            fallback = withAirQuality(fallback, todayAirQuality);
        }

        log.warn("[기상 스냅샷] 당일 fallback snapshot 저장 요청 - date={}, reason={}, airAvailable={}, pm10={}",
                today,
                reason,
                fallback.getAirQuality() != null && fallback.getAirQuality().isAvailable(),
                fallback.getAirQuality() != null ? fallback.getAirQuality().getValue() : null);
        weatherSnapshotWriter.save(today, locationLabel, fallback);
    }

    private void refreshForecastSnapshot(
            LocalDate targetDate,
            Map<LocalDate, DayWeather> forecastMap,
            List<WeatherInfoDto.AlertItem> alerts,
            WeatherInfoDto.AirQualityCard airQualityCard
    ) {
        try {
            WeatherInfo cached = weatherInfoRepository.findByReportDate(targetDate).orElse(null);
            WeatherInfoDto.DashboardRes cachedDashboard = cached != null ? fromSnapshot(cached) : null;

            WeatherInfoDto.DashboardRes response = buildForecastDashboardFromFetchedData(
                    targetDate,
                    forecastMap,
                    alerts,
                    airQualityCard
            );
            response = mergeMissingAirQuality(response, cachedDashboard);
            saveSnapshotWithPolicy(targetDate, response, cachedDashboard);
        } catch (Exception e) {
            log.error("[기상 스냅샷] 날짜별 snapshot 갱신 실패 - targetDate={}", targetDate, e);
        }
    }

    private WeatherInfoDto.DashboardRes mergeMissingAirQuality(
            WeatherInfoDto.DashboardRes response,
            WeatherInfoDto.DashboardRes cachedDashboard
    ) {
        if (response == null || hasAvailableAirQuality(response)) {
            return response;
        }

        if (cachedDashboard == null || !hasAvailableAirQuality(cachedDashboard)) {
            return response;
        }

        return withAirQuality(response, cachedDashboard.getAirQuality());
    }

    private boolean hasAvailableAirQuality(WeatherInfoDto.DashboardRes dashboard) {
        return dashboard != null && isAvailableAirQuality(dashboard.getAirQuality());
    }

    private boolean isAvailableAirQuality(WeatherInfoDto.AirQualityCard airQuality) {
        return airQuality != null && airQuality.isAvailable() && airQuality.getValue() != null;
    }

    private WeatherInfoDto.DashboardRes withAirQuality(
            WeatherInfoDto.DashboardRes source,
            WeatherInfoDto.AirQualityCard airQuality
    ) {
        if (source == null) {
            return null;
        }

        Integer fineDustValue = airQuality != null ? airQuality.getValue() : null;

        return WeatherInfoDto.DashboardRes.builder()
                .reportDate(source.getReportDate())
                .locationLabel(source.getLocationLabel())
                .today(source.getToday())
                .week(source.getWeek())
                .rain(source.getRain())
                .airQuality(airQuality != null ? airQuality : WeatherInfoDto.AirQualityCard.empty())
                .analysis(withFineDust(source.getAnalysis(), fineDustValue))
                .equipmentRisks(source.getEquipmentRisks())
                .planRisks(source.getPlanRisks())
                .alerts(source.getAlerts())
                .forecastDays(source.getForecastDays())
                .build();
    }

    private WeatherInfoDto.WeatherAnalysis withFineDust(
            WeatherInfoDto.WeatherAnalysis analysis,
            Integer fineDustValue
    ) {
        if (analysis == null) {
            return null;
        }

        Integer appliedFineDustValue = fineDustValue != null ? fineDustValue : analysis.getFineDustValue();

        return WeatherInfoDto.WeatherAnalysis.builder()
                .reportDate(analysis.getReportDate())
                .sourceType(analysis.getSourceType())
                .outOfRange(analysis.isOutOfRange())
                .minTemperature(analysis.getMinTemperature())
                .maxTemperature(analysis.getMaxTemperature())
                .avgAmTemperature(analysis.getAvgAmTemperature())
                .avgPmTemperature(analysis.getAvgPmTemperature())
                .precipitationProbability(analysis.getPrecipitationProbability())
                .maxWindSpeed(analysis.getMaxWindSpeed())
                .fineDustValue(appliedFineDustValue)
                .fineDustRisk(appliedFineDustValue != null && appliedFineDustValue >= 80)
                .hasRain(analysis.isHasRain())
                .hasSnow(analysis.isHasSnow())
                .heatRisk(analysis.isHeatRisk())
                .coldRisk(analysis.isColdRisk())
                .windRisk(analysis.isWindRisk())
                .build();
    }

    private WeatherInfoDto.DashboardRes selectDashboardForResponse(
            WeatherInfoDto.DashboardRes response,
            WeatherInfoDto.DashboardRes cachedDashboard
    ) {
        if (response == null || isEmptyDashboard(response)) {
            return cachedDashboard != null ? cachedDashboard : response;
        }

        if (cachedDashboard != null && isAsosDaily(cachedDashboard)) {
            return cachedDashboard;
        }

        if (cachedDashboard != null && isConfirmedWeather(cachedDashboard) && !isConfirmedWeather(response)) {
            return cachedDashboard;
        }

        return response;
    }

    private void saveSnapshotWithPolicy(
            LocalDate targetDate,
            WeatherInfoDto.DashboardRes response,
            WeatherInfoDto.DashboardRes cachedDashboard
    ) {
        if (targetDate == null) {
            return;
        }

        if (response == null || response.getAnalysis() == null) {
            log.warn("[기상 스냅샷] 저장 생략 - 응답 또는 분석 정보 없음 - targetDate={}", targetDate);
            weatherSnapshotWriter.touch(targetDate, "응답 또는 분석 정보 없음");
            return;
        }

        if (isEmptyDashboard(response)) {
            log.warn("[기상 스냅샷] 저장 생략 - EMPTY dashboard - targetDate={}", targetDate);
            weatherSnapshotWriter.touch(targetDate, "EMPTY dashboard");
            return;
        }

        // 과거 날짜는 이미 확정된 저장값을 절대 덮지 않는다.
        if (targetDate.isBefore(LocalDate.now())) {
            log.info("[기상 스냅샷] 과거 날짜이므로 저장 생략 - targetDate={}", targetDate);
            return;
        }

        // ASOS는 과거 날짜의 실측 확정값이므로 더 낮은 신뢰도의 데이터로 덮지 않는다.
        if (cachedDashboard != null && isAsosDaily(cachedDashboard)) {
            log.info("[기상 스냅샷] 저장 생략 - 기존 ASOS 실측값 유지 - targetDate={}", targetDate);
            weatherSnapshotWriter.touch(targetDate, "기존 ASOS 실측값 유지");
            return;
        }

        // 오늘/미래 스냅샷은 스케줄러 갱신 결과를 저장한다.
        // 같은 값이어도 WeatherSnapshotWriter에서 updated_at을 강제로 touch하여
        // 실제 갱신 시각을 확인할 수 있게 한다.
        log.info("[기상 스냅샷] 저장 후보 확인 - targetDate={}, sourceType={}, cachedSourceType={}, wind={}, rain={}, airAvailable={}, pm10={}, forecastDays={}",
                targetDate,
                sourceTypeOf(response),
                sourceTypeOf(cachedDashboard),
                windSpeedOf(response),
                rainValueOf(response),
                response.getAirQuality() != null && response.getAirQuality().isAvailable(),
                response.getAirQuality() != null ? response.getAirQuality().getValue() : null,
                response.getForecastDays() != null ? response.getForecastDays().size() : 0);
        weatherSnapshotWriter.save(targetDate, locationLabel, response);
    }

    private void finalizeStalePastForecastSnapshots(LocalDate today) {
        if (today == null) {
            return;
        }

        LocalDate startDate = today.minusDays(STALE_FORECAST_FINALIZE_LOOKBACK_DAYS);
        LocalDate endDate = today.minusDays(1);

        if (endDate.isBefore(startDate)) {
            return;
        }

        List<WeatherInfo> snapshots = weatherInfoRepository.findAllByReportDateBetween(startDate, endDate);
        if (snapshots.isEmpty()) {
            log.info("[기상 스냅샷] 과거 최종값 보정 대상 없음 - startDate={}, endDate={}", startDate, endDate);
            return;
        }

        int checkedCount = 0;
        int finalizedCount = 0;
        int skippedCount = 0;
        int failedCount = 0;

        for (WeatherInfo snapshot : snapshots) {
            checkedCount++;

            if (!needsPastForecastFinalization(snapshot, today)) {
                skippedCount++;
                continue;
            }

            LocalDate targetDate = snapshot.getReportDate();
            WeatherInfoDto.DashboardRes cachedDashboard = fromSnapshot(snapshot);
            WeatherInfoDto.AirQualityCard cachedAirQuality = hasAvailableAirQuality(cachedDashboard)
                    ? cachedDashboard.getAirQuality()
                    : WeatherInfoDto.AirQualityCard.empty();

            AsosDay asosDay = fetchAsosDay(targetDate);
            if (asosDay == null) {
                failedCount++;
                log.warn("[기상 스냅샷] 과거 최종값 보정 실패 - ASOS 일자료 없음 - targetDate={}, previousSourceType={}, previousUpdatedAt={}",
                        targetDate,
                        sourceTypeOf(cachedDashboard),
                        snapshot.getUpdatedAt());
                continue;
            }

            WeatherInfoDto.DashboardRes finalizedDashboard = buildHistoricalDashboard(targetDate, asosDay, cachedAirQuality);
            log.info("[기상 스냅샷] 과거 최종값 보정 저장 - targetDate={}, previousSourceType={}, previousUpdatedAt={}, finalSourceType={}, wind={}, pm10={}",
                    targetDate,
                    sourceTypeOf(cachedDashboard),
                    snapshot.getUpdatedAt(),
                    sourceTypeOf(finalizedDashboard),
                    windSpeedOf(finalizedDashboard),
                    finalizedDashboard.getAirQuality() != null ? finalizedDashboard.getAirQuality().getValue() : null);

            weatherSnapshotWriter.save(targetDate, locationLabel, finalizedDashboard);
            finalizedCount++;
        }

        log.info("[기상 스냅샷] 과거 최종값 보정 완료 - startDate={}, endDate={}, checked={}, finalized={}, skipped={}, failed={}",
                startDate,
                endDate,
                checkedCount,
                finalizedCount,
                skippedCount,
                failedCount);
    }

    private boolean needsPastForecastFinalization(WeatherInfo snapshot, LocalDate today) {
        if (snapshot == null || snapshot.getReportDate() == null || !snapshot.getReportDate().isBefore(today)) {
            return false;
        }

        WeatherInfoDto.DashboardRes dashboard = fromSnapshot(snapshot);
        String sourceType = sourceTypeOf(dashboard);

        if ("ASOS_DAILY".equals(sourceType)) {
            return false;
        }

        LocalDate updatedDate = snapshot.getUpdatedAt() != null
                ? snapshot.getUpdatedAt().toLocalDate()
                : null;

        boolean savedBeforeTargetDate = updatedDate == null || updatedDate.isBefore(snapshot.getReportDate());
        boolean uncertainSource = sourceType == null
                || sourceType.isBlank()
                || "EMPTY".equals(sourceType)
                || "DERIVED".equals(sourceType);

        return savedBeforeTargetDate || uncertainSource;
    }

    private String sourceTypeOf(WeatherInfoDto.DashboardRes dashboard) {
        if (dashboard == null || dashboard.getAnalysis() == null) {
            return "NONE";
        }
        return dashboard.getAnalysis().getSourceType();
    }

    private Double windSpeedOf(WeatherInfoDto.DashboardRes dashboard) {
        if (dashboard == null || dashboard.getAnalysis() == null) {
            return null;
        }
        return dashboard.getAnalysis().getMaxWindSpeed();
    }

    private String rainValueOf(WeatherInfoDto.DashboardRes dashboard) {
        if (dashboard == null || dashboard.getRain() == null) {
            return "";
        }
        return dashboard.getRain().getValue();
    }

    private boolean isConfirmedWeather(WeatherInfoDto.DashboardRes dashboard) {
        if (dashboard == null || dashboard.getAnalysis() == null) {
            return false;
        }

        WeatherInfoDto.WeatherAnalysis analysis = dashboard.getAnalysis();

        if (analysis.isOutOfRange()) {
            return false;
        }

        String sourceType = analysis.getSourceType();

        return "KMA_FORECAST".equals(sourceType)
                || "KMA_MID".equals(sourceType)
                || "ASOS_DAILY".equals(sourceType);
    }

    private boolean isAsosDaily(WeatherInfoDto.DashboardRes dashboard) {
        if (dashboard == null || dashboard.getAnalysis() == null) {
            return false;
        }

        return "ASOS_DAILY".equals(dashboard.getAnalysis().getSourceType());
    }

    // 대시보드
    private WeatherInfoDto.DashboardRes buildDashboard(LocalDate targetDate) throws Exception {
        LocalDate today = LocalDate.now();
        List<WeatherInfoDto.AlertItem> alerts = targetDate.equals(today)
                ? fetchAlerts()
                : new ArrayList<>();

        WeatherInfoDto.AirQualityCard airQualityCard = targetDate.equals(today)
                ? airKoreaClient.fetchSidoPm10()
                : WeatherInfoDto.AirQualityCard.empty();

        if (targetDate.isBefore(today)) {
            AsosDay asosDay = fetchAsosDay(targetDate);
            if (asosDay == null) {
                return buildFallbackDashboard(targetDate);
            }
            return buildHistoricalDashboard(targetDate, asosDay, airQualityCard);
        }

        Map<LocalDate, DayWeather> forecastMap = new TreeMap<>();
        forecastMap.putAll(fetchVillageForecastMap());
        forecastMap.putAll(fetchMidForecastMap());

        return buildForecastDashboardFromFetchedData(targetDate, forecastMap, alerts, airQualityCard);
    }

    private WeatherInfoDto.DashboardRes buildForecastDashboardFromFetchedData(
            LocalDate targetDate,
            Map<LocalDate, DayWeather> forecastMap,
            List<WeatherInfoDto.AlertItem> alerts,
            WeatherInfoDto.AirQualityCard airQualityCard
    ) {
        DayWeather selectedDay = forecastMap.get(targetDate);
        List<WeatherInfoDto.ForecastDay> forecastDays = buildExtendedForecastDays(targetDate, forecastMap);

        if (selectedDay == null) {
            return buildForecastFallbackDashboard(targetDate, forecastDays, alerts, airQualityCard);
        }

        WeatherInfoDto.TodayCard todayCard = WeatherInfoDto.TodayCard.builder()
                .headlineTemp(buildHeadlineTemp(selectedDay.getMaxTemp(), selectedDay.getMinTemp()))
                .summary(defaultString(selectedDay.getSummary(), "기상정보 없음"))
                .amLabel(defaultString(selectedDay.getAmLabel(), "기상정보 없음"))
                .pmLabel(defaultString(selectedDay.getPmLabel(), "기상정보 없음"))
                .observedAt(LocalTime.now().format(HHMM))
                .build();

        WeatherInfoDto.WeekCard weekCard = WeatherInfoDto.WeekCard.builder()
                .summary(buildWindSummary(selectedDay, alerts))
                .subSummary(buildWindGuide(selectedDay))
                .build();

        int todayMaxPop = defaultInt(selectedDay.getPrecipitationProbability());

        WeatherInfoDto.RainCard rainCard = WeatherInfoDto.RainCard.builder()
                .label("강수확률")
                .value(todayMaxPop + "%")
                .build();

        Integer fineDustValue = airQualityCard != null ? airQualityCard.getValue() : null;
        boolean fineDustRisk = fineDustValue != null && fineDustValue >= 80;

        List<WeatherInfoDto.RiskItem> equipmentRisks = buildEquipmentRisks(selectedDay, alerts, fineDustValue);
        List<WeatherInfoDto.RiskItem> planRisks = buildPlanRisks(selectedDay, alerts, fineDustValue);

        WeatherInfoDto.WeatherAnalysis analysis = WeatherInfoDto.WeatherAnalysis.builder()
                .reportDate(targetDate.toString())
                .sourceType("KMA_FORECAST")
                .outOfRange(false)
                .minTemperature(selectedDay.getMinTemp())
                .maxTemperature(selectedDay.getMaxTemp())
                .avgAmTemperature(extractTemperatureFromLabel(selectedDay.getAmLabel()))
                .avgPmTemperature(extractTemperatureFromLabel(selectedDay.getPmLabel()))
                .precipitationProbability(defaultInt(selectedDay.getPrecipitationProbability()))
                .maxWindSpeed(selectedDay.getMaxWindSpeed())
                .fineDustValue(fineDustValue)
                .fineDustRisk(fineDustRisk)
                .hasRain(selectedDay.isHasRain())
                .hasSnow(selectedDay.isHasSnow())
                .heatRisk(selectedDay.getMaxTemp() != null && selectedDay.getMaxTemp() >= 33)
                .coldRisk(selectedDay.getMinTemp() != null && selectedDay.getMinTemp() <= -5)
                .windRisk(selectedDay.getMaxWindSpeed() != null && selectedDay.getMaxWindSpeed() >= 8)
                .build();

        return WeatherInfoDto.DashboardRes.builder()
                .reportDate(targetDate.toString())
                .locationLabel(locationLabel)
                .today(todayCard)
                .week(weekCard)
                .rain(rainCard)
                .airQuality(airQualityCard != null ? airQualityCard : WeatherInfoDto.AirQualityCard.empty())
                .analysis(analysis)
                .equipmentRisks(equipmentRisks)
                .planRisks(planRisks)
                .alerts(alerts)
                .forecastDays(forecastDays)
                .build();
    }

    private WeatherInfoDto.DashboardRes buildHistoricalDashboard(
            LocalDate targetDate,
            AsosDay asosDay,
            WeatherInfoDto.AirQualityCard airQualityCard
    ) {
        WeatherInfoDto.TodayCard todayCard = WeatherInfoDto.TodayCard.builder()
                .headlineTemp(buildHeadlineTemp(roundInt(asosDay.getMaxTa()), roundInt(asosDay.getMinTa())))
                .summary(asosDay.getSummary())
                .amLabel(asosDay.getAmLabel())
                .pmLabel(asosDay.getPmLabel())
                .observedAt("ASOS 일자료")
                .build();

        WeatherInfoDto.WeekCard weekCard = WeatherInfoDto.WeekCard.builder()
                .summary("선택 날짜 실측값 기준 요약")
                .subSummary("최대 순간풍속 " + formatDouble(defaultDouble(asosDay.getMaxInsWs())) + "m/s · 일강수량 " + formatDouble(defaultDouble(asosDay.getSumRn())) + "mm")
                .build();

        int rainPercent = defaultDouble(asosDay.getSumRn()) > 0 ? 100 : 0;

        WeatherInfoDto.RainCard rainCard = WeatherInfoDto.RainCard.builder()
                .label("강수이력")
                .value(rainPercent + "%")
                .build();

        DayWeather selectedDay = DayWeather.builder()
                .summary(asosDay.getSummary())
                .amLabel(asosDay.getAmLabel())
                .pmLabel(asosDay.getPmLabel())
                .minTemp(roundInt(asosDay.getMinTa()))
                .maxTemp(roundInt(asosDay.getMaxTa()))
                .precipitationProbability(rainPercent)
                .maxWindSpeed(asosDay.getMaxInsWs())
                .hasRain(defaultDouble(asosDay.getSumRn()) > 0)
                .hasSnow(defaultDouble(asosDay.getDdMes()) > 0 || defaultDouble(asosDay.getSumDpthFhsc()) > 0)
                .build();

        WeatherInfoDto.WeatherAnalysis analysis = WeatherInfoDto.WeatherAnalysis.builder()
                .reportDate(targetDate.toString())
                .sourceType("ASOS_DAILY")
                .outOfRange(false)
                .minTemperature(roundInt(asosDay.getMinTa()))
                .maxTemperature(roundInt(asosDay.getMaxTa()))
                .avgAmTemperature(null)
                .avgPmTemperature(null)
                .precipitationProbability(rainPercent)
                .maxWindSpeed(defaultDouble(asosDay.getMaxInsWs()))
                .fineDustValue(null)
                .fineDustRisk(false)
                .hasRain(defaultDouble(asosDay.getSumRn()) > 0)
                .hasSnow(defaultDouble(asosDay.getDdMes()) > 0 || defaultDouble(asosDay.getSumDpthFhsc()) > 0)
                .heatRisk(roundInt(asosDay.getMaxTa()) != null && roundInt(asosDay.getMaxTa()) >= 33)
                .coldRisk(roundInt(asosDay.getMinTa()) != null && roundInt(asosDay.getMinTa()) <= -5)
                .windRisk(defaultDouble(asosDay.getMaxInsWs()) >= 8)
                .build();

        return WeatherInfoDto.DashboardRes.builder()
                .reportDate(targetDate.toString())
                .locationLabel(locationLabel)
                .today(todayCard)
                .week(weekCard)
                .rain(rainCard)
                .airQuality(airQualityCard)
                .analysis(analysis)
                .equipmentRisks(buildEquipmentRisks(selectedDay, new ArrayList<>(), null))
                .planRisks(buildPlanRisks(selectedDay, new ArrayList<>(), null))
                .alerts(new ArrayList<>())
                .forecastDays(List.of(
                        WeatherInfoDto.ForecastDay.builder()
                                .date(targetDate.toString())
                                .dayLabel(formatDayLabel(targetDate))
                                .weatherLabel(asosDay.getSummary())
                                .minTemp(roundInt(asosDay.getMinTa()))
                                .maxTemp(roundInt(asosDay.getMaxTa()))
                                .precipitationProbability(rainPercent)
                                .windSpeed(asosDay.getMaxInsWs())
                                .build()
                ))
                .build();
    }

    private WeatherInfoDto.DashboardRes buildForecastFallbackDashboard(
            LocalDate targetDate,
            List<WeatherInfoDto.ForecastDay> forecastDays,
            List<WeatherInfoDto.AlertItem> alerts,
            WeatherInfoDto.AirQualityCard airQualityCard
    ) {
        Optional<WeatherInfoDto.ForecastDay> nearestForecast = findNearestAvailableForecast(targetDate, forecastDays);

        if (nearestForecast.isPresent()) {
            return buildDerivedDashboard(targetDate, forecastDays, nearestForecast.get(), alerts, airQualityCard);
        }

        WeatherInfoDto.TodayCard todayCard = WeatherInfoDto.TodayCard.builder()
                .headlineTemp("--°C / --°C")
                .summary("선택 날짜 예보 정보가 없습니다")
                .amLabel("기상정보 없음")
                .pmLabel("기상정보 없음")
                .observedAt(LocalTime.now().format(HHMM))
                .build();

        WeatherInfoDto.WeekCard weekCard = WeatherInfoDto.WeekCard.builder()
                .summary("예보 범위를 벗어났거나 응답값이 없습니다")
                .subSummary("기상청 제공 범위 밖 날짜는 실제 예보 대신 대기 상태로 표시합니다")
                .build();

        WeatherInfoDto.RainCard rainCard = WeatherInfoDto.RainCard.builder()
                .label("강수확률")
                .value("0%")
                .build();

        return WeatherInfoDto.DashboardRes.builder()
                .reportDate(targetDate.toString())
                .locationLabel(locationLabel)
                .today(todayCard)
                .week(weekCard)
                .rain(rainCard)
                .airQuality(airQualityCard != null ? airQualityCard : WeatherInfoDto.AirQualityCard.empty())
                .analysis(WeatherInfoDto.WeatherAnalysis.empty(targetDate.toString()))
                .equipmentRisks(new ArrayList<>())
                .planRisks(new ArrayList<>())
                .alerts(alerts)
                .forecastDays(forecastDays.isEmpty() ? buildFallbackForecastDays(targetDate) : forecastDays)
                .build();
    }

    private Optional<WeatherInfoDto.ForecastDay> findNearestAvailableForecast(
            LocalDate targetDate,
            List<WeatherInfoDto.ForecastDay> forecastDays
    ) {
        if (forecastDays == null || forecastDays.isEmpty()) {
            return Optional.empty();
        }

        return forecastDays.stream()
                .filter(this::isAvailableForecastDay)
                .min(Comparator.comparingLong(day -> Math.abs(
                        java.time.temporal.ChronoUnit.DAYS.between(
                                targetDate,
                                LocalDate.parse(day.getDate())
                        )
                )));
    }

    private boolean isAvailableForecastDay(WeatherInfoDto.ForecastDay day) {
        if (day == null || day.getDate() == null || day.getDate().isBlank()) {
            return false;
        }

        String label = defaultString(day.getWeatherLabel(), "");
        return !label.contains(FORECAST_OUT_OF_RANGE_LABEL)
                && !label.contains("기상정보 없음")
                && (day.getMaxTemp() != null || day.getMinTemp() != null);
    }

    private WeatherInfoDto.DashboardRes buildDerivedDashboard(
            LocalDate targetDate,
            List<WeatherInfoDto.ForecastDay> forecastDays,
            WeatherInfoDto.ForecastDay nearest,
            List<WeatherInfoDto.AlertItem> alerts,
            WeatherInfoDto.AirQualityCard airQualityCard
    ) {
        int rainPercent = defaultInt(nearest.getPrecipitationProbability());
        Double windSpeed = nearest.getWindSpeed();
        Integer fineDustValue = airQualityCard != null ? airQualityCard.getValue() : null;

        DayWeather selectedDay = DayWeather.builder()
                .summary(defaultString(nearest.getWeatherLabel(), "기상정보 없음"))
                .amLabel(defaultString(nearest.getWeatherLabel(), "기상정보 없음"))
                .pmLabel(defaultString(nearest.getWeatherLabel(), "기상정보 없음"))
                .minTemp(nearest.getMinTemp())
                .maxTemp(nearest.getMaxTemp())
                .precipitationProbability(rainPercent)
                .maxWindSpeed(windSpeed)
                .hasRain(defaultString(nearest.getWeatherLabel(), "").contains("비") || rainPercent >= 60)
                .hasSnow(defaultString(nearest.getWeatherLabel(), "").contains("눈"))
                .build();

        WeatherInfoDto.TodayCard todayCard = WeatherInfoDto.TodayCard.builder()
                .headlineTemp(buildHeadlineTemp(nearest.getMaxTemp(), nearest.getMinTemp()))
                .summary(defaultString(nearest.getWeatherLabel(), "기상정보 없음") + " · 인접 예보 기준")
                .amLabel(defaultString(nearest.getWeatherLabel(), "기상정보 없음"))
                .pmLabel(defaultString(nearest.getWeatherLabel(), "기상정보 없음"))
                .observedAt("인접 예보 기준")
                .build();

        WeatherInfoDto.WeekCard weekCard = WeatherInfoDto.WeekCard.builder()
                .summary(buildWindSummary(selectedDay, alerts))
                .subSummary("선택 날짜 공식 예보가 없어서 가장 가까운 예보값을 기준으로 표시합니다")
                .build();

        WeatherInfoDto.RainCard rainCard = WeatherInfoDto.RainCard.builder()
                .label("강수확률")
                .value(rainPercent + "%")
                .build();

        WeatherInfoDto.WeatherAnalysis analysis = WeatherInfoDto.WeatherAnalysis.builder()
                .reportDate(targetDate.toString())
                .sourceType("DERIVED")
                .outOfRange(true)
                .minTemperature(nearest.getMinTemp())
                .maxTemperature(nearest.getMaxTemp())
                .avgAmTemperature(null)
                .avgPmTemperature(null)
                .precipitationProbability(rainPercent)
                .maxWindSpeed(windSpeed)
                .fineDustValue(fineDustValue)
                .fineDustRisk(fineDustValue != null && fineDustValue >= 80)
                .hasRain(selectedDay.isHasRain())
                .hasSnow(selectedDay.isHasSnow())
                .heatRisk(nearest.getMaxTemp() != null && nearest.getMaxTemp() >= 33)
                .coldRisk(nearest.getMinTemp() != null && nearest.getMinTemp() <= -5)
                .windRisk(windSpeed != null && windSpeed >= 8)
                .build();

        return WeatherInfoDto.DashboardRes.builder()
                .reportDate(targetDate.toString())
                .locationLabel(locationLabel)
                .today(todayCard)
                .week(weekCard)
                .rain(rainCard)
                .airQuality(airQualityCard != null ? airQualityCard : WeatherInfoDto.AirQualityCard.empty())
                .analysis(analysis)
                .equipmentRisks(new ArrayList<>())
                .planRisks(new ArrayList<>())
                .alerts(alerts)
                .forecastDays(forecastDays.isEmpty() ? buildFallbackForecastDays(targetDate) : forecastDays)
                .build();
    }

    private WeatherInfoDto.DashboardRes buildFallbackDashboard(LocalDate targetDate) {
        WeatherInfoDto.TodayCard todayCard = WeatherInfoDto.TodayCard.builder()
                .headlineTemp("--°C / --°C")
                .summary("기상 정보를 불러오지 못했습니다")
                .amLabel("기상정보 없음")
                .pmLabel("기상정보 없음")
                .observedAt(LocalTime.now().format(HHMM))
                .build();

        WeatherInfoDto.WeekCard weekCard = WeatherInfoDto.WeekCard.builder()
                .summary("3일 내 특이 기상 없음")
                .subSummary("기상청 응답 전 임시 표시")
                .build();

        WeatherInfoDto.RainCard rainCard = WeatherInfoDto.RainCard.builder()
                .label("강수확률")
                .value("0%")
                .build();

        return WeatherInfoDto.DashboardRes.builder()
                .reportDate(targetDate.toString())
                .locationLabel(locationLabel)
                .today(todayCard)
                .week(weekCard)
                .rain(rainCard)
                .airQuality(WeatherInfoDto.AirQualityCard.empty())
                .analysis(WeatherInfoDto.WeatherAnalysis.empty(targetDate.toString()))
                .equipmentRisks(new ArrayList<>())
                .planRisks(new ArrayList<>())
                .alerts(new ArrayList<>())
                .forecastDays(buildFallbackForecastDays(targetDate))
                .build();
    }

    // 기상청 단기예보 OpenAPI 호출
    // 2024.11 이후 단기예보가 최대 5일권으로 확장되어 1,000건을 초과할 수 있으므로
    // totalCount 기준으로 페이지를 끝까지 조회해 주간/월간 탭 누락을 막는다.
    private Map<LocalDate, DayWeather> fetchVillageForecastMap() throws Exception {
        BaseDateTime baseDateTime = resolveVillageBaseDateTime();
        Map<LocalDate, TreeMap<String, VillageSlot>> grouped = new TreeMap<>();

        int pageNo = 1;
        int fetchedCount = 0;
        int totalCount = -1;

        while (pageNo <= MAX_VILLAGE_PAGE_REQUESTS) {
            String url = UriComponentsBuilder.fromHttpUrl(villageUrl)
                    .queryParam("serviceKey", serviceKey)
                    .queryParam("pageNo", pageNo)
                    .queryParam("numOfRows", VILLAGE_PAGE_SIZE)
                    .queryParam("dataType", "JSON")
                    .queryParam("base_date", baseDateTime.getBaseDate())
                    .queryParam("base_time", baseDateTime.getBaseTime())
                    .queryParam("nx", nx)
                    .queryParam("ny", ny)
                    .build(false)
                    .toUriString();

            JsonNode root = objectMapper.readTree(restTemplate.getForObject(url, String.class));

            if (!isNormalService(root)) {
                log.warn("[기상 스냅샷] 단기예보 API 응답 비정상 - baseDate={}, baseTime={}, pageNo={}",
                        baseDateTime.getBaseDate(), baseDateTime.getBaseTime(), pageNo);
                return new LinkedHashMap<>();
            }

            JsonNode bodyNode = root.path("response").path("body");
            if (totalCount < 0) {
                totalCount = bodyNode.path("totalCount").asInt(0);
            }

            List<JsonNode> items = toNodeList(bodyNode.path("items").path("item"));
            if (items.isEmpty()) {
                break;
            }

            collectVillageForecastItems(items, grouped);
            fetchedCount += items.size();

            if ((totalCount > 0 && fetchedCount >= totalCount) || items.size() < VILLAGE_PAGE_SIZE) {
                break;
            }

            pageNo++;
        }

        if (totalCount > 0 && fetchedCount < totalCount) {
            log.warn("[기상 스냅샷] 단기예보 일부만 수집됨 - totalCount={}, fetchedCount={}, maxPages={}",
                    totalCount, fetchedCount, MAX_VILLAGE_PAGE_REQUESTS);
        }

        Map<LocalDate, DayWeather> result = new LinkedHashMap<>();
        for (Map.Entry<LocalDate, TreeMap<String, VillageSlot>> entry : grouped.entrySet()) {
            DayWeather dayWeather = buildVillageDayWeather(entry.getKey(), new ArrayList<>(entry.getValue().values()));
            result.put(entry.getKey(), dayWeather);
        }

        log.info("[기상 스냅샷] 단기예보 수집 완료 - baseDate={}, baseTime={}, totalCount={}, fetchedCount={}, dateCount={}",
                baseDateTime.getBaseDate(),
                baseDateTime.getBaseTime(),
                totalCount,
                fetchedCount,
                result.size());

        return result;
    }

    private void collectVillageForecastItems(
            List<JsonNode> items,
            Map<LocalDate, TreeMap<String, VillageSlot>> grouped
    ) {
        for (JsonNode item : items) {
            String fcstDateText = item.path("fcstDate").asText("");
            if (fcstDateText.isBlank()) {
                continue;
            }

            LocalDate fcstDate = LocalDate.parse(fcstDateText, BASIC_DATE);
            String fcstTime = item.path("fcstTime").asText();
            String category = item.path("category").asText();
            String value = item.path("fcstValue").asText();

            VillageSlot slot = grouped
                    .computeIfAbsent(fcstDate, key -> new TreeMap<>())
                    .computeIfAbsent(fcstTime, VillageSlot::new);

            switch (category) {
                case "TMP" -> slot.setTemperature(parseDouble(value));
                case "POP" -> slot.setPrecipitationProbability(parseInteger(value));
                case "SKY" -> slot.setSky(parseInteger(value));
                case "PTY" -> slot.setPty(parseInteger(value));
                case "WSD" -> slot.setWindSpeed(parseDouble(value));
                case "UUU" -> slot.setEastWestWindComponent(parseDouble(value));
                case "VVV" -> slot.setNorthSouthWindComponent(parseDouble(value));
                case "PCP" -> slot.setPrecipitationVolume(parsePrecipitation(value));
                case "TMN" -> slot.setDailyMin(parseDouble(value));
                case "TMX" -> slot.setDailyMax(parseDouble(value));
                default -> {
                }
            }
        }
    }

    private Map<LocalDate, DayWeather> fetchMidForecastMap() {
        try {
            String tmFc = resolveMidTmFc();
            LocalDate issueDate = LocalDate.parse(tmFc.substring(0, 8), BASIC_DATE);
            boolean morningIssue = tmFc.endsWith("0600");
            int startOffset = morningIssue ? 4 : 5;

            String landUrl = UriComponentsBuilder.fromHttpUrl(midLandUrl)
                    .queryParam("serviceKey", serviceKey)
                    .queryParam("pageNo", 1)
                    .queryParam("numOfRows", 10)
                    .queryParam("dataType", "JSON")
                    .queryParam("regId", midLandRegId)
                    .queryParam("tmFc", tmFc)
                    .build(false)
                    .toUriString();

            String tempUrl = UriComponentsBuilder.fromHttpUrl(midTempUrl)
                    .queryParam("serviceKey", serviceKey)
                    .queryParam("pageNo", 1)
                    .queryParam("numOfRows", 10)
                    .queryParam("dataType", "JSON")
                    .queryParam("regId", midTempRegId)
                    .queryParam("tmFc", tmFc)
                    .build(false)
                    .toUriString();

            JsonNode landRoot = objectMapper.readTree(restTemplate.getForObject(landUrl, String.class));
            JsonNode tempRoot = objectMapper.readTree(restTemplate.getForObject(tempUrl, String.class));

            if (!isNormalService(landRoot) || !isNormalService(tempRoot)) {
                log.warn("[기상 스냅샷] 중기예보 API 응답 비정상 - tmFc={}", tmFc);
                return new LinkedHashMap<>();
            }

            JsonNode landItem = firstItem(landRoot.path("response").path("body").path("items").path("item"));
            JsonNode tempItem = firstItem(tempRoot.path("response").path("body").path("items").path("item"));

            if (landItem == null || tempItem == null) {
                log.warn("[기상 스냅샷] 중기예보 item이 비어 있습니다. - tmFc={}", tmFc);
                return new LinkedHashMap<>();
            }

            Map<LocalDate, DayWeather> result = new LinkedHashMap<>();

            for (int offset = startOffset; offset <= 10; offset++) {
                LocalDate targetDate = issueDate.plusDays(offset);

                String weatherLabel = offset <= 7
                        ? pickMidWeatherLabel(
                        landItem.path("wf" + offset + "Am").asText(""),
                        landItem.path("wf" + offset + "Pm").asText("")
                )
                        : normalizeMidLabel(landItem.path("wf" + offset).asText(""));

                Integer pop = offset <= 7
                        ? maxNullable(
                        parseInteger(landItem.path("rnSt" + offset + "Am").asText("")),
                        parseInteger(landItem.path("rnSt" + offset + "Pm").asText(""))
                )
                        : parseInteger(landItem.path("rnSt" + offset).asText(""));

                Integer taMin = parseInteger(tempItem.path("taMin" + offset).asText(""));
                Integer taMax = parseInteger(tempItem.path("taMax" + offset).asText(""));

                DayWeather dayWeather = DayWeather.builder()
                        .summary(defaultString(weatherLabel, "기상정보 없음"))
                        .amLabel(offset <= 7
                                ? defaultString(normalizeMidLabel(landItem.path("wf" + offset + "Am").asText("")), defaultString(weatherLabel, "기상정보 없음"))
                                : defaultString(weatherLabel, "기상정보 없음"))
                        .pmLabel(offset <= 7
                                ? defaultString(normalizeMidLabel(landItem.path("wf" + offset + "Pm").asText("")), defaultString(weatherLabel, "기상정보 없음"))
                                : defaultString(weatherLabel, "기상정보 없음"))
                        .minTemp(taMin)
                        .maxTemp(taMax)
                        .precipitationProbability(pop != null ? pop : 0)
                        .maxWindSpeed(null)
                        .hasRain(defaultString(weatherLabel, "").contains("비"))
                        .hasSnow(defaultString(weatherLabel, "").contains("눈"))
                        .build();

                result.put(targetDate, dayWeather);
            }

            log.info("[기상 스냅샷] 중기예보 수집 완료 - tmFc={}, dateCount={}, dates={}", tmFc, result.size(), result.keySet());
            return result;
        } catch (Exception e) {
            log.warn("[기상 스냅샷] 중기예보 수집 실패 - reason={}", summarizeException(e));
            return new LinkedHashMap<>();
        }
    }

    private AsosDay fetchAsosDay(LocalDate targetDate) {
        try {
            String dateText = targetDate.format(BASIC_DATE);

            String url = UriComponentsBuilder.fromHttpUrl(asosUrl)
                    .queryParam("serviceKey", serviceKey)
                    .queryParam("pageNo", 1)
                    .queryParam("numOfRows", 10)
                    .queryParam("dataType", "JSON")
                    .queryParam("dataCd", "ASOS")
                    .queryParam("dateCd", "DAY")
                    .queryParam("startDt", dateText)
                    .queryParam("endDt", dateText)
                    .queryParam("stnIds", stnId)
                    .build(false)
                    .toUriString();

            JsonNode root = objectMapper.readTree(restTemplate.getForObject(url, String.class));
            if (!isNormalService(root)) {
                return null;
            }

            JsonNode item = firstItem(root.path("response").path("body").path("items").path("item"));
            if (item == null) {
                return null;
            }

            Double minTa = parseDouble(item.path("minTa").asText(""));
            Double maxTa = parseDouble(item.path("maxTa").asText(""));
            Double avgTa = parseDouble(item.path("avgTa").asText(""));
            Double sumRn = parseDouble(item.path("sumRn").asText(""));
            Double maxInsWs = parseDouble(item.path("maxInsWs").asText(""));
            Double avgTca = parseDouble(item.path("avgTca").asText(""));
            Double ddMes = parseDouble(item.path("ddMes").asText(""));
            Double sumDpthFhsc = parseDouble(item.path("sumDpthFhsc").asText(""));
            String iscs = item.path("iscs").asText("");

            String weatherLabel = resolveAsosWeatherLabel(sumRn, ddMes, sumDpthFhsc, avgTca, iscs);
            String summary = weatherLabel;
            if (defaultDouble(maxInsWs) >= 8) {
                summary = summary + ", 바람 강함";
            }

            return AsosDay.builder()
                    .summary(summary)
                    .amLabel(weatherLabel + " · 12시 기준")
                    .pmLabel(weatherLabel + " · 18시 기준")
                    .avgTa(avgTa)
                    .minTa(minTa)
                    .maxTa(maxTa)
                    .sumRn(sumRn)
                    .maxInsWs(maxInsWs)
                    .ddMes(ddMes)
                    .sumDpthFhsc(sumDpthFhsc)
                    .iscs(iscs)
                    .build();
        } catch (Exception e) {
            return null;
        }
    }

    private List<WeatherInfoDto.AlertItem> fetchAlerts() {
        try {
            LocalDate today = LocalDate.now();

            String url = UriComponentsBuilder.fromHttpUrl(warningUrl)
                    .queryParam("serviceKey", serviceKey)
                    .queryParam("pageNo", 1)
                    .queryParam("numOfRows", 10)
                    .queryParam("dataType", "JSON")
                    .queryParam("stnId", stnId)
                    .queryParam("fromTmFc", today.minusDays(1).format(BASIC_DATE))
                    .queryParam("toTmFc", today.format(BASIC_DATE))
                    .build(false)
                    .toUriString();

            JsonNode root = objectMapper.readTree(restTemplate.getForObject(url, String.class));
            if (!isNormalService(root)) {
                return new ArrayList<>();
            }

            JsonNode itemsNode = root.path("response").path("body").path("items").path("item");
            List<WeatherInfoDto.AlertItem> result = new ArrayList<>();

            for (JsonNode item : toNodeList(itemsNode)) {
                String title = firstNonBlank(
                        item.path("t1").asText(""),
                        item.path("title").asText(""),
                        item.path("t2").asText("")
                );

                String message = firstNonBlank(
                        item.path("t6").asText(""),
                        item.path("t4").asText(""),
                        item.path("other").asText(""),
                        "기상특보 정보 확인"
                );

                if (title.isBlank()) {
                    continue;
                }
                if (isInactiveAlert(title, message)) {
                    continue;
                }

                String level = inferAlertLevel(title + " " + message);

                result.add(WeatherInfoDto.AlertItem.builder()
                        .title(title)
                        .level(level)
                        .message(trimMessage(message))
                        .build());
            }

            return result.size() > 3 ? result.subList(0, 3) : result;
        } catch (Exception e) {
            return new ArrayList<>();
        }
    }

    // 일자/주간 예보
    private List<WeatherInfoDto.ForecastDay> buildForecastDays(LocalDate startDate, Map<LocalDate, DayWeather> source) {
        List<WeatherInfoDto.ForecastDay> result = new ArrayList<>();

        for (Map.Entry<LocalDate, DayWeather> entry : source.entrySet()) {
            if (entry.getKey().isBefore(startDate)) {
                continue;
            }

            DayWeather day = entry.getValue();
            result.add(WeatherInfoDto.ForecastDay.builder()
                    .date(entry.getKey().toString())
                    .dayLabel(formatDayLabel(entry.getKey()))
                    .weatherLabel(defaultString(day.getSummary(), "기상정보 없음"))
                    .minTemp(day.getMinTemp())
                    .maxTemp(day.getMaxTemp())
                    .precipitationProbability(defaultInt(day.getPrecipitationProbability()))
                    .windSpeed(day.getMaxWindSpeed())
                    .build());

            if (result.size() >= 10) {
                break;
            }
        }

        return result.isEmpty() ? buildFallbackForecastDays(startDate) : result;
    }

    private List<WeatherInfoDto.ForecastDay> buildExtendedForecastDays(
            LocalDate baseDate,
            Map<LocalDate, DayWeather> forecastMap
    ) {
        LocalDate minimumEndDate = baseDate.plusDays(MIN_WEEKLY_FORECAST_DAYS - 1L);
        LocalDate latestForecastDate = forecastMap.keySet().stream()
                .filter(date -> !date.isBefore(baseDate))
                .max(LocalDate::compareTo)
                .orElse(minimumEndDate);
        LocalDate cappedLatestDate = latestForecastDate.isAfter(baseDate.plusDays(MAX_FORECAST_DISPLAY_DAYS - 1L))
                ? baseDate.plusDays(MAX_FORECAST_DISPLAY_DAYS - 1L)
                : latestForecastDate;
        LocalDate endDate = cappedLatestDate.isAfter(minimumEndDate) ? cappedLatestDate : minimumEndDate;

        List<WeatherInfoDto.ForecastDay> result = new ArrayList<>();
        LocalDate cursor = baseDate;

        while (!cursor.isAfter(endDate)) {
            DayWeather day = forecastMap.get(cursor);

            if (day != null) {
                result.add(WeatherInfoDto.ForecastDay.builder()
                        .date(cursor.toString())
                        .dayLabel(formatDayLabel(cursor))
                        .weatherLabel(defaultString(day.getSummary(), "기상정보 없음"))
                        .minTemp(day.getMinTemp())
                        .maxTemp(day.getMaxTemp())
                        .precipitationProbability(defaultInt(day.getPrecipitationProbability()))
                        .windSpeed(day.getMaxWindSpeed())
                        .build());
            } else {
                result.add(WeatherInfoDto.ForecastDay.builder()
                        .date(cursor.toString())
                        .dayLabel(formatDayLabel(cursor))
                        .weatherLabel(FORECAST_OUT_OF_RANGE_LABEL)
                        .minTemp(null)
                        .maxTemp(null)
                        .precipitationProbability(0)
                        .windSpeed(null)
                        .build());
            }

            cursor = cursor.plusDays(1);
        }

        return result;
    }

    private List<WeatherInfoDto.ForecastDay> buildFallbackForecastDays(LocalDate startDate) {
        List<WeatherInfoDto.ForecastDay> list = new ArrayList<>();

        for (int i = 0; i < 3; i++) {
            LocalDate date = startDate.plusDays(i);
            list.add(WeatherInfoDto.ForecastDay.builder()
                    .date(date.toString())
                    .dayLabel(formatDayLabel(date))
                    .weatherLabel("기상정보 없음")
                    .minTemp(null)
                    .maxTemp(null)
                    .precipitationProbability(0)
                    .windSpeed(null)
                    .build());
        }

        return list;
    }

    private DayWeather buildVillageDayWeather(LocalDate targetDate, List<VillageSlot> slots) {
        if (slots == null || slots.isEmpty()) {
            return DayWeather.builder()
                    .summary(targetDate.equals(LocalDate.now()) ? "기상정보 없음" : "선택일 기상정보 없음")
                    .amLabel("기상정보 없음")
                    .pmLabel("기상정보 없음")
                    .minTemp(null)
                    .maxTemp(null)
                    .precipitationProbability(0)
                    .maxWindSpeed(null)
                    .hasRain(false)
                    .hasSnow(false)
                    .build();
        }

        Integer maxTemp = slots.stream()
                .map(VillageSlot::getTemperature)
                .filter(Objects::nonNull)
                .map(v -> (int) Math.round(v))
                .max(Integer::compareTo)
                .orElseGet(() -> slots.stream()
                        .map(VillageSlot::getDailyMax)
                        .filter(Objects::nonNull)
                        .map(v -> (int) Math.round(v))
                        .max(Integer::compareTo)
                        .orElse(null));

        Integer minTemp = slots.stream()
                .map(VillageSlot::getTemperature)
                .filter(Objects::nonNull)
                .map(v -> (int) Math.round(v))
                .min(Integer::compareTo)
                .orElseGet(() -> slots.stream()
                        .map(VillageSlot::getDailyMin)
                        .filter(Objects::nonNull)
                        .map(v -> (int) Math.round(v))
                        .min(Integer::compareTo)
                        .orElse(null));

        Integer maxPop = slots.stream()
                .map(VillageSlot::getPrecipitationProbability)
                .filter(Objects::nonNull)
                .max(Integer::compareTo)
                .orElse(0);

        Double maxWind = slots.stream()
                .map(this::resolveVillageSlotWindSpeed)
                .filter(Objects::nonNull)
                .max(Double::compareTo)
                .orElse(null);

        boolean hasRain = slots.stream()
                .map(VillageSlot::getPty)
                .filter(Objects::nonNull)
                .anyMatch(v -> v == 1 || v == 2 || v == 4);

        boolean hasSnow = slots.stream()
                .map(VillageSlot::getPty)
                .filter(Objects::nonNull)
                .anyMatch(v -> v == 2 || v == 3);

        VillageSlot am = findNearestSlot(slots, "1200");
        VillageSlot pm = findNearestSlot(slots, "1800");

        String amLabel = toWeatherLabel(am) + formatTemp(am.getTemperature());
        String pmLabel = toWeatherLabel(pm) + formatTemp(pm.getTemperature());
        String amSummary = toWeatherLabel(am);
        String pmSummary = toWeatherLabel(pm);

        String summary = amSummary.equals(pmSummary)
                ? amSummary
                : amSummary + ", 오후 " + normalizeAfternoonText(pmSummary);

        if (defaultDouble(maxWind) >= 8 && !summary.contains("강풍")) {
            summary = summary + ", 강풍 주의";
        }

        return DayWeather.builder()
                .summary(summary)
                .amLabel(amLabel)
                .pmLabel(pmLabel)
                .minTemp(minTemp)
                .maxTemp(maxTemp)
                .precipitationProbability(maxPop)
                .maxWindSpeed(maxWind)
                .hasRain(hasRain)
                .hasSnow(hasSnow)
                .build();
    }

    // 풍속 기반 기상 영향도 요약
    private Double resolveVillageSlotWindSpeed(VillageSlot slot) {
        if (slot == null) {
            return null;
        }

        if (slot.getWindSpeed() != null) {
            return slot.getWindSpeed();
        }

        if (slot.getEastWestWindComponent() == null || slot.getNorthSouthWindComponent() == null) {
            return null;
        }

        double squared = Math.pow(slot.getEastWestWindComponent(), 2)
                + Math.pow(slot.getNorthSouthWindComponent(), 2);
        return Math.round(Math.sqrt(squared) * 10.0) / 10.0;
    }

    private String buildWindSummary(DayWeather selectedDay, List<WeatherInfoDto.AlertItem> alerts) {
        if (alerts != null && !alerts.isEmpty()) {
            return alerts.get(0).getTitle();
        }

        if (selectedDay == null || selectedDay.getMaxWindSpeed() == null) {
            return "풍속 예보 미제공 — 제공 가능한 기상정보만 표시합니다";
        }

        double wind = selectedDay.getMaxWindSpeed();
        String windText = formatDouble(wind);

        if (wind >= 14) {
            return "강풍 경보 수준 (" + windText + "m/s) — 옥외 작업 중지";
        }
        if (wind >= 10) {
            return "강풍 주의보 수준 (" + windText + "m/s) — 양중·고소 제한";
        }
        if (wind >= 8) {
            return "바람 강함 (" + windText + "m/s) — 외부 작업 점검";
        }
        if (wind >= 4) {
            return "바람 보통 (" + windText + "m/s) — 평시 작업 가능";
        }
        return "바람 약함 (" + windText + "m/s) — 모든 공정 정상";
    }

    // 풍속 기반 작업 가이드
    private String buildWindGuide(DayWeather selectedDay) {
        if (selectedDay == null || selectedDay.getMaxWindSpeed() == null) {
            return "풍속 세부값이 제공되지 않는 예보 구간입니다.";
        }

        double wind = selectedDay.getMaxWindSpeed();

        if (wind >= 14) {
            return "타워크레인·고소작업대 즉시 운전 중지, 자재 결속 재점검";
        }
        if (wind >= 10) {
            return "양중 작업 일시 중지, 외장재·패널 설치 보류";
        }
        if (wind >= 8) {
            return "양중 신호수 추가 배치, 자재 결속 상태 수시 확인";
        }
        if (wind >= 4) {
            return "현재 풍속 기준 모든 옥외 작업 정상 진행 가능";
        }
        return "풍속 영향 없음, 표준 작업 절차 유지";
    }

    // 위험 통제 추천
    private List<WeatherInfoDto.RiskItem> buildEquipmentRisks(
            DayWeather selectedDay,
            List<WeatherInfoDto.AlertItem> alerts,
            Integer fineDustValue
    ) {
        List<WeatherInfoDto.RiskItem> result = new ArrayList<>();

        if (!alerts.isEmpty()) {
            result.add(WeatherInfoDto.RiskItem.builder()
                    .badge("AI")
                    .title("위험 장비 통제")
                    .subtitle(alerts.get(0).getTitle())
                    .level(alerts.get(0).getLevel())
                    .reason("실시간 활성 특보가 있는 경우 외부 장비 운용 안정성이 빠르게 낮아질 수 있습니다.")
                    .action("특보 해제 전까지 양중·고소 장비 투입 여부 재검토")
                    .build());
        }

        if (defaultDouble(selectedDay.getMaxWindSpeed()) >= 10) {
            result.add(WeatherInfoDto.RiskItem.builder()
                    .badge("AI")
                    .title("타워크레인 / 이동식 크레인")
                    .subtitle("순간 풍속 " + formatDouble(defaultDouble(selectedDay.getMaxWindSpeed())) + "m/s 주의")
                    .level("경고")
                    .reason("강풍 시 자재 흔들림과 하중 제어 위험이 커져 양중 작업 안정성이 크게 떨어집니다.")
                    .action("풍속 안정 전까지 양중 작업 제한 또는 중지")
                    .build());
        }

        if (selectedDay.isHasRain() || defaultInt(selectedDay.getPrecipitationProbability()) >= 60) {
            result.add(WeatherInfoDto.RiskItem.builder()
                    .badge("AI")
                    .title("고소작업차 / 외부 이동 장비")
                    .subtitle("우천·젖은 노면 주의")
                    .level("주의")
                    .reason("노면 미끄럼과 시야 저하로 장비 접근성과 작업 발판 안정성이 떨어질 수 있습니다.")
                    .action("노면 점검 후 제한 운용, 실외 작업 시간 재조정")
                    .build());
        }

        if (selectedDay.isHasSnow()) {
            result.add(WeatherInfoDto.RiskItem.builder()
                    .badge("AI")
                    .title("지게차 / 자재 운반 장비")
                    .subtitle("적설·결빙 구간 주의")
                    .level("경고")
                    .reason("결빙 노면에서는 제동거리 증가와 하역 중 미끄럼 위험이 동시에 커집니다.")
                    .action("제설·제빙 후 운행, 경사 구간 장비 투입 제한")
                    .build());
        }

        if (selectedDay.getMaxTemp() != null && selectedDay.getMaxTemp() >= 33) {
            result.add(WeatherInfoDto.RiskItem.builder()
                    .badge("AI")
                    .title("옥외 장비 장시간 운용")
                    .subtitle("고온 노출 주의")
                    .level("주의")
                    .reason("고온 시간대 장시간 노출 시 작업 집중도와 체력 저하가 동시에 발생할 수 있습니다.")
                    .action("폭염 시간대 휴식 주기 확대, 냉방/수분 보급 강화")
                    .build());
        }

        if (fineDustValue != null && fineDustValue >= 80) {
            result.add(WeatherInfoDto.RiskItem.builder()
                    .badge("AI")
                    .title("옥외 분진 발생 장비")
                    .subtitle("미세먼지 PM10 " + fineDustValue + "㎍/㎥")
                    .level(fineDustValue >= 150 ? "경고" : "주의")
                    .reason("절단·연마·굴착 장비는 분진 농도를 더 끌어올려 호흡기 위험을 키울 수 있습니다.")
                    .action("작업 시간 분산, 살수 빈도 강화, KF94 이상 보호구 배포")
                    .build());
        }

        return result;
    }

    private List<WeatherInfoDto.RiskItem> buildPlanRisks(
            DayWeather selectedDay,
            List<WeatherInfoDto.AlertItem> alerts,
            Integer fineDustValue
    ) {
        List<WeatherInfoDto.RiskItem> result = new ArrayList<>();

        if (!alerts.isEmpty()) {
            result.add(WeatherInfoDto.RiskItem.builder()
                    .badge("AI")
                    .title("계획 대비 위험 경고")
                    .subtitle(alerts.get(0).getTitle())
                    .level(alerts.get(0).getLevel())
                    .reason("기상특보가 있을 때는 계획 공정 진행 여부를 우선 재검토해야 합니다.")
                    .action("해당 시간대 외부 공정과 장비 투입 계획 즉시 재확인")
                    .build());
        }

        if (selectedDay.isHasRain() || defaultInt(selectedDay.getPrecipitationProbability()) >= 60) {
            result.add(WeatherInfoDto.RiskItem.builder()
                    .badge("AI")
                    .title("외부 콘크리트 타설 / 도장 / 방수")
                    .subtitle("우천 시 공정 품질 위험")
                    .level("주의")
                    .reason("우천 시 품질 저하와 양생·건조 이슈가 동시에 발생할 수 있습니다.")
                    .action("실내 공종 우선 전환 또는 타설 시간 재조정")
                    .build());
        }

        if (defaultDouble(selectedDay.getMaxWindSpeed()) >= 8) {
            result.add(WeatherInfoDto.RiskItem.builder()
                    .badge("AI")
                    .title("외부 고소 작업 / 철골 양중 / 비계 작업")
                    .subtitle("강풍 시 추락·낙하 위험")
                    .level(defaultDouble(selectedDay.getMaxWindSpeed()) >= 10 ? "경고" : "주의")
                    .reason("강풍 조건에서는 작업자 안전과 자재 낙하 위험이 동시에 증가합니다.")
                    .action("풍속 안정 전까지 외부 고소 작업 최소화")
                    .build());
        }

        if (selectedDay.isHasSnow()) {
            result.add(WeatherInfoDto.RiskItem.builder()
                    .badge("AI")
                    .title("외부 이동 작업 / 굴착 / 자재 운반")
                    .subtitle("적설·결빙 시 이동성 저하")
                    .level("경고")
                    .reason("적설 또는 결빙 조건에서는 이동 동선과 장비 진입 구간의 안전성이 급격히 낮아집니다.")
                    .action("제설 후 진행, 외부 동선 우선 점검")
                    .build());
        }

        if (selectedDay.getMinTemp() != null && selectedDay.getMinTemp() <= -5) {
            result.add(WeatherInfoDto.RiskItem.builder()
                    .badge("AI")
                    .title("저온 민감 공정 (타설 / 방수 / 배관)")
                    .subtitle("저온 시 시공 품질 주의")
                    .level("주의")
                    .reason("저온에서는 양생·동결·부착 품질 확보에 주의가 필요합니다.")
                    .action("보양 및 작업 시간 조정 검토")
                    .build());
        }

        if (fineDustValue != null && fineDustValue >= 80) {
            result.add(WeatherInfoDto.RiskItem.builder()
                    .badge("AI")
                    .title("옥외 도장 / 용접 / 절단 공정")
                    .subtitle("미세먼지 PM10 " + fineDustValue + "㎍/㎥")
                    .level(fineDustValue >= 150 ? "경고" : "주의")
                    .reason("미세먼지 농도가 높을 때는 도장 부착력과 작업자 호흡기 안전이 동시에 위협받습니다.")
                    .action("실내 작업 우선 편성, 외부 작업 시 보호구·살수 강화")
                    .build());
        }

        return result;
    }

    private boolean isEmptyDashboard(WeatherInfoDto.DashboardRes response) {
        if (response == null || response.getAnalysis() == null) {
            return true;
        }

        String sourceType = response.getAnalysis().getSourceType();
        return "EMPTY".equals(sourceType);
    }

    private WeatherInfoDto.DashboardRes loadSnapshotOrFallback(LocalDate targetDate) {
        return weatherInfoRepository.findByReportDate(targetDate)
                .map(this::fromSnapshot)
                .orElseGet(() -> buildFallbackDashboard(targetDate));
    }

    private WeatherInfoDto.DashboardRes fromSnapshot(WeatherInfo snapshot) {
        WeatherInfoDto.AirQualityCard storedAirQuality = buildStoredAirQualityCard(snapshot);

        try {
            if (snapshot.getDashboardJson() != null && !snapshot.getDashboardJson().isBlank()) {
                WeatherInfoDto.DashboardRes parsed = objectMapper.readValue(
                        snapshot.getDashboardJson(),
                        WeatherInfoDto.DashboardRes.class
                );

                WeatherInfoDto.AirQualityCard parsedAirQuality = isAvailableAirQuality(parsed.getAirQuality())
                        ? parsed.getAirQuality()
                        : storedAirQuality;

                WeatherInfoDto.WeatherAnalysis parsedAnalysis = parsed.getAnalysis() != null
                        ? parsed.getAnalysis()
                        : WeatherInfoDto.WeatherAnalysis.empty(snapshot.getReportDate().toString());

                if (isAvailableAirQuality(parsedAirQuality)) {
                    parsedAnalysis = withFineDust(parsedAnalysis, parsedAirQuality.getValue());
                }

                return WeatherInfoDto.DashboardRes.builder()
                        .reportDate(parsed.getReportDate() != null ? parsed.getReportDate() : snapshot.getReportDate().toString())
                        .locationLabel(parsed.getLocationLabel())
                        .today(parsed.getToday())
                        .week(parsed.getWeek())
                        .rain(parsed.getRain())
                        .airQuality(parsedAirQuality)
                        .analysis(parsedAnalysis)
                        .equipmentRisks(parsed.getEquipmentRisks() != null ? parsed.getEquipmentRisks() : new ArrayList<>())
                        .planRisks(parsed.getPlanRisks() != null ? parsed.getPlanRisks() : new ArrayList<>())
                        .alerts(parsed.getAlerts() != null ? parsed.getAlerts() : new ArrayList<>())
                        .forecastDays(parsed.getForecastDays() != null ? parsed.getForecastDays() : buildFallbackForecastDays(snapshot.getReportDate()))
                        .build();
            }
        } catch (Exception e) {
            log.warn("[기상 스냅샷] dashboard_json 복원 실패 - reportDate={}", snapshot.getReportDate(), e);
        }

        WeatherInfoDto.TodayCard todayCard = WeatherInfoDto.TodayCard.builder()
                .headlineTemp(defaultString(snapshot.getTodayHeadlineTemp(), "--°C / --°C"))
                .summary(defaultString(snapshot.getTodaySummary(), "기상 정보 없음"))
                .amLabel(defaultString(snapshot.getAmLabel(), "기상정보 없음"))
                .pmLabel(defaultString(snapshot.getPmLabel(), "기상정보 없음"))
                .observedAt(LocalTime.now().format(HHMM))
                .build();

        WeatherInfoDto.WeekCard weekCard = WeatherInfoDto.WeekCard.builder()
                .summary(defaultString(snapshot.getWeeklySummary(), "3일 내 특이 기상 없음"))
                .subSummary("저장된 스냅샷 기준")
                .build();

        WeatherInfoDto.RainCard rainCard = WeatherInfoDto.RainCard.builder()
                .label("강수확률")
                .value(defaultString(snapshot.getPrecipitationProbability(), "0%"))
                .build();

        WeatherInfoDto.WeatherAnalysis analysis = WeatherInfoDto.WeatherAnalysis.empty(snapshot.getReportDate().toString());
        if (isAvailableAirQuality(storedAirQuality)) {
            analysis = withFineDust(analysis, storedAirQuality.getValue());
        }

        return WeatherInfoDto.DashboardRes.builder()
                .reportDate(snapshot.getReportDate().toString())
                .locationLabel(defaultString(snapshot.getLocationLabel(), locationLabel))
                .today(todayCard)
                .week(weekCard)
                .rain(rainCard)
                .airQuality(storedAirQuality)
                .analysis(analysis)
                .equipmentRisks(new ArrayList<>())
                .planRisks(new ArrayList<>())
                .alerts(new ArrayList<>())
                .forecastDays(buildFallbackForecastDays(snapshot.getReportDate()))
                .build();
    }

    private WeatherInfoDto.AirQualityCard buildStoredAirQualityCard(WeatherInfo snapshot) {
        Integer fineDustValue = parseInteger(snapshot.getFineDustValue());
        String fineDustGrade = defaultString(snapshot.getFineDustGrade(), "");

        if (fineDustValue == null) {
            return WeatherInfoDto.AirQualityCard.empty();
        }

        String grade = !fineDustGrade.isBlank()
                ? fineDustGrade
                : resolveFineDustGrade(fineDustValue);

        return WeatherInfoDto.AirQualityCard.builder()
                .available(true)
                .value(fineDustValue)
                .pm10(fineDustValue)
                .grade(grade)
                .label(grade)
                .build();
    }

    private String resolveFineDustGrade(int pm10) {
        if (pm10 <= 30) {
            return "좋음";
        }
        if (pm10 <= 80) {
            return "보통";
        }
        if (pm10 <= 150) {
            return "나쁨";
        }
        return "매우 나쁨";
    }

    // 파서
    private String resolveAsosWeatherLabel(Double sumRn, Double ddMes, Double sumDpthFhsc, Double avgTca, String iscs) {
        if (defaultDouble(ddMes) > 0 || defaultDouble(sumDpthFhsc) > 0 || defaultString(iscs, "").contains("눈")) {
            return "눈";
        }
        if (defaultDouble(sumRn) > 0 || defaultString(iscs, "").contains("비")) {
            return "비";
        }
        if (avgTca != null) {
            if (avgTca >= 8) {
                return "흐림";
            }
            if (avgTca >= 6) {
                return "구름많음";
            }
        }
        return "맑음";
    }

    private VillageSlot findNearestSlot(List<VillageSlot> slots, String targetTime) {
        if (slots == null || slots.isEmpty()) {
            return new VillageSlot(targetTime);
        }

        int target = parseTimeValue(targetTime);

        return slots.stream()
                .min(Comparator.comparingInt(slot -> Math.abs(parseTimeValue(slot.getFcstTime()) - target)))
                .orElse(new VillageSlot(targetTime));
    }

    private int parseTimeValue(String time) {
        try {
            return Integer.parseInt(time);
        } catch (Exception e) {
            return 0;
        }
    }

    private String toWeatherLabel(VillageSlot slot) {
        if (slot == null) {
            return "기상정보 없음";
        }

        Integer pty = slot.getPty();
        if (pty != null && pty > 0) {
            return switch (pty) {
                case 1 -> "비";
                case 2 -> "비/눈";
                case 3 -> "눈";
                case 4 -> "소나기";
                default -> "강수";
            };
        }

        Integer sky = slot.getSky();
        if (sky == null) {
            return "기상정보 없음";
        }

        return switch (sky) {
            case 1 -> "맑음";
            case 3 -> "구름많음";
            case 4 -> "흐림";
            default -> "기상정보";
        };
    }

    private String normalizeAfternoonText(String label) {
        if (label.startsWith("오후 ")) {
            return label.substring(3);
        }
        return label;
    }

    private String pickMidWeatherLabel(String wfAm, String wfPm) {
        String am = normalizeMidLabel(wfAm);
        String pm = normalizeMidLabel(wfPm);

        if (am.isBlank() && pm.isBlank()) {
            return "기상정보 없음";
        }
        if (am.equals(pm) || pm.isBlank()) {
            return am;
        }
        if (am.isBlank()) {
            return pm;
        }
        return am + ", 오후 " + pm;
    }

    private String normalizeMidLabel(String text) {
        return text == null ? "" : text.trim();
    }

    // 3일치 일자 라벨
    private String formatDayLabel(LocalDate date) {
        LocalDate today = LocalDate.now();

        if (date.equals(today)) {
            return "오늘";
        }
        if (date.equals(today.plusDays(1))) {
            return "내일";
        }
        if (date.equals(today.plusDays(2))) {
            return "모레";
        }

        DayOfWeek dayOfWeek = date.getDayOfWeek();
        return switch (dayOfWeek) {
            case MONDAY -> "월요일";
            case TUESDAY -> "화요일";
            case WEDNESDAY -> "수요일";
            case THURSDAY -> "목요일";
            case FRIDAY -> "금요일";
            case SATURDAY -> "토요일";
            case SUNDAY -> "일요일";
        };
    }

    private String buildHeadlineTemp(Integer maxTemp, Integer minTemp) {
        return (maxTemp != null ? maxTemp : "--") + "°C / " + (minTemp != null ? minTemp : "--") + "°C";
    }

    private String formatDouble(double value) {
        if (Math.abs(value - Math.round(value)) < 0.0001) {
            return String.valueOf((int) Math.round(value));
        }
        return String.format(Locale.KOREA, "%.1f", value);
    }

    private String formatTemp(Double temp) {
        if (temp == null) {
            return "";
        }
        return " (" + (int) Math.round(temp) + "°C)";
    }

    private Integer extractTemperatureFromLabel(String label) {
        if (label == null) {
            return null;
        }
        Matcher matcher = Pattern.compile("(-?\\d+)°C").matcher(label);
        if (matcher.find()) {
            return Integer.parseInt(matcher.group(1));
        }
        return null;
    }

    private Double parseDouble(String value) {
        try {
            if (value == null || value.isBlank()) {
                return null;
            }
            return Double.parseDouble(value.trim());
        } catch (Exception e) {
            return null;
        }
    }

    private Integer parseInteger(String value) {
        try {
            if (value == null || value.isBlank()) {
                return null;
            }
            return Integer.parseInt(value.trim());
        } catch (Exception e) {
            return null;
        }
    }

    private Double parsePrecipitation(String value) {
        if (value == null || value.isBlank() || "강수없음".equals(value)) {
            return 0.0;
        }

        String normalized = value.replaceAll("[^0-9.]", "");
        if (normalized.isBlank()) {
            return 0.0;
        }

        try {
            return Double.parseDouble(normalized);
        } catch (Exception e) {
            return 0.0;
        }
    }

    private String firstNonBlank(String... values) {
        if (values == null) {
            return "";
        }

        for (String value : values) {
            if (value != null && !value.isBlank()) {
                return value;
            }
        }
        return "";
    }

    private String trimMessage(String message) {
        String normalized = defaultString(message, "")
                .replaceAll("\\s+", " ")
                .trim();
        return normalized.length() > 180 ? normalized.substring(0, 180) + "..." : normalized;
    }

    private boolean isInactiveAlert(String title, String message) {
        String merged = defaultString(title, "") + " " + defaultString(message, "");
        return merged.contains("해제") || merged.contains("종료") || merged.contains("없음");
    }

    private String inferAlertLevel(String text) {
        String target = defaultString(text, "");
        if (target.contains("경보")) {
            return "경고";
        }
        if (target.contains("주의보") || target.contains("주의")) {
            return "주의";
        }
        return "안내";
    }

    private List<JsonNode> toNodeList(JsonNode itemsNode) {
        List<JsonNode> result = new ArrayList<>();

        if (itemsNode == null || itemsNode.isMissingNode() || itemsNode.isNull()) {
            return result;
        }

        if (itemsNode.isArray()) {
            itemsNode.forEach(result::add);
            return result;
        }

        result.add(itemsNode);
        return result;
    }

    private JsonNode firstItem(JsonNode itemNode) {
        List<JsonNode> list = toNodeList(itemNode);
        return list.isEmpty() ? null : list.get(0);
    }

    private boolean isNormalService(JsonNode root) {
        String resultCode = root.path("response").path("header").path("resultCode").asText("");
        return "00".equals(resultCode) || "0".equals(resultCode);
    }

    private BaseDateTime resolveVillageBaseDateTime() {
        LocalDateTime now = LocalDateTime.now();
        LocalDate baseDate = now.toLocalDate();
        LocalTime time = now.toLocalTime();

        if (time.isBefore(LocalTime.of(2, 10))) {
            return new BaseDateTime(baseDate.minusDays(1).format(BASIC_DATE), "2300");
        }
        if (time.isBefore(LocalTime.of(5, 10))) {
            return new BaseDateTime(baseDate.format(BASIC_DATE), "0200");
        }
        if (time.isBefore(LocalTime.of(8, 10))) {
            return new BaseDateTime(baseDate.format(BASIC_DATE), "0500");
        }
        if (time.isBefore(LocalTime.of(11, 10))) {
            return new BaseDateTime(baseDate.format(BASIC_DATE), "0800");
        }
        if (time.isBefore(LocalTime.of(14, 10))) {
            return new BaseDateTime(baseDate.format(BASIC_DATE), "1100");
        }
        if (time.isBefore(LocalTime.of(17, 10))) {
            return new BaseDateTime(baseDate.format(BASIC_DATE), "1400");
        }
        if (time.isBefore(LocalTime.of(20, 10))) {
            return new BaseDateTime(baseDate.format(BASIC_DATE), "1700");
        }
        if (time.isBefore(LocalTime.of(23, 10))) {
            return new BaseDateTime(baseDate.format(BASIC_DATE), "2000");
        }

        return new BaseDateTime(baseDate.format(BASIC_DATE), "2300");
    }

    private String resolveMidTmFc() {
        LocalDate today = LocalDate.now();
        LocalTime now = LocalTime.now();

        if (now.isBefore(LocalTime.of(6, 0))) {
            return today.minusDays(1).format(BASIC_DATE) + "1800";
        }
        if (now.isBefore(LocalTime.of(18, 0))) {
            return today.format(BASIC_DATE) + "0600";
        }
        return today.format(BASIC_DATE) + "1800";
    }

    private String summarizeException(Exception e) {
        if (e == null) {
            return "unknown";
        }

        Throwable target = e.getCause() != null ? e.getCause() : e;
        String message = target.getMessage();
        String simpleMessage = message == null || message.isBlank() ? "no message" : message;
        return target.getClass().getSimpleName() + ": " + simpleMessage;
    }

    private String defaultString(String value, String fallback) {
        return (value == null || value.isBlank()) ? fallback : value;
    }

    private double defaultDouble(Double value) {
        return value == null ? 0.0 : value;
    }

    private int defaultInt(Integer value) {
        return value == null ? 0 : value;
    }

    private Integer roundInt(Double value) {
        return value == null ? null : (int) Math.round(value);
    }

    private Integer maxNullable(Integer a, Integer b) {
        if (a == null && b == null) {
            return null;
        }
        if (a == null) {
            return b;
        }
        if (b == null) {
            return a;
        }
        return Math.max(a, b);
    }

    @Getter
    @Setter
    @NoArgsConstructor
    @AllArgsConstructor
    private static class VillageSlot {
        private String fcstTime;
        private Double temperature;
        private Integer precipitationProbability;
        private Integer sky;
        private Integer pty;
        private Double windSpeed;
        private Double eastWestWindComponent;
        private Double northSouthWindComponent;
        private Double precipitationVolume;
        private Double dailyMin;
        private Double dailyMax;

        public VillageSlot(String fcstTime) {
            this.fcstTime = fcstTime;
        }
    }

    @Getter
    @Setter
    @Builder
    private static class DayWeather {
        private String summary;
        private String amLabel;
        private String pmLabel;
        private Integer minTemp;
        private Integer maxTemp;
        private Integer precipitationProbability;
        private Double maxWindSpeed;
        private boolean hasRain;
        private boolean hasSnow;
    }

    @Getter
    @Setter
    @Builder
    private static class AsosDay {
        private String summary;
        private String amLabel;
        private String pmLabel;
        private Double avgTa;
        private Double minTa;
        private Double maxTa;
        private Double sumRn;
        private Double maxInsWs;
        private Double ddMes;
        private Double sumDpthFhsc;
        private String iscs;
    }

    @Getter
    @AllArgsConstructor
    private static class BaseDateTime {
        private String baseDate;
        private String baseTime;
    }
}