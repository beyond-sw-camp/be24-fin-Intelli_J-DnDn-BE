package org.example.dndncore.ai.extractor;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import org.example.dndncore.project.model.dto.TradeProcessDto;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClient;
import org.example.dndncore.project.model.entity.MasterSchedule;
import org.example.dndncore.project.model.entity.ScheduleAiAnalysis;
import org.example.dndncore.project.repository.MasterScheduleRepository;
import org.example.dndncore.project.repository.ScheduleAiAnalysisRepository;

import java.io.File;
import java.nio.file.Files;
import java.time.LocalDate;
import java.util.Base64;
import java.util.List;
import java.util.Map;

@Component
@RequiredArgsConstructor
public class OpenAiScheduleExtractor {

    private final ObjectMapper objectMapper;
    private final MasterScheduleRepository masterScheduleRepository;
    private final ScheduleAiAnalysisRepository scheduleAiAnalysisRepository;

    @Value("${openai.api-key}")
    private String apiKey;

    @Value("${openai.model:gpt-5.5}")
    private String model;

    private final WebClient webClient = WebClient.builder()
            .baseUrl("https://api.openai.com/v1")
            .build();

    public List<TradeProcessDto.Req> extractSchedule(File file, Long masterScheduleId) {
        MasterSchedule masterSchedule = masterScheduleRepository.findById(masterScheduleId)
                .orElseThrow(() -> new RuntimeException("공정표 파일 정보를 찾을 수 없습니다."));

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
                                                    "text", buildPrompt()
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

            AiScheduleExtractionResponse parsed = objectMapper.readValue(
                    jsonText,
                    AiScheduleExtractionResponse.class
            );

            ScheduleAiAnalysis analysis = ScheduleAiAnalysis.builder()
                    .masterSchedule(masterSchedule)
                    .rawJson(jsonText)
                    .status("SUCCESS")
                    .errorMessage(null)
                    .build();

            scheduleAiAnalysisRepository.save(analysis);

            List<AiTradeProcessResult> aiResults =
                    parsed.schedules() != null ? parsed.schedules() : List.of();

            return aiResults.stream()
                    .map(item -> TradeProcessDto.Req.builder()
                            .masterScheduleId(masterScheduleId)
                            .tradeName(item.tradeName())
                            .processName(item.processName())
                            .weightPct(item.weightPct())
                            .plannedStart(parseDate(item.plannedStart()))
                            .plannedEnd(parseDate(item.plannedEnd()))
                            .isMilestone(item.isMilestone() != null ? item.isMilestone() : false)
                            .build())
                    .toList();

        } catch (Exception e) {
            ScheduleAiAnalysis failedAnalysis = ScheduleAiAnalysis.builder()
                    .masterSchedule(masterSchedule)
                    .rawJson("{}")
                    .status("FAILED")
                    .errorMessage(e.getMessage())
                    .build();

            scheduleAiAnalysisRepository.save(failedAnalysis);

            throw new RuntimeException("OpenAI 공정표 분석 중 오류가 발생했습니다.", e);
        }
    }

    private String buildPrompt() {
        return """
            너는 건설 공정표 문서를 분석해서 DnDn 간트차트용 JSON을 만드는 AI다.

            업로드된 문서는 마스터 공정표, 마일스톤 공정표, 보할 공정표, 공종별 시공계획서 중 하나일 수 있다.
            문서에서 공정별 기준 일정 데이터를 추출해라.

            추출 필드:
            - tradeName: 공종명 또는 상위 공사 구분
            - processName: 세부 공정명 또는 작업 내용
            - weightPct: 보할율 또는 전체 대비 비중(%)
            - plannedStart: 계획 시작일
            - plannedEnd: 계획 종료일
            - isMilestone: 마일스톤 여부

            추출 규칙:
            1. 반드시 schedules 배열을 가진 JSON 객체만 반환한다.
            2. 설명 문장은 절대 넣지 않는다.
            3. 날짜는 반드시 yyyy-MM-dd 형식으로 반환한다.
            4. 공종명과 공정명이 모두 없는 행은 제외한다.
            5. 합계, 소계, 누계, 총계, 검증 행은 제외한다.
            6. 공사금액, 보할, 비고, 합계, 검증 컬럼은 일정 행으로 만들지 않는다.
            7. tradeName은 표의 '구분' 컬럼 또는 상위 공종명을 사용한다.
            8. processName은 표의 '내용' 컬럼 또는 세부 공정명을 사용한다.
            9. weightPct는 '보할' 컬럼의 값을 사용한다. 단, 반드시 0~100 범위의 백분율 숫자로 반환한다.
                - 셀 표시값이 '0.73%' 이면 0.73으로 반환 (0.0073이 아님).
                - 셀 표시값이 '2.00%' 이면 2.0으로 반환.
                - 만약 원본 값이 0~1 범위(예: 0.0073)이면 100을 곱해서 0.73으로 변환한다.
                - 보할 합계는 100이 되어야 한다.
            10. 보할값이 0이거나 비어 있는 주차/월은 해당 공정의 수행 기간으로 보지 않는다.
            11. 보할 공정표처럼 월/주차별 값이 있는 경우, 값이 처음 나타나는 주차 또는 월을 plannedStart로 판단한다.
            12. 값이 마지막으로 나타나는 주차 또는 월을 plannedEnd로 판단한다.
            13. 주차 단위 날짜가 명확하지 않으면 해당 월의 1주차는 1일, 2주차는 8일, 3주차는 15일, 4주차는 22일, 5주차는 말일로 추정한다.
            14. 월 단위만 있으면 시작일은 해당 월의 1일, 종료일은 해당 월의 마지막 날로 한다.
            15. 마일스톤, 준공, 완료, 착공, 골조완료, 사용승인, 중간검사 같은 주요 이벤트는 isMilestone=true로 판단한다.
            16. 일반 공정은 isMilestone=false로 판단한다.
            17. 값이 확실하지 않은 필드는 null로 둔다.
            18. 동일한 공종/공정명이 여러 줄에 나뉘어 있으면 하나의 공정으로 병합하되, 시작일은 가장 빠른 날짜, 종료일은 가장 늦은 날짜로 한다.
            19. DnDn 간트차트의 기준선 생성을 위한 데이터만 추출한다.
                 마일스톤 생성 규칙:
                            20. 공사 전체 기간에서 가장 빠른 plannedStart를 공사 착공일로 판단하고,
                                tradeName은 "마일스톤", processName은 "착공", isMilestone=true로 추가한다.
                
                            21. 공사 전체 기간에서 가장 늦은 plannedEnd를 공사 준공일로 판단하고,
                                tradeName은 "마일스톤", processName은 "준공", isMilestone=true로 추가한다.
                
                            22. 모든 일반 공정의 종료일을 마일스톤으로 만들지 않는다.
                                세부 공정 종료 마일스톤은 생성하지 않는다.               
                
                            23. 공종 완료 마일스톤은 해당 tradeName에 속한 일반 공정들 중 가장 늦은 plannedEnd를 사용한다.
                                공종 완료 마일스톤의 tradeName은 해당 공종명으로 유지한다.
                                예: tradeName="토공사", processName="토공사 완료", isMilestone=true
                                plannedStart와 plannedEnd는 같은 날짜로 한다.
                                weightPct는 null로 한다.
                                isMilestone=true로 한다.
                               
                            24. 사용승인, 사용승인 신청, 준공검사, 중간검사처럼 문서에 명시된 주요 이벤트는 마일스톤에서 제외한다.
                
                            25. 동일한 날짜와 동일한 processName의 마일스톤이 중복되면 하나만 반환한다.
            """;
    }

    private Map<String, Object> buildJsonSchema() {
        return Map.of(
                "type", "json_schema",
                "name", "schedule_extraction_result",
                "strict", true,
                "schema", Map.of(
                        "type", "object",
                        "additionalProperties", false,
                        "properties", Map.of(
                                "schedules", Map.of(
                                        "type", "array",
                                        "items", Map.of(
                                                "type", "object",
                                                "additionalProperties", false,
                                                "properties", Map.of(
                                                        "tradeName", Map.of(
                                                                "type", List.of("string", "null")
                                                        ),
                                                        "processName", Map.of(
                                                                "type", List.of("string", "null")
                                                        ),
                                                        "weightPct", Map.of(
                                                                "type", List.of("number", "null")
                                                        ),
                                                        "plannedStart", Map.of(
                                                                "type", List.of("string", "null")
                                                        ),
                                                        "plannedEnd", Map.of(
                                                                "type", List.of("string", "null")
                                                        ),
                                                        "isMilestone", Map.of(
                                                                "type", List.of("boolean", "null")
                                                        )
                                                ),
                                                "required", List.of(
                                                        "tradeName",
                                                        "processName",
                                                        "weightPct",
                                                        "plannedStart",
                                                        "plannedEnd",
                                                        "isMilestone"
                                                )
                                        )
                                )
                        ),
                        "required", List.of("schedules")
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

    private record AiScheduleExtractionResponse(
            List<AiTradeProcessResult> schedules
    ) {
    }

    private record AiTradeProcessResult(
            String tradeName,
            String processName,
            Float weightPct,
            String plannedStart,
            String plannedEnd,
            Boolean isMilestone
    ) {
    }
}