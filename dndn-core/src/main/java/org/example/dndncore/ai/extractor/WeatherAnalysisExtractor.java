package org.example.dndncore.ai.extractor;

import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.example.dndncore.ai.dto.WeatherAiDto;
import org.example.dndncore.ai.entity.WeatherAiAnalysis;
import org.example.dndncore.ai.repository.WeatherAiAnalysisRepository;
import org.example.dndncore.project.model.entity.Project;
import org.example.dndncore.project.repository.ProjectRepository;
import org.example.dndncore.weather.WeatherInfoService;
import org.example.dndncore.weather.model.WeatherInfoDto;
import org.example.dndncore.workorder.WorkOrderService;
import org.example.dndncore.workorder.model.WorkOrderDto;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Slf4j
@Component
@RequiredArgsConstructor
public class WeatherAnalysisExtractor {

    private static final Duration ANALYSIS_REFRESH_INTERVAL = Duration.ofHours(1);

    private final OpenAiWeatherAnalyzer openAiWeatherAnalyzer;
    private final WeatherInfoService weatherInfoService;
    private final WorkOrderService workOrderService;
    private final WeatherAiAnalysisRepository weatherAiAnalysisRepository;
    private final ProjectRepository projectRepository;
    private final ObjectMapper objectMapper;

    /**
     * 기존 호출부 호환용 메서드.
     */
    public WeatherAiDto.AnalysisResult analyze(LocalDate analysisDate) {
        return analyze(analysisDate, null);
    }

    /**
     * 화면 조회용 API.
     * - projectId가 있으면 현장 + 날짜 기준 저장 결과를 우선 반환한다.
     * - 당일 저장 결과가 없으면 해당 현장/날짜 기준으로 1회 분석 후 저장한다.
     * - 과거/미래는 저장 결과가 없으면 신규 OpenAI 호출 없이 안내 문구만 반환한다.
     */
    public WeatherAiDto.AnalysisResult analyze(LocalDate analysisDate, Long projectId) {
        LocalDate targetDate = analysisDate != null ? analysisDate : LocalDate.now();
        LocalDate today = LocalDate.now();

        log.info("[기상분석] 저장 결과 조회 요청 - 날짜: {}, 현장: {}", targetDate, projectId);

        WeatherAiAnalysis savedSnapshot = findSavedSnapshot(targetDate, projectId);
        if (savedSnapshot != null) {
            WeatherAiDto.AnalysisResult savedResult = toAnalysisResult(savedSnapshot, targetDate, projectId);
            if (savedResult != null) {
                if (targetDate.equals(today) && isStaleTodaySnapshot(savedSnapshot)) {
                    log.info("[기상분석] 저장 결과가 1시간 이상 경과하여 재분석 수행 - 날짜: {}, 현장: {}, updatedAt: {}",
                            targetDate, projectId, savedSnapshot.getUpdatedAt());
                    return refreshTodayAnalysis(targetDate, projectId);
                }

                log.info("[기상분석] 저장된 AI 분석 결과 반환 - 날짜: {}, 현장: {}", targetDate, projectId);
                return savedResult;
            }
        }

        if (targetDate.isBefore(today)) {
            log.info("[기상분석] 과거 날짜 저장 결과 없음 - 신규 AI 호출 생략 - 날짜: {}, 현장: {}", targetDate, projectId);
            return createInfoResult("해당 날짜의 저장된 AI 분석 결과가 없습니다.");
        }

        if (targetDate.isAfter(today)) {
            log.info("[기상분석] 미래 날짜 저장 결과 없음 - 신규 AI 호출 생략 - 날짜: {}, 현장: {}", targetDate, projectId);
            return createInfoResult("미래 날짜의 AI 분석 결과는 제공되지 않습니다.");
        }

        log.info("[기상분석] 당일 저장 결과 없음 - 즉시 분석 수행 - 날짜: {}, 현장: {}", targetDate, projectId);
        return refreshTodayAnalysis(targetDate, projectId);
    }

