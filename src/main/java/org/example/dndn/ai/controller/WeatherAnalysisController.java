package org.example.dndn.ai.controller;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.example.dndn.ai.dto.WeatherAiDto;
import org.example.dndn.ai.extractor.WeatherAnalysisExtractor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;

/**
 * 기상 분석 Controller
 * 역할:
 * - API 엔드포인트 제공
 * - WeatherAnalysisExtractor 호출
 * - 결과 반환
 */
@Slf4j
@RestController
@RequestMapping("/ai/weather")
@RequiredArgsConstructor
public class WeatherAnalysisController {

    private final WeatherAnalysisExtractor weatherAnalysisExtractor;

    /**
     * 기상관제 분석 API
     * @param date 분석 날짜 (YYYY-MM-DD, 선택)
     * @return AI 분석 결과
     */
    @PostMapping("/analyze")
    public ResponseEntity<WeatherAiDto.AnalysisResult> analyze(
            @RequestParam(required = false)
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE)
            LocalDate date) {

        log.info("[분석 API] 요청 - 날짜: {}", date);

        try {
            WeatherAiDto.AnalysisResult result = weatherAnalysisExtractor.analyze(date);
            return ResponseEntity.ok(result);
        } catch (Exception e) {
            log.error("[분석 API] 실패", e);
            return ResponseEntity.internalServerError().build();
        }
    }
}
