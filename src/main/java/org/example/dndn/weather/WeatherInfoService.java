package org.example.dndn.weather;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.RequiredArgsConstructor;
import lombok.Setter;
import org.example.dndn.weather.model.WeatherInfo;
import org.example.dndn.weather.model.WeatherInfoDto;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
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

/**
 * 기상 관제 서비스.
 * - 단기예보(VilageFcst) + 중기예보(MidFcst) + 실측(ASOS) + 특보(WthrWrn) + 미세먼지(에어코리아)
 * - forecastDays 는 최대 10일치까지 채워 프론트의 주간/월간 표출에 사용
 * - 일자별 스냅샷을 WeatherInfo 에 캐시 (스케줄러 주기 갱신 + 30분 fresh check)
 * - 응답은 프론트(WeatherControlView.vue) spread 패턴에 맞춰 평탄(flat)하게 구성
 */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class WeatherInfoService {

    private static final DateTimeFormatter BASIC_DATE = DateTimeFormatter.BASIC_ISO_DATE;
    private static final DateTimeFormatter HHMM = DateTimeFormatter.ofPattern("HH:mm");

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

    private final RestTemplate restTemplate = new RestTemplate();
    private final ObjectMapper objectMapper = new ObjectMapper();

    // 기상 관제 대시보드 단건 조회 (캐시 우선)
    public WeatherInfoDto.DashboardRes readDashboard(LocalDate reportDate) {
        LocalDate targetDate = reportDate != null ? reportDate : LocalDate.now();

        try {
            WeatherInfo cached = weatherInfoRepository.findByReportDate(targetDate).orElse(null);
            if (isFreshSnapshot(cached)) {
                return fromSnapshot(cached);
            }

            WeatherInfoDto.DashboardRes response = buildDashboard(targetDate);
            weatherSnapshotWriter.save(targetDate, locationLabel, response);
            return response;
        } catch (Exception e) {
            return loadSnapshotOrFallback(targetDate);
        }
    }

    // 작업일보 자동 기입 등 다른 도메인용 간이 응답
    public WeatherInfoDto.TodaySimpleRes readTodaySimple(LocalDate reportDate) {
        return readDashboard(reportDate).toTodaySimpleRes();
    }

    /**
     * 스냅샷 강제 갱신 (스케줄러 / 워밍업 진입점).
     * 호출 측이 트랜잭션 외부이므로 read 와 별개로 fresh check 없이 빌드 후 저장한다.
     */
    public void refreshSnapshot(LocalDate targetDate) {
        try {
            WeatherInfoDto.DashboardRes response = buildDashboard(targetDate);
            weatherSnapshotWriter.save(targetDate, locationLabel, response);
        } catch (Exception ignored) {
        }
    }

    // ───────────────────────────────────────────────────────────────────────
    // 대시보드 빌드 (예보 / 실측 분기)
    // ───────────────────────────────────────────────────────────────────────
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

        DayWeather selectedDay = forecastMap.get(targetDate);
        // 월간 표출을 위해 이번 달 1일부터 다음 달 마지막까지 일자별로 채운다
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

        int maxPopInWindow = forecastDays.stream()
                .filter(day -> day.getDate() != null && !LocalDate.parse(day.getDate()).isBefore(targetDate))
                .limit(3)
                .map(WeatherInfoDto.ForecastDay::getPrecipitationProbability)
                .filter(Objects::nonNull)
                .max(Integer::compareTo)
                .orElse(defaultInt(selectedDay.getPrecipitationProbability()));

        WeatherInfoDto.WeekCard weekCard = WeatherInfoDto.WeekCard.builder()
                .summary(buildWeekSummary(targetDate, forecastDays, selectedDay, alerts))
                .subSummary("최대 풍속 " + formatDouble(defaultDouble(selectedDay.getMaxWindSpeed())) + "m/s · 3일 최고 강수확률 " + maxPopInWindow + "%")
                .build();

        WeatherInfoDto.RainCard rainCard = WeatherInfoDto.RainCard.builder()
                .label("강수확률")
                .value(maxPopInWindow + "%")
                .build();

        Integer fineDustValue = airQualityCard.getValue();
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
                .maxWindSpeed(defaultDouble(selectedDay.getMaxWindSpeed()))
                .fineDustValue(fineDustValue)
                .fineDustRisk(fineDustRisk)
                .hasRain(selectedDay.isHasRain())
                .hasSnow(selectedDay.isHasSnow())
                .heatRisk(selectedDay.getMaxTemp() != null && selectedDay.getMaxTemp() >= 33)
                .coldRisk(selectedDay.getMinTemp() != null && selectedDay.getMinTemp() <= -5)
                .windRisk(defaultDouble(selectedDay.getMaxWindSpeed()) >= 8)
                .build();

        return WeatherInfoDto.DashboardRes.builder()
                .reportDate(targetDate.toString())
                .locationLabel(locationLabel)
                .today(todayCard)
                .week(weekCard)
                .rain(rainCard)
                .airQuality(airQualityCard)
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
        WeatherInfoDto.TodayCard todayCard = WeatherInfoDto.TodayCard.builder()
                .headlineTemp("--°C / --°C")
                .summary("선택 날짜 예보 정보가 없습니다")
                .amLabel("기상정보 없음")
                .pmLabel("기상정보 없음")
                .observedAt(LocalTime.now().format(HHMM))
                .build();

        WeatherInfoDto.WeekCard weekCard = WeatherInfoDto.WeekCard.builder()
                .summary("예보 범위를 벗어났거나 응답값이 없습니다")
                .subSummary("지역코드 / 발표시각 / API 응답 상태 확인")
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
                .airQuality(airQualityCard)
                .analysis(WeatherInfoDto.WeatherAnalysis.empty(targetDate.toString()))
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

    // ───────────────────────────────────────────────────────────────────────
    // 기상청 OpenAPI 호출
    // ───────────────────────────────────────────────────────────────────────
    private Map<LocalDate, DayWeather> fetchVillageForecastMap() throws Exception {
        BaseDateTime baseDateTime = resolveVillageBaseDateTime();

        String url = UriComponentsBuilder.fromHttpUrl(villageUrl)
                .queryParam("serviceKey", serviceKey)
                .queryParam("pageNo", 1)
                .queryParam("numOfRows", 1000)
                .queryParam("dataType", "JSON")
                .queryParam("base_date", baseDateTime.getBaseDate())
                .queryParam("base_time", baseDateTime.getBaseTime())
                .queryParam("nx", nx)
                .queryParam("ny", ny)
                .build(false)
                .toUriString();

        JsonNode root = objectMapper.readTree(restTemplate.getForObject(url, String.class));

        if (!isNormalService(root)) {
            return new LinkedHashMap<>();
        }

        JsonNode itemsNode = root.path("response").path("body").path("items").path("item");
        Map<LocalDate, TreeMap<String, VillageSlot>> grouped = new TreeMap<>();

        for (JsonNode item : toNodeList(itemsNode)) {
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
                case "PCP" -> slot.setPrecipitationVolume(parsePrecipitation(value));
                case "TMN" -> slot.setDailyMin(parseDouble(value));
                case "TMX" -> slot.setDailyMax(parseDouble(value));
                default -> {
                }
            }
        }

        Map<LocalDate, DayWeather> result = new LinkedHashMap<>();
        for (Map.Entry<LocalDate, TreeMap<String, VillageSlot>> entry : grouped.entrySet()) {
            DayWeather dayWeather = buildVillageDayWeather(entry.getKey(), new ArrayList<>(entry.getValue().values()));
            result.put(entry.getKey(), dayWeather);
        }
        return result;
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
                return new LinkedHashMap<>();
            }

            JsonNode landItem = firstItem(landRoot.path("response").path("body").path("items").path("item"));
            JsonNode tempItem = firstItem(tempRoot.path("response").path("body").path("items").path("item"));

            if (landItem == null || tempItem == null) {
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
                        .maxWindSpeed(0.0)
                        .hasRain(defaultString(weatherLabel, "").contains("비"))
                        .hasSnow(defaultString(weatherLabel, "").contains("눈"))
                        .build();

                result.put(targetDate, dayWeather);
            }

            return result;
        } catch (Exception e) {
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

    // ───────────────────────────────────────────────────────────────────────
    // 일자/주간 예보 가공 (forecastDays 는 오늘부터 최대 10일)
    // ───────────────────────────────────────────────────────────────────────
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
                    .windSpeed(defaultDouble(day.getMaxWindSpeed()))
                    .build());

            if (result.size() >= 10) {
                break;
            }
        }

        return result.isEmpty() ? buildFallbackForecastDays(startDate) : result;
    }

    /**
     * 월간 표출용 확장 예보.
     * - 이번 달 1일부터 다음 달 마지막일까지 일자별로 채운다 (월간 그룹핑이 자연스럽도록)
     * - 과거 일자: ASOS 실측 (호출 비용 고려해 7일 이내만 시도)
     * - 오늘~+10일: 단기예보 + 중기예보 결과 사용
     * - 그 외: fallback (참고용 빈 데이터)
     */
    private List<WeatherInfoDto.ForecastDay> buildExtendedForecastDays(
            LocalDate baseDate,
            Map<LocalDate, DayWeather> forecastMap
    ) {
        LocalDate today = LocalDate.now();
        LocalDate startOfThisMonth = baseDate.withDayOfMonth(1);
        LocalDate endOfNextMonth = baseDate.plusMonths(1).withDayOfMonth(1)
                .plusMonths(1).minusDays(1);

        List<WeatherInfoDto.ForecastDay> result = new ArrayList<>();
        LocalDate cursor = startOfThisMonth;

        while (!cursor.isAfter(endOfNextMonth)) {
            DayWeather day = forecastMap.get(cursor);

            if (day == null && cursor.isBefore(today) && !cursor.isBefore(today.minusDays(7))) {
                AsosDay asosDay = fetchAsosDay(cursor);
                if (asosDay != null) {
                    int rainPercent = defaultDouble(asosDay.getSumRn()) > 0 ? 100 : 0;
                    day = DayWeather.builder()
                            .summary(defaultString(asosDay.getSummary(), "기상정보 없음"))
                            .amLabel(defaultString(asosDay.getAmLabel(), "기상정보 없음"))
                            .pmLabel(defaultString(asosDay.getPmLabel(), "기상정보 없음"))
                            .minTemp(roundInt(asosDay.getMinTa()))
                            .maxTemp(roundInt(asosDay.getMaxTa()))
                            .precipitationProbability(rainPercent)
                            .maxWindSpeed(asosDay.getMaxInsWs())
                            .hasRain(defaultDouble(asosDay.getSumRn()) > 0)
                            .hasSnow(defaultDouble(asosDay.getDdMes()) > 0 || defaultDouble(asosDay.getSumDpthFhsc()) > 0)
                            .build();
                }
            }

            if (day != null) {
                result.add(WeatherInfoDto.ForecastDay.builder()
                        .date(cursor.toString())
                        .dayLabel(formatDayLabel(cursor))
                        .weatherLabel(defaultString(day.getSummary(), "기상정보 없음"))
                        .minTemp(day.getMinTemp())
                        .maxTemp(day.getMaxTemp())
                        .precipitationProbability(defaultInt(day.getPrecipitationProbability()))
                        .windSpeed(defaultDouble(day.getMaxWindSpeed()))
                        .build());
            } else {
                result.add(WeatherInfoDto.ForecastDay.builder()
                        .date(cursor.toString())
                        .dayLabel(formatDayLabel(cursor))
                        .weatherLabel("예보 범위 외")
                        .minTemp(null)
                        .maxTemp(null)
                        .precipitationProbability(0)
                        .windSpeed(0.0)
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
                    .windSpeed(0.0)
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
                    .maxWindSpeed(0.0)
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
                .map(VillageSlot::getWindSpeed)
                .filter(Objects::nonNull)
                .max(Double::compareTo)
                .orElse(0.0);

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

    private String buildWeekSummary(
            LocalDate targetDate,
            List<WeatherInfoDto.ForecastDay> forecastDays,
            DayWeather selectedDay,
            List<WeatherInfoDto.AlertItem> alerts
    ) {
        if (!alerts.isEmpty()) {
            return alerts.get(0).getTitle();
        }

        if (defaultDouble(selectedDay.getMaxWindSpeed()) >= 10) {
            return formatDayLabel(targetDate) + " 강풍 주의 (" + formatDouble(defaultDouble(selectedDay.getMaxWindSpeed())) + "m/s)";
        }

        Optional<WeatherInfoDto.ForecastDay> rainyDay = forecastDays.stream()
                .filter(day -> day.getDate() != null && !LocalDate.parse(day.getDate()).isBefore(targetDate))
                .limit(3)
                .filter(day -> day.getPrecipitationProbability() != null && day.getPrecipitationProbability() >= 60)
                .findFirst();

        if (rainyDay.isPresent()) {
            return rainyDay.get().getDayLabel() + " 강수 대비 필요";
        }

        return "3일 내 특이 기상 없음";
    }

    // ───────────────────────────────────────────────────────────────────────
    // 위험 통제 추천 (장비 / 계획 연동)
    // ───────────────────────────────────────────────────────────────────────
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

    // ───────────────────────────────────────────────────────────────────────
    // 스냅샷 캐시 처리
    // ───────────────────────────────────────────────────────────────────────
    private boolean isFreshSnapshot(WeatherInfo snapshot) {
        if (snapshot == null || snapshot.getUpdatedAt() == null) {
            return false;
        }
        return snapshot.getUpdatedAt().isAfter(LocalDateTime.now().minusMinutes(30));
    }

    private WeatherInfoDto.DashboardRes loadSnapshotOrFallback(LocalDate targetDate) {
        return weatherInfoRepository.findByReportDate(targetDate)
                .map(this::fromSnapshot)
                .orElseGet(() -> buildFallbackDashboard(targetDate));
    }

    private WeatherInfoDto.DashboardRes fromSnapshot(WeatherInfo snapshot) {
        try {
            if (snapshot.getDashboardJson() != null && !snapshot.getDashboardJson().isBlank()) {
                WeatherInfoDto.DashboardRes parsed = objectMapper.readValue(
                        snapshot.getDashboardJson(),
                        WeatherInfoDto.DashboardRes.class
                );
                return WeatherInfoDto.DashboardRes.builder()
                        .reportDate(parsed.getReportDate() != null ? parsed.getReportDate() : snapshot.getReportDate().toString())
                        .locationLabel(parsed.getLocationLabel())
                        .today(parsed.getToday())
                        .week(parsed.getWeek())
                        .rain(parsed.getRain())
                        .airQuality(parsed.getAirQuality() != null ? parsed.getAirQuality() : WeatherInfoDto.AirQualityCard.empty())
                        .analysis(parsed.getAnalysis() != null ? parsed.getAnalysis() : WeatherInfoDto.WeatherAnalysis.empty(snapshot.getReportDate().toString()))
                        .equipmentRisks(parsed.getEquipmentRisks() != null ? parsed.getEquipmentRisks() : new ArrayList<>())
                        .planRisks(parsed.getPlanRisks() != null ? parsed.getPlanRisks() : new ArrayList<>())
                        .alerts(parsed.getAlerts() != null ? parsed.getAlerts() : new ArrayList<>())
                        .forecastDays(parsed.getForecastDays() != null ? parsed.getForecastDays() : buildFallbackForecastDays(snapshot.getReportDate()))
                        .build();
            }
        } catch (Exception ignored) {
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

        return WeatherInfoDto.DashboardRes.builder()
                .reportDate(snapshot.getReportDate().toString())
                .locationLabel(defaultString(snapshot.getLocationLabel(), locationLabel))
                .today(todayCard)
                .week(weekCard)
                .rain(rainCard)
                .airQuality(WeatherInfoDto.AirQualityCard.empty())
                .analysis(WeatherInfoDto.WeatherAnalysis.empty(snapshot.getReportDate().toString()))
                .equipmentRisks(new ArrayList<>())
                .planRisks(new ArrayList<>())
                .alerts(new ArrayList<>())
                .forecastDays(buildFallbackForecastDays(snapshot.getReportDate()))
                .build();
    }

    // ───────────────────────────────────────────────────────────────────────
    // 헬퍼 / 파서
    // ───────────────────────────────────────────────────────────────────────
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

    // 일자 라벨 - 오늘/내일/모레, 그 이후는 요일
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

    // ───────────────────────────────────────────────────────────────────────
    // 내부 VO
    // ───────────────────────────────────────────────────────────────────────
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
