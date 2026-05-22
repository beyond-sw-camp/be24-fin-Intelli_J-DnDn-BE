package org.example.dndncore.ai.extractor;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.example.dndncore.ai.dto.WeatherAiDto;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClient;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;

@Slf4j
@Component
@RequiredArgsConstructor
public class OpenAiWeatherAnalyzer {

    private final ObjectMapper objectMapper;

    @Value("${openai.api-key}")
    private String apiKey;

    @Value("${openai.model:gpt-5.5}")
    private String model;

    private final WebClient webClient = WebClient.builder()
            .baseUrl("https://api.openai.com/v1")
            .build();

    public WeatherAiDto.AnalysisResult analyze(WeatherAiDto.AnalysisRequest request) {
        try {
            log.info("[OpenAI분석] 시작 - 날짜: {}, 작업: {}건",
                    request.getAnalysisDate(),
                    request.getWorkTasks() != null ? request.getWorkTasks().size() : 0);

            String aiResponse = callOpenAi(buildPrompt(request));
            WeatherAiDto.AnalysisResult result = parseResponse(aiResponse);

            log.info("[OpenAI분석] 완료 - 위험항목: {}, 조치: {}",
                    result.getRisks() != null ? result.getRisks().size() : 0,
                    result.getActions() != null ? result.getActions().size() : 0);

            return result;
        } catch (Exception e) {
            log.error("[OpenAI분석] 실패 - 규칙 기반 fallback 반환", e);
            return createFallbackResult(request);
        }
    }

    private String buildPrompt(WeatherAiDto.AnalysisRequest request) {
        StringBuilder prompt = new StringBuilder();

        prompt.append("너는 DnDn 건설현장 기상관제 AI다.\n");
        prompt.append("오늘 날씨와 작업지시서의 작업상세내역, 중장비명을 비교해서 위험 작업과 위험 장비를 찾는다.\n");
        prompt.append("작업지시서에 있는 내용은 risks에 반영하고, 현장에서 바로 확인해야 하는 조치는 actions에 작성한다.\n");
        prompt.append("작업지시서가 없어도 actions에는 오늘 기상만으로 선제 확인이 필요한 대표 작업 유형, 장비, 현장 조치를 작성한다.\n");
        prompt.append("작업지시서가 없으면 risks는 빈 배열로 유지하고, actions만 기상 기준으로 작성한다.\n");
        prompt.append("문장은 현장관리자가 바로 읽을 수 있게 '~해 주세요', '~바랍니다', '~검토해 주세요'의 부드러운 요청형으로 작성한다.\n");
        prompt.append("'실시한다', '통제한다', '결정한다'처럼 딱딱한 명령형은 사용하지 않는다.\n\n");

        prompt.append("[분석 날짜]\n");
        prompt.append("- ").append(request.getAnalysisDate()).append("\n\n");

        prompt.append("[금일 기상]\n");
        prompt.append("- 평균/대표 온도: ").append(formatNullable(request.getTemperature(), "°C")).append("\n");
        prompt.append("- 습도: ").append(formatNullable(request.getHumidity(), "%")).append("\n");
        prompt.append("- 최대 풍속: ").append(formatNullable(request.getWindSpeed(), "m/s")).append("\n");
        prompt.append("- 강수확률: ").append(formatNullable(request.getPrecipitationProbability(), "%")).append("\n");
        prompt.append("- PM10 미세먼지: ").append(formatNullable(request.getPm10(), "㎍/㎥")).append("\n");
        prompt.append("- PM2.5 초미세먼지: ").append(formatNullable(request.getPm25(), "㎍/㎥")).append("\n\n");

        prompt.append("[작업지시서]\n");
        if (request.getWorkTasks() == null || request.getWorkTasks().isEmpty()) {
            prompt.append("- 등록된 작업지시서 없음\n");
        } else {
            int index = 1;
            for (WeatherAiDto.WorkTaskInfo task : request.getWorkTasks()) {
                prompt.append(index++).append(". ").append(defaultText(task.getTitle(), "작업 지시서")).append("\n");
                prompt.append("   - 위치: ").append(defaultText(task.getWorkLocation(), "작업구역 미지정")).append("\n");
                prompt.append("   - 공종: ").append(defaultText(task.getTradeType(), "공종 미지정")).append("\n");
                prompt.append("   - 상세: ").append(defaultText(task.getWorkDetail(), "상세내역 없음")).append("\n");

                if (task.getEquipments() == null || task.getEquipments().isEmpty()) {
                    prompt.append("   - 장비: 없음\n");
                } else {
                    for (WeatherAiDto.EquipmentInfo equipment : task.getEquipments()) {
                        prompt.append("   - 장비: ")
                                .append(defaultText(equipment.getName(), "장비 미지정"))
                                .append(" ")
                                .append(equipment.getCount() != null ? equipment.getCount() : 1)
                                .append("대\n");
                    }
                }
            }
        }

        prompt.append("\n[분석 기준]\n");
        prompt.append("- 풍속 8m/s 이상: 타워크레인, 크레인, 고소작업차, 리프트, 양중, 외부 고소 작업 주의\n");
        prompt.append("- 풍속 10m/s 이상: 양중/고소 작업 중지 또는 순연 검토\n");
        prompt.append("- 강수확률 60% 이상 또는 우천: 콘크리트 타설, 도장, 방수, 굴착, 덤프/트럭 진입 동선 주의\n");
        prompt.append("- PM10 80 이상: 굴착, 절단, 연마, 덤프, 트럭, 옥외 작업 분진 관리\n");
        prompt.append("- 위험이 없으면 risks와 actions는 빈 배열로 둔다. 억지 경고를 만들지 않는다.\n");

        return prompt.toString();
    }

