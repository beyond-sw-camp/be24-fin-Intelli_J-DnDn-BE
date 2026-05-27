package org.example.dndncore.weather.model;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Lob;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.example.dndncore.common.model.BaseEntity;

import java.time.LocalDate;


@Entity
@Table(name = "weather_info")
@Getter
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "날씨 스냅샷 엔티티")
public class WeatherInfo extends BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Schema(description = "날씨 정보 ID", example = "1")
    private Long idx;

    @Column(name = "report_date", nullable = false, unique = true)
    @Schema(description = "기준 일자", example = "2026-05-27")
    private LocalDate reportDate;

    @Column(name = "location_label", length = 100)
    @Schema(description = "위치 라벨", example = "서울특별시")
    private String locationLabel;

    @Column(name = "today_headline_temp", length = 50)
    @Schema(description = "오늘 헤드라인 온도", example = "23°C / 16°C")
    private String todayHeadlineTemp;

    @Column(name = "today_summary", length = 200)
    @Schema(description = "오늘 요약", example = "대체로 맑음")
    private String todaySummary;

    @Column(name = "am_label", length = 100)
    @Schema(description = "오전 라벨", example = "맑음")
    private String amLabel;

    @Column(name = "pm_label", length = 100)
    @Schema(description = "오후 라벨", example = "구름많음")
    private String pmLabel;

    @Lob
    @Column(name = "weekly_summary", columnDefinition = "TEXT")
    @Schema(description = "주간 요약", example = "3일 내 특이 기상 정보 없음")
    private String weeklySummary;

    @Column(name = "precipitation_probability", length = 50)
    @Schema(description = "강수확률", example = "30%")
    private String precipitationProbability;

    @Column(name = "fine_dust_value", length = 50)
    @Schema(description = "미세먼지 수치", example = "41")
    private String fineDustValue;

    @Column(name = "fine_dust_grade", length = 50)
    @Schema(description = "미세먼지 등급", example = "보통")
    private String fineDustGrade;

    @Lob
    @Column(name = "dashboard_json", columnDefinition = "LONGTEXT")
    @Schema(description = "대시보드 원본 JSON")
    private String dashboardJson;

    public void updateSnapshot(
            String locationLabel,
            String todayHeadlineTemp,
            String todaySummary,
            String amLabel,
            String pmLabel,
            String weeklySummary,
            String precipitationProbability,
            String fineDustValue,
            String fineDustGrade,
            String dashboardJson
    ) {
        this.locationLabel = locationLabel;
        this.todayHeadlineTemp = todayHeadlineTemp;
        this.todaySummary = todaySummary;
        this.amLabel = amLabel;
        this.pmLabel = pmLabel;
        this.weeklySummary = weeklySummary;
        this.precipitationProbability = precipitationProbability;
        this.fineDustValue = fineDustValue;
        this.fineDustGrade = fineDustGrade;
        this.dashboardJson = dashboardJson;
    }
}
