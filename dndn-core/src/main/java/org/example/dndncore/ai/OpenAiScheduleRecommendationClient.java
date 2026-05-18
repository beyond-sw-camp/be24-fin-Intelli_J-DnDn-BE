package org.example.dndncore.ai;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClient;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Component
@RequiredArgsConstructor
public class OpenAiScheduleRecommendationClient {

    private final ObjectMapper objectMapper;

    @Value("${openai.api-key}")
    private String apiKey;

    @Value("${openai.model:gpt-5.5}")
    private String model;

    private final WebClient webClient = WebClient.builder()
            .baseUrl("https://api.openai.com/v1")
            .build();

    public Map<String, Object> generate(Map<String, Object> context) {
        try {
            Map<String, Object> requestBody = Map.of(
                    "model", model,
                    "input", List.of(
                            Map.of(
                                    "role", "system",
                                    "content", List.of(
                                            Map.of(
                                                    "type", "input_text",
                                                    "text", buildSystemPrompt()
                                            )
                                    )
                            ),
                            Map.of(
                                    "role", "user",
                                    "content", List.of(
                                            Map.of(
                                                    "type", "input_text",
                                                    "text", objectMapper.writeValueAsString(context)
                                            )
                                    )
                            )
                    ),
                    "text", Map.of(
                            "format", buildJsonSchema()
                    )
            );

            String response = webClient.post()
                    .uri("/responses")
                    .contentType(MediaType.APPLICATION_JSON)
                    .header("Authorization", "Bearer " + apiKey)
                    .bodyValue(requestBody)
                    .retrieve()
                    .bodyToMono(String.class)
                    .block();

            String jsonText = extractOutputText(response);
            return objectMapper.readValue(jsonText, new TypeReference<LinkedHashMap<String, Object>>() {});
        } catch (Exception e) {
            throw new RuntimeException("OpenAI schedule recommendation failed.", e);
        }
    }

    private String buildSystemPrompt() {
        return """
                You are a construction schedule recovery assistant for DnDn.
                Use the supplied project, monthly work plan, delay risk, and child work plans.

                Rules:
                1. Return JSON only in the requested schema.
                2. Recommend changes only for workPlanId values included in recoveryCandidates.
                3. If recoveryCandidates is empty, return "detailChanges": [].
                4. Do not invent workPlanId values.
                5. Do not recommend changing completed, unrelated, zero-worker, zero-man-hour, or date-missing work.
                6. Prefer keeping the monthly plan end date unless the delay cannot be recovered safely.
                7. Work time must use "HH:mm ~ HH:mm".
                8. recommendedRequiredCount must be realistic: keep current count or increase by at most 30% / 2 workers.
                9. Return at most 2 detailChanges. Choose the nearest actionable recovery candidates first.
                10. Do not rename work into vague phrases such as 집중 시공, 조기 완료, 확인 강화, 투입 unless the original work name is preserved.
                11. recommendedName should usually be the original name. If changed, append a short concrete suffix only.
                12. recommendedNote must be concrete field guidance based on originalNote and must not copy previous "[승인 변경 반영]" audit text.
                13. Write Korean B2B operations text. Be concise, factual, and non-promotional.
                """;
    }

    private Map<String, Object> buildJsonSchema() {
        return Map.of(
                "type", "json_schema",
                "name", "schedule_recommendation_result",
                "strict", true,
                "schema", Map.of(
                        "type", "object",
                        "additionalProperties", false,
                        "properties", Map.of(
                                "changeSummary", Map.of(
                                        "type", "object",
                                        "additionalProperties", false,
                                        "properties", Map.of(
                                                "summary", Map.of("type", "string"),
                                                "expectedEffect", Map.of("type", "string"),
                                                "basis", Map.of("type", "string"),
                                                "addDays", Map.of("type", "integer"),
                                                "recommendedWorkers", Map.of("type", "integer")
                                        ),
                                        "required", List.of(
                                                "summary",
                                                "expectedEffect",
                                                "basis",
                                                "addDays",
                                                "recommendedWorkers"
                                        )
                                ),
                                "detailChanges", Map.of(
                                        "type", "array",
                                        "items", Map.of(
                                                "type", "object",
                                                "additionalProperties", false,
                                                "properties", Map.of(
                                                        "workPlanId", Map.of("type", "integer"),
                                                        "recommendedName", Map.of("type", "string"),
                                                        "recommendedRequiredCount", Map.of("type", "integer"),
                                                        "recommendedWorkTime", Map.of("type", "string"),
                                                        "recommendedNote", Map.of("type", "string"),
                                                        "manHourAdjustmentReason", Map.of("type", "string")
                                                ),
                                                "required", List.of(
                                                        "workPlanId",
                                                        "recommendedName",
                                                        "recommendedRequiredCount",
                                                        "recommendedWorkTime",
                                                        "recommendedNote",
                                                        "manHourAdjustmentReason"
                                                )
                                        )
                                )
                        ),
                        "required", List.of("changeSummary", "detailChanges")
                )
        );
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

            throw new RuntimeException("OpenAI response output text not found.");
        } catch (Exception e) {
            throw new RuntimeException("Failed to parse OpenAI response.", e);
        }
    }
}
