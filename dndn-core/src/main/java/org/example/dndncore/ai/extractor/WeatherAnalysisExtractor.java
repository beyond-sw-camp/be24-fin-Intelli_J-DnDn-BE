package org.example.dndncore.ai.extractor;

import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.example.dndncore.ai.dto.WeatherAiDto;
import org.example.dndncore.ai.entity.WeatherAiAnalysis;
import org.example.dndncore.ai.repository.WeatherAiAnalysisRepository;
import org.example.dndncore.weather.WeatherInfoService;
import org.example.dndncore.weather.model.WeatherInfoDto;
import org.example.dndncore.workorder.WorkOrderService;
import org.example.dndncore.workorder.model.WorkOrderDto;
import org.springframework.stereotype.Component;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Slf4j
@Component
@RequiredArgsConstructor
public class WeatherAnalysisExtractor {

    private final OpenAiWeatherAnalyzer openAiWeatherAnalyzer;
    private final WeatherInfoService weatherInfoService;
    private final WorkOrderService workOrderService;
    private final WeatherAiAnalysisRepository weatherAiAnalysisRepository;
    private final ObjectMapper objectMapper;

    public WeatherAiDto.AnalysisResult analyze(LocalDate analysisDate) {
        LocalDate targetDate = analysisDate != null ? analysisDate : LocalDate.now();

        log.info("[기상분석] 분석 요청 - 날짜: {}", targetDate);

        WeatherAiDto.AnalysisResult savedResult = findSavedResult(targetDate);
        if (savedResult != null) {
            log.info("[기상분석] 저장된 AI 분석 결과 반환 - 날짜: {}", targetDate);
            return savedResult;
        }

        List<WorkOrderDto.GateEquipmentRes> gateEquipments = workOrderService.getGateEquipments(targetDate);
        if (gateEquipments == null || gateEquipments.isEmpty()) {
            log.info("[기상분석] 작업지시서 없음 - AI 호출 생략 - 날짜: {}", targetDate);
            return createNoWorkOrderResult();
        }

        try {
            WeatherAiDto.AnalysisRequest request = prepareAnalysisRequest(targetDate, gateEquipments);
            WeatherAiDto.AnalysisResult result = openAiWeatherAnalyzer.analyze(request);
            saveResult(targetDate, result);

            log.info("[기상분석] 신규 AI 분석 저장 완료 - 날짜: {}, 위험항목: {}, 조치: {}",
                    targetDate,
                    result.getRisks() != null ? result.getRisks().size() : 0,
                    result.getActions() != null ? result.getActions().size() : 0);

            return result;
        } catch (Exception e) {
            log.error("[기상분석] 분석 실패", e);
            return createErrorResult(e);
        }
    }

    private WeatherAiDto.AnalysisResult findSavedResult(LocalDate targetDate) {
        return weatherAiAnalysisRepository.findByAnalysisDate(targetDate)
                .map(snapshot -> {
                    try {
                        return objectMapper.readValue(snapshot.getResultJson(), WeatherAiDto.AnalysisResult.class);
                    } catch (Exception e) {
                        log.warn("[기상분석] 저장된 AI 분석 결과 파싱 실패 - date={}", targetDate);
                        return null;
                    }
                })
                .orElse(null);
    }

    private WeatherAiDto.AnalysisRequest prepareAnalysisRequest(
            LocalDate analysisDate,
            List<WorkOrderDto.GateEquipmentRes> gateEquipments
    ) {
        WeatherInfoDto.DashboardRes dashboard = weatherInfoService.readDashboard(analysisDate);
        WeatherInfoDto.WeatherAnalysis weather = dashboard != null ? dashboard.getAnalysis() : null;
        List<WeatherAiDto.WorkTaskInfo> workTasks = toWorkTasks(gateEquipments);

        return WeatherAiDto.AnalysisRequest.builder()
                .temperature(resolveTemperature(weather))
                .humidity(null)
                .windSpeed(weather != null ? weather.getMaxWindSpeed() : 0.0)
                .precipitationProbability(weather != null ? weather.getPrecipitationProbability() : 0)
                .pm10(weather != null ? weather.getFineDustValue() : null)
                .pm25(null)
                .workTasks(workTasks)
                .analysisDate(analysisDate)
                .build();
    }

