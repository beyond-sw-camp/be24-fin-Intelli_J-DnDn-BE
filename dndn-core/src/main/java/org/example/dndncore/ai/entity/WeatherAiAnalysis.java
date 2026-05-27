package org.example.dndncore.ai.entity;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Lob;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.example.dndncore.common.model.BaseEntity;

import java.time.LocalDate;

@Entity
@Table(name = "weather_ai_analysis")
@Getter
@Builder(access = AccessLevel.PRIVATE)
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor(access = AccessLevel.PRIVATE)
@Schema(description = "기상 AI 분석 엔티티")
public class WeatherAiAnalysis extends BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Schema(description = "기상 분석 ID", example = "1")
    private Long idx;

    @Column(name = "analysis_date", nullable = false, unique = true)
    @Schema(description = "분석 기준일", example = "2026-05-27")
    private LocalDate analysisDate;

    @Column(name = "overall_safety", length = 30)
    @Schema(description = "종합 안전도", example = "주의")
    private String overallSafety;

    @Lob
    @Column(name = "note", columnDefinition = "TEXT")
    @Schema(description = "분석 비고")
    private String note;

    @Lob
    @Column(name = "result_json", nullable = false, columnDefinition = "LONGTEXT")
    @Schema(description = "AI 분석 결과 JSON")
    private String resultJson;

    public static WeatherAiAnalysis create(
            LocalDate analysisDate,
            String overallSafety,
            String note,
            String resultJson
    ) {
        return WeatherAiAnalysis.builder()
                .analysisDate(analysisDate)
                .overallSafety(overallSafety)
                .note(note)
                .resultJson(resultJson)
                .build();
    }

    public void updateResult(String overallSafety, String note, String resultJson) {
        this.overallSafety = overallSafety;
        this.note = note;
        this.resultJson = resultJson;
    }
}