    private String callOpenAi(String prompt) {
        Map<String, Object> requestBody = Map.of(
                "model", model,
                "input", List.of(
                        Map.of(
                                "role", "user",
                                "content", List.of(
                                        Map.of(
                                                "type", "input_text",
                                                "text", prompt
                                        )
                                )
                        )
                ),
                "text", Map.of(
                        "format", buildJsonSchema()
                )
        );

        return webClient.post()
                .uri("/responses")
                .contentType(MediaType.APPLICATION_JSON)
                .header("Authorization", "Bearer " + apiKey)
                .bodyValue(requestBody)
                .retrieve()
                .bodyToMono(String.class)
                .block();
    }

    private Map<String, Object> buildJsonSchema() {
        return Map.of(
                "type", "json_schema",
                "name", "weather_analysis_result",
                "strict", true,
                "schema", Map.of(
                        "type", "object",
                        "additionalProperties", false,
                        "properties", Map.of(
                                "risks", Map.of(
                                        "type", "array",
                                        "items", Map.of(
                                                "type", "object",
                                                "additionalProperties", false,
                                                "properties", Map.of(
                                                        "target", Map.of("type", "string"),
                                                        "level", Map.of("type", "string", "enum", List.of("양호", "주의", "경고", "위험")),
                                                        "reason", Map.of("type", "string"),
                                                        "recommendation", Map.of("type", "string"),
                                                        "affectedWorks", Map.of(
                                                                "type", "array",
                                                                "items", Map.of("type", "string")
                                                        )
                                                ),
                                                "required", List.of("target", "level", "reason", "recommendation", "affectedWorks")
                                        )
                                ),
                                "actions", Map.of(
                                        "type", "array",
                                        "items", Map.of(
                                                "type", "object",
                                                "additionalProperties", false,
                                                "properties", Map.of(
                                                        "action", Map.of("type", "string"),
                                                        "priority", Map.of("type", "string", "enum", List.of("낮음", "보통", "높음", "긴급")),
                                                        "reason", Map.of("type", "string"),
                                                        "responsibleRole", Map.of("type", "string"),
                                                        "estimatedTime", Map.of("type", "string")
                                                ),
                                                "required", List.of("action", "priority", "reason", "responsibleRole", "estimatedTime")
                                        )
                                ),
                                "overallSafety", Map.of("type", "string", "enum", List.of("SAFE", "CAUTION", "WARNING", "DANGER")),
                                "note", Map.of("type", "string")
                        ),
                        "required", List.of("risks", "actions", "overallSafety", "note")
                )
        );
    }