    private List<WeatherAiDto.WorkTaskInfo> toWorkTasks(List<WorkOrderDto.GateEquipmentRes> gateEquipments) {
        Map<Long, WorkTaskBucket> buckets = new LinkedHashMap<>();

        for (WorkOrderDto.GateEquipmentRes item : gateEquipments) {
            Long key = item.getWorkOrderIdx() != null ? item.getWorkOrderIdx() : item.getIdx();

            WorkTaskBucket bucket = buckets.computeIfAbsent(key, ignored -> new WorkTaskBucket(
                    firstNonBlank(item.getTitle(), "작업 지시서"),
                    firstNonBlank(item.getWorkDetail(), "작업 상세내역 없음"),
                    firstNonBlank(item.getWorkLocation(), "작업구역 미지정"),
                    firstNonBlank(item.getTradeType(), "공종 미지정")
            ));

            bucket.equipments().add(WeatherAiDto.EquipmentInfo.builder()
                    .name(firstNonBlank(item.getEquipmentName(), "장비 미지정"))
                    .type(firstNonBlank(item.getEquipmentType(), "중장비"))
                    .count(item.getEquipmentCount() != null ? item.getEquipmentCount() : 1)
                    .build());
        }

        return buckets.values().stream()
                .map(bucket -> WeatherAiDto.WorkTaskInfo.builder()
                        .title(bucket.title())
                        .workDetail(bucket.workDetail())
                        .workLocation(bucket.workLocation())
                        .tradeType(bucket.tradeType())
                        .equipments(bucket.equipments())
                        .build())
                .toList();
    }

    private Double resolveTemperature(WeatherInfoDto.WeatherAnalysis weather) {
        if (weather == null) {
            return null;
        }

        Integer max = weather.getMaxTemperature();
        Integer min = weather.getMinTemperature();

        if (max != null && min != null) {
            return (max + min) / 2.0;
        }
        if (max != null) {
            return max.doubleValue();
        }
        if (min != null) {
            return min.doubleValue();
        }

        return null;
    }

    private void saveResult(LocalDate targetDate, WeatherAiDto.AnalysisResult result) {
        try {
            String resultJson = objectMapper.writeValueAsString(result);
            WeatherAiAnalysis snapshot = weatherAiAnalysisRepository.findByAnalysisDate(targetDate)
                    .orElseGet(() -> WeatherAiAnalysis.create(
                            targetDate,
                            result.getOverallSafety(),
                            result.getNote(),
                            resultJson
                    ));

            snapshot.updateResult(result.getOverallSafety(), result.getNote(), resultJson);
            weatherAiAnalysisRepository.save(snapshot);
        } catch (Exception e) {
            log.warn("[기상분석] AI 분석 결과 저장 실패 - date={}, message={}", targetDate, e.getMessage());
        }
    }

    private WeatherAiDto.AnalysisResult createNoWorkOrderResult() {
        return WeatherAiDto.AnalysisResult.builder()
                .risks(List.of())
                .actions(List.of())
                .overallSafety("SAFE")
                .note("해당 날짜에 등록된 작업지시서가 없어 AI 분석을 생략했습니다.")
                .build();
    }

    private WeatherAiDto.AnalysisResult createErrorResult(Exception e) {
        return WeatherAiDto.AnalysisResult.builder()
                .risks(List.of())
                .actions(List.of())
                .overallSafety("WARNING")
                .note("분석 중 오류가 발생했습니다: " + e.getMessage())
                .build();
    }

    private String firstNonBlank(String... values) {
        if (values == null) {
            return null;
        }

        for (String value : values) {
            if (value != null && !value.isBlank()) {
                return value;
            }
        }

        return null;
    }

    private record WorkTaskBucket(
            String title,
            String workDetail,
            String workLocation,
            String tradeType,
            List<WeatherAiDto.EquipmentInfo> equipments
    ) {
        private WorkTaskBucket(String title, String workDetail, String workLocation, String tradeType) {
            this(title, workDetail, workLocation, tradeType, new ArrayList<>());
        }
    }
}