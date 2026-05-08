package org.example.dndn.ai.entity;

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
import org.example.dndn.common.model.BaseEntity;

import java.time.LocalDate;

@Entity
@Table(name = "weather_ai_analysis")
@Getter
@Builder(access = AccessLevel.PRIVATE)
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor(access = AccessLevel.PRIVATE)
public class WeatherAiAnalysis extends BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long idx;

    @Column(name = "analysis_date", nullable = false, unique = true)
    private LocalDate analysisDate;

    @Column(name = "overall_safety", length = 30)
    private String overallSafety;

    @Lob
    @Column(name = "note", columnDefinition = "TEXT")
    private String note;

    @Lob
    @Column(name = "result_json", nullable = false, columnDefinition = "LONGTEXT")
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