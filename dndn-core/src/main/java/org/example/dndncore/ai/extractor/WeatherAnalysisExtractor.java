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

    /**
     * 화면 조회용 API.
     * - 과거/미래/당일 모두 저장된 AI 분석 결과가 있으면 DB 결과만 반환한다.
     * - 저장 결과가 없으면 사용자 안내용 문구만 반환하며, OpenAI 신규 호출은 하지 않는다.
     * - 실제 OpenAI 분석은 1시간 기상 갱신 직후 refreshTodayAnalysis()에서만 수행한다.
     */
    public WeatherAiDto.AnalysisResult analyze(LocalDate analysisDate) {
        LocalDate targetDate = analysisDate != null ? analysisDate : LocalDate.now();
        LocalDate today = LocalDate.now();

        log.info("[기상분석] 저장 결과 조회 요청 - 날짜: {}", targetDate);

        WeatherAiDto.AnalysisResult savedResult = findSavedResult(targetDate);
        if (savedResult != null) {
            log.info("[기상분석] 저장된 AI 분석 결과 반환 - 날짜: {}", targetDate);
            return savedResult;
        }

        if (targetDate.isBefore(today)) {
            log.info("[기상분석] 과거 날짜 저장 결과 없음 - 신규 AI 호출 생략 - 날짜: {}", targetDate);
            return createInfoResult("해당 날짜의 저장된 AI 분석 결과가 없습니다.");
        }

        if (targetDate.isAfter(today)) {
            log.info("[기상분석] 미래 날짜 저장 결과 없음 - 신규 AI 호출 생략 - 날짜: {}", targetDate);
            return createInfoResult("미래 날짜의 AI 분석 결과는 제공되지 않습니다.");
        }

        List<WorkOrderDto.GateEquipmentRes> gateEquipments = workOrderService.getGateEquipments(targetDate);
        if (gateEquipments == null || gateEquipments.isEmpty()) {
            log.info("[기상분석] 오늘 작업지시서 없음 - 저장 없이 안내 문구 반환 - 날짜: {}", targetDate);
            return createNoWorkOrderResult();
        }

        log.info("[기상분석] 오늘 저장 결과 없음 - 정기 기상 갱신 후 AI 분석 결과 제공 예정 - 날짜: {}", targetDate);
        return createInfoResult("최신 기상 갱신 이후 AI 분석 결과가 제공됩니다.");
    }

    /**
     * 정기 기상 갱신 완료 직후 실행되는 당일 AI 갱신.
     * - 오늘 날짜에만 동작한다.
     * - 작업지시가 없어도 최신 weather_info snapshot 기준 조치추천(actions)은 생성·저장한다.
     * - 작업지시가 없으면 작업지시 기반 위험항목(risks)은 비워두고, 안내 문구는 유지한다.
     * - 작업지시가 있으면 최신 weather_info snapshot과 작업지시를 기준으로 OpenAI 분석을 다시 수행하고 DB에 update한다.
     */
    public WeatherAiDto.AnalysisResult refreshTodayAnalysis(LocalDate analysisDate) {
        LocalDate today = LocalDate.now();
        LocalDate targetDate = analysisDate != null ? analysisDate : today;

        if (!targetDate.equals(today)) {
            log.info("[기상분석] 당일 이외 정기 분석 요청 생략 - 날짜: {}", targetDate);
            WeatherAiDto.AnalysisResult savedResult = findSavedResult(targetDate);
            return savedResult != null
                    ? savedResult
                    : createInfoResult("해당 날짜의 저장된 AI 분석 결과가 없습니다.");
        }

        log.info("[기상분석] 정기 당일 AI 분석 갱신 시작 - 날짜: {}", targetDate);

        List<WorkOrderDto.GateEquipmentRes> gateEquipments = workOrderService.getGateEquipments(targetDate);
        List<WorkOrderDto.GateEquipmentRes> analysisEquipments = gateEquipments != null ? gateEquipments : List.of();
        boolean hasWorkOrders = !analysisEquipments.isEmpty();

        WeatherInfoDto.DashboardRes dashboard = weatherInfoService.readDashboard(targetDate);
        WeatherInfoDto.WeatherAnalysis weather = dashboard != null ? dashboard.getAnalysis() : null;
        if (!hasUsableWeatherAnalysis(weather)) {
            WeatherAiDto.AnalysisResult weatherUnavailableResult = createInfoResult("최신 기상 스냅샷이 없어 AI 분석을 보류했습니다.");
            saveResult(targetDate, weatherUnavailableResult);
            log.info("[기상분석] 기상 스냅샷 없음 결과 저장 완료 - 날짜: {}", targetDate);
            return weatherUnavailableResult;
        }

        try {
            WeatherAiDto.AnalysisRequest request = prepareAnalysisRequest(targetDate, analysisEquipments, weather);
            WeatherAiDto.AnalysisResult result = openAiWeatherAnalyzer.analyze(request);
            if (!hasWorkOrders) {
                result = normalizeNoWorkOrderWeatherActionResult(result);
            }
            saveResult(targetDate, result);

            log.info("[기상분석] 정기 당일 AI 분석 저장 완료 - 날짜: {}, 작업지시: {}, 위험항목: {}, 조치: {}",
                    targetDate,
                    hasWorkOrders ? "있음" : "없음",
                    result.getRisks() != null ? result.getRisks().size() : 0,
                    result.getActions() != null ? result.getActions().size() : 0);

            return result;
        } catch (Exception e) {
            log.error("[기상분석] 정기 당일 AI 분석 실패", e);
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

    private boolean hasUsableWeatherAnalysis(WeatherInfoDto.WeatherAnalysis weather) {
        if (weather == null) {
            return false;
        }

        String sourceType = weather.getSourceType();
        return sourceType != null && !sourceType.isBlank() && !"EMPTY".equals(sourceType);
    }

    private WeatherAiDto.AnalysisRequest prepareAnalysisRequest(
            LocalDate analysisDate,
            List<WorkOrderDto.GateEquipmentRes> gateEquipments,
            WeatherInfoDto.WeatherAnalysis weather
    ) {
        List<WeatherAiDto.WorkTaskInfo> workTasks = toWorkTasks(gateEquipments);

        return WeatherAiDto.AnalysisRequest.builder()
                .temperature(resolveTemperature(weather))
                .humidity(null)
                .windSpeed(weather != null ? weather.getMaxWindSpeed() : null)
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

    private WeatherAiDto.AnalysisResult normalizeNoWorkOrderWeatherActionResult(WeatherAiDto.AnalysisResult result) {
        if (result == null) {
            return createNoWorkOrderResult();
        }

        result.setRisks(List.of());
        if (result.getActions() == null) {
            result.setActions(List.of());
        }
        result.setNote("해당 날짜에 등록된 작업지시서가 없어 AI 분석을 생략했습니다.");
        return result;
    }


    private WeatherAiDto.AnalysisResult createInfoResult(String note) {
        return WeatherAiDto.AnalysisResult.builder()
                .risks(List.of())
                .actions(List.of())
                .overallSafety("SAFE")
                .note(note)
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
