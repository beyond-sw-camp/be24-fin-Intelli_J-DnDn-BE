package org.example.dndncore.weather;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.example.dndncore.esg.event.EsgSnapshotRefreshEventPublisher;
import org.example.dndncore.weather.model.WeatherInfo;
import org.example.dndncore.weather.model.WeatherInfoDto;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalDateTime;

@Slf4j
@Component
@RequiredArgsConstructor
public class WeatherSnapshotWriter {

    private final WeatherInfoRepository weatherInfoRepository;
    private final EsgSnapshotRefreshEventPublisher esgSnapshotRefreshEventPublisher;
    private final ObjectMapper objectMapper = new ObjectMapper();

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void save(LocalDate targetDate, String locationLabel, WeatherInfoDto.DashboardRes response) {
        try {
            if (targetDate == null || response == null) {
                log.warn("[기상 스냅샷 저장] 저장 요청 무시 - targetDate={}, responseNull={}", targetDate, response == null);
                return;
            }

            String dashboardJson = objectMapper.writeValueAsString(response);
            String todayHeadlineTemp = response.getToday() != null ? response.getToday().getHeadlineTemp() : "--°C / --°C";
            String todaySummary = response.getToday() != null ? response.getToday().getSummary() : "기상 정보 없음";
            String amLabel = response.getToday() != null ? response.getToday().getAmLabel() : "기상정보 없음";
            String pmLabel = response.getToday() != null ? response.getToday().getPmLabel() : "기상정보 없음";
            String weeklySummary = response.getWeek() != null ? response.getWeek().getSummary() : "3일 내 특이 기상 없음";
            String precipitationProbability = response.getRain() != null ? response.getRain().getValue() : "0%";
            Integer fineDustValue = response.getAirQuality() != null ? response.getAirQuality().getValue() : null;
            String fineDustValueText = fineDustValue == null ? "" : String.valueOf(fineDustValue);
            String fineDustGrade = response.getAirQuality() != null ? response.getAirQuality().getGrade() : "";
            LocalDateTime now = LocalDateTime.now();

            int updatedRows = weatherInfoRepository.updateSnapshotByReportDate(
                    targetDate,
                    locationLabel,
                    todayHeadlineTemp,
                    todaySummary,
                    amLabel,
                    pmLabel,
                    weeklySummary,
                    precipitationProbability,
                    fineDustValueText,
                    fineDustGrade == null ? "" : fineDustGrade,
                    dashboardJson,
                    now
            );

            String saveMode = "UPDATE";
            if (updatedRows == 0) {
                WeatherInfo snapshot = WeatherInfo.builder()
                        .reportDate(targetDate)
                        .locationLabel(locationLabel)
                        .amLabel(amLabel)
                        .pmLabel(pmLabel)
                        .todayHeadlineTemp(todayHeadlineTemp)
                        .todaySummary(todaySummary)
                        .weeklySummary(weeklySummary)
                        .precipitationProbability(precipitationProbability)
                        .fineDustValue(fineDustValueText)
                        .fineDustGrade(fineDustGrade == null ? "" : fineDustGrade)
                        .dashboardJson(dashboardJson)
                        .build();

                weatherInfoRepository.saveAndFlush(snapshot);
                saveMode = "INSERT";
            }

            esgSnapshotRefreshEventPublisher.publishDate(targetDate);
            log.info("[기상 스냅샷 저장] 저장 완료 - targetDate={}, mode={}, rows={}, sourceType={}, rain={}, wind={}, airAvailable={}, fineDustValue={}, forecastDays={}",
                    targetDate,
                    saveMode,
                    updatedRows,
                    sourceTypeOf(response),
                    precipitationProbability,
                    windSpeedOf(response),
                    response.getAirQuality() != null && response.getAirQuality().isAvailable(),
                    fineDustValue,
                    response.getForecastDays() != null ? response.getForecastDays().size() : 0);

            verifySavedSnapshot(targetDate);
        } catch (Exception e) {
            log.error("[기상 스냅샷 저장] weather_info 저장 실패 - targetDate={}", targetDate, e);
        }
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void touch(LocalDate targetDate, String reason) {
        if (targetDate == null) {
            return;
        }

        try {
            int touched = weatherInfoRepository.touchUpdatedAt(targetDate, LocalDateTime.now());
            log.info("[기상 스냅샷 저장] 내용 유지 후 갱신시각만 반영 - targetDate={}, touched={}, reason={}",
                    targetDate,
                    touched,
                    reason);
        } catch (Exception e) {
            log.error("[기상 스냅샷 저장] updated_at touch 실패 - targetDate={}, reason={}", targetDate, reason, e);
        }
    }

    private void verifySavedSnapshot(LocalDate targetDate) {
        try {
            weatherInfoRepository.findByReportDate(targetDate).ifPresentOrElse(snapshot -> {
                SnapshotValues values = parseSnapshotValues(snapshot.getDashboardJson());
                log.info("[기상 스냅샷 저장검증] DB 반영 확인 - targetDate={}, updatedAt={}, columnPm10={}, columnPm10Grade={}, sourceType={}, wind={}, airAvailable={}, jsonPm10={}",
                        targetDate,
                        snapshot.getUpdatedAt(),
                        snapshot.getFineDustValue(),
                        snapshot.getFineDustGrade(),
                        values.sourceType(),
                        values.windSpeed(),
                        values.airAvailable(),
                        values.fineDustValue());
            }, () -> log.warn("[기상 스냅샷 저장검증] DB row 없음 - targetDate={}", targetDate));
        } catch (Exception e) {
            log.warn("[기상 스냅샷 저장검증] DB 반영 확인 실패 - targetDate={}, reason={}", targetDate, summarizeException(e));
        }
    }

    private SnapshotValues parseSnapshotValues(String dashboardJson) {
        if (dashboardJson == null || dashboardJson.isBlank()) {
            return new SnapshotValues("NONE", null, false, null);
        }

        try {
            JsonNode root = objectMapper.readTree(dashboardJson);
            JsonNode analysisNode = root.path("analysis");
            JsonNode airQualityNode = root.path("airQuality");

            String sourceType = analysisNode.path("sourceType").asText("NONE");
            Double windSpeed = asNullableDouble(analysisNode.path("maxWindSpeed"));
            boolean airAvailable = airQualityNode.path("available").asBoolean(false);
            Integer fineDustValue = asNullableInteger(airQualityNode.path("value"));

            return new SnapshotValues(sourceType, windSpeed, airAvailable, fineDustValue);
        } catch (Exception e) {
            return new SnapshotValues("PARSE_FAILED", null, false, null);
        }
    }

    private String sourceTypeOf(WeatherInfoDto.DashboardRes response) {
        if (response == null || response.getAnalysis() == null) {
            return "NONE";
        }
        return response.getAnalysis().getSourceType();
    }

    private Double windSpeedOf(WeatherInfoDto.DashboardRes response) {
        if (response == null || response.getAnalysis() == null) {
            return null;
        }
        return response.getAnalysis().getMaxWindSpeed();
    }

    private Double asNullableDouble(JsonNode node) {
        if (node == null || node.isMissingNode() || node.isNull()) {
            return null;
        }
        return node.asDouble();
    }

    private Integer asNullableInteger(JsonNode node) {
        if (node == null || node.isMissingNode() || node.isNull()) {
            return null;
        }
        return node.asInt();
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

    private record SnapshotValues(
            String sourceType,
            Double windSpeed,
            boolean airAvailable,
            Integer fineDustValue
    ) {
    }
}