    private WeatherAiDto.AnalysisResult parseResponse(String response) {
        try {
            String jsonText = extractOutputText(response);
            return objectMapper.readValue(jsonText, WeatherAiDto.AnalysisResult.class);
        } catch (Exception e) {
            throw new RuntimeException("OpenAI 기상 분석 응답 파싱 실패", e);
        }
    }

    private String extractOutputText(String response) {
        try {
            JsonNode root = objectMapper.readTree(response);
            JsonNode output = root.path("output");

            for (JsonNode item : output) {
                JsonNode content = item.path("content");
                for (JsonNode contentItem : content) {
                    if (contentItem.has("text")) {
                        return contentItem.get("text").asText();
                    }
                }
            }

            throw new RuntimeException("OpenAI 응답에서 output text를 찾을 수 없습니다.");
        } catch (Exception e) {
            throw new RuntimeException("OpenAI 응답 파싱 중 오류가 발생했습니다.", e);
        }
    }

    private WeatherAiDto.AnalysisResult createFallbackResult(WeatherAiDto.AnalysisRequest request) {
        List<WeatherAiDto.RiskItem> risks = new ArrayList<>();
        List<WeatherAiDto.ActionItem> actions = new ArrayList<>();

        double wind = request.getWindSpeed() != null ? request.getWindSpeed() : 0.0;
        int rain = request.getPrecipitationProbability() != null ? request.getPrecipitationProbability() : 0;
        int pm10 = request.getPm10() != null ? request.getPm10() : 0;

        if (request.getWorkTasks() != null) {
            for (WeatherAiDto.WorkTaskInfo task : request.getWorkTasks()) {
                String targetText = (defaultText(task.getTitle(), "") + " "
                        + defaultText(task.getWorkDetail(), "") + " "
                        + defaultText(task.getWorkLocation(), "") + " "
                        + equipmentNames(task)).toLowerCase(Locale.KOREA);

                if (wind >= 8 && containsAny(targetText, "크레인", "타워", "양중", "고소", "리프트")) {
                    risks.add(WeatherAiDto.RiskItem.builder()
                            .target(defaultText(task.getWorkLocation(), "작업구역") + " 강풍 작업 통제")
                            .level(wind >= 10 ? "경고" : "주의")
                            .reason("작업상세내역 또는 장비명에 양중/고소 작업이 있고 최대 풍속이 " + formatNumber(wind) + "m/s입니다.")
                            .recommendation("풍속 재측정 후 양중 작업 순연, 신호수 추가 배치, 자재 결속 상태 점검을 검토해 주세요.")
                            .affectedWorks(List.of(defaultText(task.getTitle(), "작업 지시서")))
                            .build());
                }

                if (rain >= 60 && containsAny(targetText, "콘크리트", "타설", "도장", "방수", "굴착", "덤프", "트럭")) {
                    risks.add(WeatherAiDto.RiskItem.builder()
                            .target(defaultText(task.getWorkLocation(), "작업구역") + " 우천 작업 재검토")
                            .level(rain >= 70 ? "경고" : "주의")
                            .reason("강수확률 " + rain + "% 조건에서 품질 저하, 미끄럼, 장비 진입 동선 위험이 커질 수 있습니다.")
                            .recommendation("타설/도장/방수 시간 조정, 노면 배수 확인, 장비 진입 전 통로 통제를 검토해 주세요.")
                            .affectedWorks(List.of(defaultText(task.getTitle(), "작업 지시서")))
                            .build());
                }

                if (pm10 >= 80 && containsAny(targetText, "굴착", "절단", "연마", "덤프", "트럭", "외부")) {
                    risks.add(WeatherAiDto.RiskItem.builder()
                            .target(defaultText(task.getWorkLocation(), "작업구역") + " 분진 노출 관리")
                            .level(pm10 >= 150 ? "경고" : "주의")
                            .reason("PM10 " + pm10 + "㎍/㎥ 조건에서 분진 발생 작업이 예정되어 있습니다.")
                            .recommendation("살수 강화, 방진마스크 지급, 옥외 작업 시간 분산을 검토해 주세요.")
                            .affectedWorks(List.of(defaultText(task.getTitle(), "작업 지시서")))
                            .build());
                }
            }
        }

        if (wind >= 8) {
            actions.add(WeatherAiDto.ActionItem.builder()
                    .action("양중·고소 작업 전 풍속을 재측정해 주세요.")
                    .priority(wind >= 10 ? "긴급" : "높음")
                    .reason("최대 풍속 " + formatNumber(wind) + "m/s 기준으로 자재 흔들림과 추락·낙하 위험이 증가합니다.")
                    .responsibleRole("안전관리자")
                    .estimatedTime("작업 시작 전")
                    .build());
        }

        if (rain >= 40) {
            actions.add(WeatherAiDto.ActionItem.builder()
                    .action("작업면 배수와 미끄럼 상태를 점검해 주세요.")
                    .priority(rain >= 70 ? "긴급" : "높음")
                    .reason("강수확률 " + rain + "% 기준으로 노면 미끄럼과 품질 저하 위험을 사전 점검해야 합니다.")
                    .responsibleRole("현장관리자")
                    .estimatedTime("오전 회의 시")
                    .build());
        }

        if (pm10 >= 80) {
            actions.add(WeatherAiDto.ActionItem.builder()
                    .action("KF94 보호구 지급과 살수 빈도 강화를 진행해 주세요.")
                    .priority(pm10 >= 150 ? "긴급" : "높음")
                    .reason("PM10 " + pm10 + "㎍/㎥ 기준으로 옥외 작업자 호흡기 보호가 필요합니다.")
                    .responsibleRole("안전관리자")
                    .estimatedTime("작업 시작 전")
                    .build());
        }

        String overallSafety = risks.stream().anyMatch(risk -> "경고".equals(risk.getLevel()) || "위험".equals(risk.getLevel()))
                ? "WARNING"
                : risks.isEmpty() ? "SAFE" : "CAUTION";

        return WeatherAiDto.AnalysisResult.builder()
                .risks(risks.stream().limit(5).toList())
                .actions(actions.stream().limit(5).toList())
                .overallSafety(overallSafety)
                .note("OpenAI 호출 실패 시 작업지시서와 기상 기준을 이용한 fallback 분석 결과입니다.")
                .build();
    }

    private String equipmentNames(WeatherAiDto.WorkTaskInfo task) {
        if (task.getEquipments() == null || task.getEquipments().isEmpty()) {
            return "";
        }

        return task.getEquipments().stream()
                .map(WeatherAiDto.EquipmentInfo::getName)
                .filter(name -> name != null && !name.isBlank())
                .reduce("", (left, right) -> left + " " + right);
    }

    private boolean containsAny(String text, String... keywords) {
        if (text == null || keywords == null) {
            return false;
        }

        for (String keyword : keywords) {
            if (text.contains(keyword.toLowerCase(Locale.KOREA))) {
                return true;
            }
        }

        return false;
    }

    private String formatNullable(Number value, String unit) {
        if (value == null) {
            return "정보 없음";
        }

        return formatNumber(value.doubleValue()) + unit;
    }

    private String formatNumber(double value) {
        if (Math.abs(value - Math.round(value)) < 0.0001) {
            return String.valueOf((int) Math.round(value));
        }

        return String.format(Locale.KOREA, "%.1f", value);
    }

    private String defaultText(String value, String defaultValue) {
        return value == null || value.isBlank() ? defaultValue : value;
    }
}