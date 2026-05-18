package org.example.dndncore.ai.extractor;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import org.example.dndncore.ai.dto.WorkPlanAiDto;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClient;

import java.io.File;
import java.nio.file.Files;
import java.time.LocalDate;
import java.util.Base64;
import java.util.List;
import java.util.Map;

@Component
@RequiredArgsConstructor
public class OpenAiWorkPlanExtractor {

    private final ObjectMapper objectMapper;

    @Value("${openai.api-key}")
    private String apiKey;

    @Value("${openai.model:gpt-5.5}")
    private String model;

    private final WebClient webClient = WebClient.builder()
            .baseUrl("https://api.openai.com/v1")
            .build();

    public List<WorkPlanAiDto.Item> extractWorkPlan(
            File file,
            String planType,
            Integer year,
            Integer month,
            String selectedTradeName
    ) {
        try {
            String base64File = Base64.getEncoder().encodeToString(Files.readAllBytes(file.toPath()));
            String mimeType = detectMimeType(file);

            Map<String, Object> requestBody = Map.of(
                    "model", model,
                    "input", List.of(
                            Map.of(
                                    "role", "user",
                                    "content", List.of(
                                            Map.of(
                                                    "type", "input_text",
                                                    "text", buildPrompt(planType, year, month, selectedTradeName)
                                            ),
                                            Map.of(
                                                    "type", "input_file",
                                                    "filename", file.getName(),
                                                    "file_data", "data:" + mimeType + ";base64," + base64File
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

            AiWorkPlanExtractionResponse parsed = objectMapper.readValue(
                    jsonText,
                    AiWorkPlanExtractionResponse.class
            );

            List<AiWorkPlanItem> items = parsed.items() != null ? parsed.items() : List.of();

            return items.stream()
                    .map(item -> WorkPlanAiDto.Item.builder()
                            .tradeName(item.tradeName())
                            .tradeProcessName(item.tradeProcessName())
                            .name(item.name())
                            .location(item.location())
                            .startDate(parseDate(item.startDate()))
                            .endDate(parseDate(item.endDate()))
                            .note(item.note())
                            .build())
                    .toList();

        } catch (Exception e) {
            throw new RuntimeException("OpenAI 작업 계획서 분석 중 오류가 발생했습니다.", e);
        }
    }

    private String buildPrompt(String planType, Integer year, Integer month, String selectedTradeName) {
        return """
                너는 건설 현장의 연간/월간 공정 계획서를 분석해서 DnDn WorkPlan 저장용 JSON을 만드는 AI다.

                사용자가 선택한 계획 정보:
                - 계획 유형: %s
                - 기준 연도: %s
                - 기준 월: %s
                - 선택 공종: %s

                추출 필드:
                - tradeName: 공종명
                - tradeProcessName: 전체 기준 공정표의 기준 공정명
                - name: 세부 작업명
                - location: 작업 위치
                - startDate: 작업 시작일
                - endDate: 작업 종료일
                - note: 비고

                추출 규칙:
                1. 반드시 items 배열을 가진 JSON 객체만 반환한다.
                2. 설명 문장은 절대 넣지 않는다.
                3. 날짜는 반드시 yyyy-MM-dd 형식으로 반환한다.
                4. 시작일과 종료일이 없는 행은 저장 대상에서 제외한다.
                5. 합계, 소계, 누계, 총계, 공정보활, 전월실시, 금월계획, 누계계획 행은 제외한다.
                6. 선택 공종과 관련 없는 작업은 제외한다.
                7. tradeName은 사용자가 선택한 공종명을 우선 사용한다.
                8. tradeProcessName은 전체 기준 공정표의 기준 공정명으로 추정한다.
                   예: "터파기 1차 굴착"은 tradeProcessName을 "터파기 및 토사반출"로 판단한다.
                9. name은 실제 월간/연간 계획의 세부 작업명으로 작성한다.
                10. location이 문서에 있으면 추출하고, 없으면 null로 둔다.
                11. note가 문서에 있으면 추출하고, 없으면 null로 둔다.
                12. 월간 계획서의 경우 기준 월 범위를 벗어난 작업은 제외한다.
                13. 연간 계획서의 경우 기준 연도 범위를 벗어난 작업은 제외한다.
                14. 마일스톤, 착공, 준공, 공종 완료 같은 이벤트성 행은 WorkPlan으로 추출하지 않는다.
                15. DnDn 간트차트의 빨간선 생성을 위한 실행 계획 데이터만 추출한다.
                """.formatted(
                planType,
                year,
                month != null ? month : "-",
                selectedTradeName
        );
    }

    private Map<String, Object> buildJsonSchema() {
        return Map.of(
                "type", "json_schema",
                "name", "work_plan_extraction_result",
                "strict", true,
                "schema", Map.of(
                        "type", "object",
                        "additionalProperties", false,
                        "properties", Map.of(
                                "items", Map.of(
                                        "type", "array",
                                        "items", Map.of(
                                                "type", "object",
                                                "additionalProperties", false,
                                                "properties", Map.of(
                                                        "tradeName", Map.of(
                                                                "type", List.of("string", "null")
                                                        ),
                                                        "tradeProcessName", Map.of(
                                                                "type", List.of("string", "null")
                                                        ),
                                                        "name", Map.of(
                                                                "type", List.of("string", "null")
                                                        ),
                                                        "location", Map.of(
                                                                "type", List.of("string", "null")
                                                        ),
                                                        "startDate", Map.of(
                                                                "type", List.of("string", "null")
                                                        ),
                                                        "endDate", Map.of(
                                                                "type", List.of("string", "null")
                                                        ),
                                                        "note", Map.of(
                                                                "type", List.of("string", "null")
                                                        )
                                                ),
                                                "required", List.of(
                                                        "tradeName",
                                                        "tradeProcessName",
                                                        "name",
                                                        "location",
                                                        "startDate",
                                                        "endDate",
                                                        "note"
                                                )
                                        )
                                )
                        ),
                        "required", List.of("items")
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

            throw new RuntimeException("OpenAI 응답에서 output text를 찾을 수 없습니다.");
        } catch (Exception e) {
            throw new RuntimeException("OpenAI 응답 파싱 중 오류가 발생했습니다.", e);
        }
    }

    private LocalDate parseDate(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }

        return LocalDate.parse(value);
    }

    private String detectMimeType(File file) {
        String fileName = file.getName().toLowerCase();

        if (fileName.endsWith(".pdf")) {
            return "application/pdf";
        }

        if (fileName.endsWith(".png")) {
            return "image/png";
        }

        if (fileName.endsWith(".jpg") || fileName.endsWith(".jpeg")) {
            return "image/jpeg";
        }

        if (fileName.endsWith(".xlsx")) {
            return "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet";
        }

        if (fileName.endsWith(".xls")) {
            return "application/vnd.ms-excel";
        }

        throw new RuntimeException("지원하지 않는 파일 형식입니다.");
    }

    private record AiWorkPlanExtractionResponse(
            List<AiWorkPlanItem> items
    ) {
    }

    private record AiWorkPlanItem(
            String tradeName,
            String tradeProcessName,
            String name,
            String location,
            String startDate,
            String endDate,
            String note
    ) {
    }
}