    /**
     * 기존 스케줄러 호출부 호환용 메서드.
     */
    public WeatherAiDto.AnalysisResult refreshTodayAnalysis(LocalDate analysisDate) {
        return refreshTodayAnalysis(analysisDate, null);
    }

    /**
     * 정기 기상 갱신 완료 직후 모든 활성 현장 기준 AI 분석을 갱신한다.
     * 서버가 여러 대여도 상위 스케줄러의 Redis 분산락으로 1회만 실행된다.
     */
    public void refreshTodayAnalysisForActiveProjects(LocalDate analysisDate) {
        LocalDate today = LocalDate.now();
        LocalDate targetDate = analysisDate != null ? analysisDate : today;

        if (!targetDate.equals(today)) {
            log.info("[기상분석] 활성 현장 정기 갱신 생략 - 당일이 아님 - date={}", targetDate);
            return;
        }

        List<Project> activeProjects = projectRepository.findAll().stream()
                .filter(Project::isActive)
                .toList();

        if (activeProjects.isEmpty()) {
            log.info("[기상분석] 활성 현장이 없어 공통 분석만 갱신합니다. - date={}", targetDate);
            refreshTodayAnalysis(targetDate, null);
            return;
        }

        for (Project project : activeProjects) {
            try {
                refreshTodayAnalysis(targetDate, project.getIdx());
            } catch (Exception exception) {
                log.warn("[기상분석] 현장별 정기 AI 분석 갱신 실패 - date={}, projectId={}",
                        targetDate, project.getIdx(), exception);
            }
        }
    }

    /**
     * 정기 기상 갱신 완료 직후 또는 당일 화면 최초 조회 시 실행되는 AI 갱신.
     * - 오늘 날짜에만 동작한다.
     * - 기상관제는 장비가 없는 작업지시도 분석 대상에 포함한다.
     * - projectId가 있으면 해당 현장 작업지시만 분석하고 저장한다.
     */
    public WeatherAiDto.AnalysisResult refreshTodayAnalysis(LocalDate analysisDate, Long projectId) {
        LocalDate today = LocalDate.now();
        LocalDate targetDate = analysisDate != null ? analysisDate : today;

        if (!targetDate.equals(today)) {
            log.info("[기상분석] 당일 이외 정기 분석 요청 생략 - 날짜: {}, 현장: {}", targetDate, projectId);
            WeatherAiDto.AnalysisResult savedResult = findSavedResult(targetDate, projectId);
            return savedResult != null
                    ? savedResult
                    : createInfoResult("해당 날짜의 저장된 AI 분석 결과가 없습니다.");
        }

        log.info("[기상분석] 당일 AI 분석 갱신 시작 - 날짜: {}, 현장: {}", targetDate, projectId);

        List<WorkOrderDto.GateEquipmentRes> workOrders = workOrderService.getGateEquipments(targetDate, projectId, true);
        List<WorkOrderDto.GateEquipmentRes> analysisTargets = workOrders != null ? workOrders : List.of();
        boolean hasWorkOrders = !analysisTargets.isEmpty();

        WeatherInfoDto.DashboardRes dashboard = weatherInfoService.readDashboard(targetDate);
        WeatherInfoDto.WeatherAnalysis weather = dashboard != null ? dashboard.getAnalysis() : null;
        if (!hasUsableWeatherAnalysis(weather)) {
            WeatherAiDto.AnalysisResult weatherUnavailableResult = createInfoResult("최신 기상 스냅샷이 없어 AI 분석을 보류했습니다.");
            saveResult(targetDate, projectId, weatherUnavailableResult);
            log.info("[기상분석] 기상 스냅샷 없음 결과 저장 완료 - 날짜: {}, 현장: {}", targetDate, projectId);
            return weatherUnavailableResult;
        }

        try {
            WeatherAiDto.AnalysisRequest request = prepareAnalysisRequest(targetDate, analysisTargets, weather);
            WeatherAiDto.AnalysisResult result = openAiWeatherAnalyzer.analyze(request);
            if (!hasWorkOrders) {
                result = normalizeNoWorkOrderWeatherActionResult(result);
            }
            saveResult(targetDate, projectId, result);

            log.info("[기상분석] 당일 AI 분석 저장 완료 - 날짜: {}, 현장: {}, 작업지시: {}, 위험항목: {}, 조치: {}",
                    targetDate,
                    projectId,
                    hasWorkOrders ? "있음" : "없음",
                    result.getRisks() != null ? result.getRisks().size() : 0,
                    result.getActions() != null ? result.getActions().size() : 0);

            return result;
        } catch (Exception e) {
            log.error("[기상분석] 당일 AI 분석 실패 - 날짜: {}, 현장: {}", targetDate, projectId, e);
            return createErrorResult(e);
        }
    }

