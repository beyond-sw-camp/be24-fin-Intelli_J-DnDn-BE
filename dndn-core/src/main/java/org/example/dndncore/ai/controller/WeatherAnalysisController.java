package org.example.dndncore.ai.controller;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.example.dndncore.ai.dto.WeatherAiDto;
import org.example.dndncore.ai.extractor.WeatherAnalysisExtractor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;

@Slf4j
@RestController
@RequestMapping("/ai/weather")
@RequiredArgsConstructor
@Tag(name = "AI Weather Analysis", description = "AI 기상 분석 API")
public class WeatherAnalysisController {

    private final WeatherAnalysisExtractor weatherAnalysisExtractor;

    @PostMapping("/analyze")
    @Operation(summary = "기상 분석 실행", description = "지정한 날짜 기준으로 AI 기상 분석을 수행합니다. 날짜를 생략하면 내부 기준일을 사용합니다.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "분석 성공",
                    content = @Content(schema = @Schema(implementation = WeatherAiDto.AnalysisResult.class))),
            @ApiResponse(responseCode = "500", description = "분석 실패")
    })
    public ResponseEntity<WeatherAiDto.AnalysisResult> analyze(
            @Parameter(description = "분석 날짜(YYYY-MM-DD)", example = "2026-05-01")
            @RequestParam(required = false)
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE)
            LocalDate date,
            @Parameter(description = "공사현장 ID", example = "1")
            @RequestParam(required = false)
            Long projectId) {

        log.info("[분석 API] 요청 - 날짜: {}, 현장: {}", date, projectId);

        try {
            WeatherAiDto.AnalysisResult result = weatherAnalysisExtractor.analyze(date, projectId);
            return ResponseEntity.ok(result);
        } catch (Exception e) {
            log.error("[분석 API] 실패", e);
            return ResponseEntity.internalServerError().build();
        }
    }
}