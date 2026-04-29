package org.example.dndn.weather.model;

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
import org.example.dndn.common.model.BaseEntity;

import java.time.LocalDate;


@Entity
@Table(name = "weather_info")
@Getter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class WeatherInfo extends BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long idx;

    @Column(name = "report_date", nullable = false, unique = true)
    private LocalDate reportDate;

    @Column(name = "location_label", length = 100)
    private String locationLabel;

    @Column(name = "today_headline_temp", length = 50)
    private String todayHeadlineTemp;

    @Column(name = "today_summary", length = 200)
    private String todaySummary;

    @Column(name = "am_label", length = 100)
    private String amLabel;

    @Column(name = "pm_label", length = 100)
    private String pmLabel;

    @Lob
    @Column(name = "weekly_summary", columnDefinition = "TEXT")
    private String weeklySummary;

    @Column(name = "precipitation_probability", length = 50)
    private String precipitationProbability;

    @Column(name = "fine_dust_value", length = 50)
    private String fineDustValue;

    @Column(name = "fine_dust_grade", length = 50)
    private String fineDustGrade;

    @Lob
    @Column(name = "dashboard_json", columnDefinition = "LONGTEXT")
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