    private WeatherAiAnalysis findSavedSnapshot(LocalDate targetDate, Long projectId) {
        return weatherAiAnalysisRepository.findSnapshot(projectId, targetDate).orElse(null);
    }

    private WeatherAiDto.AnalysisResult findSavedResult(LocalDate targetDate, Long projectId) {
        WeatherAiAnalysis snapshot = findSavedSnapshot(targetDate, projectId);
        return snapshot == null ? null : toAnalysisResult(snapshot, targetDate, projectId);
    }

    private WeatherAiDto.AnalysisResult toAnalysisResult(WeatherAiAnalysis snapshot, LocalDate targetDate, Long projectId) {
        try {
            return objectMapper.readValue(snapshot.getResultJson(), WeatherAiDto.AnalysisResult.class);
        } catch (Exception e) {
            log.warn("[기상분석] 저장된 AI 분석 결과 파싱 실패 - date={}, projectId={}", targetDate, projectId);
            return null;
        }
    }

    private boolean isStaleTodaySnapshot(WeatherAiAnalysis snapshot) {
        if (snapshot == null || snapshot.getUpdatedAt() == null) {
            return true;
        }
        LocalDateTime refreshThreshold = LocalDateTime.now().minus(ANALYSIS_REFRESH_INTERVAL);
        return snapshot.getUpdatedAt().isBefore(refreshThreshold);
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

            if (hasRealEquipment(item)) {
                bucket.equipments().add(WeatherAiDto.EquipmentInfo.builder()
                        .name(firstNonBlank(item.getEquipmentName(), "장비 미지정"))
                        .type(firstNonBlank(item.getEquipmentType(), "중장비"))
                        .count(item.getEquipmentCount() != null ? item.getEquipmentCount() : 1)
                        .build());
            }
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

    private boolean hasRealEquipment(WorkOrderDto.GateEquipmentRes item) {
        return item != null
                && item.getIdx() != null
                && item.getEquipmentName() != null
                && !item.getEquipmentName().isBlank()
                && !"장비 미지정".equals(item.getEquipmentName());
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

    private void saveResult(LocalDate targetDate, Long projectId, WeatherAiDto.AnalysisResult result) {
        try {
            String resultJson = objectMapper.writeValueAsString(result);
            WeatherAiAnalysis snapshot = weatherAiAnalysisRepository.findSnapshot(projectId, targetDate)
                    .orElseGet(() -> WeatherAiAnalysis.create(
                            projectId,
                            targetDate,
                            result.getOverallSafety(),
                            result.getNote(),
                            resultJson
                    ));

            snapshot.updateResult(result.getOverallSafety(), result.getNote(), resultJson);
            weatherAiAnalysisRepository.save(snapshot);
        } catch (Exception e) {
            log.warn("[기상분석] AI 분석 결과 저장 실패 - date={}, projectId={}, message={}", targetDate, projectId, e.getMessage());
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
