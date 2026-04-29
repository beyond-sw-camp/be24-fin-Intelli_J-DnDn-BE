package org.example.dndn.weather.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.util.ArrayList;
import java.util.List;


public class WeatherInfoDto {

    @Getter
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class DashboardRes {
        private String reportDate;
        private String locationLabel;
        private TodayCard today;
        private WeekCard week;
        private RainCard rain;
        private AirQualityCard airQuality;
        private WeatherAnalysis analysis;
        private List<RiskItem> equipmentRisks;
        private List<RiskItem> planRisks;
        private List<AlertItem> alerts;
        private List<ForecastDay> forecastDays;

        public static DashboardRes empty(String locationLabel, String reportDate) {
            return DashboardRes.builder()
                    .reportDate(reportDate)
                    .locationLabel(locationLabel)
                    .today(TodayCard.empty())
                    .week(WeekCard.empty())
                    .rain(RainCard.empty())
                    .airQuality(AirQualityCard.empty())
                    .analysis(WeatherAnalysis.empty(reportDate))
                    .equipmentRisks(new ArrayList<>())
                    .planRisks(new ArrayList<>())
                    .alerts(new ArrayList<>())
                    .forecastDays(new ArrayList<>())
                    .build();
        }

        public TodaySimpleRes toTodaySimpleRes() {
            return TodaySimpleRes.builder()
                    .amLabel(today != null ? today.getAmLabel() : "기상정보 없음")
                    .pmLabel(today != null ? today.getPmLabel() : "기상정보 없음")
                    .summary(today != null ? today.getSummary() : "기상 정보 없음")
                    .build();
        }
    }

    @Getter
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class TodayCard {
        private String headlineTemp;
        private String summary;
        private String amLabel;
        private String pmLabel;
        private String observedAt;

        public static TodayCard empty() {
            return TodayCard.builder()
                    .headlineTemp("--°C / --°C")
                    .summary("기상 정보 없음")
                    .amLabel("기상정보 없음")
                    .pmLabel("기상정보 없음")
                    .observedAt("-")
                    .build();
        }
    }

    @Getter
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class WeekCard {
        private String summary;
        private String subSummary;

        public static WeekCard empty() {
            return WeekCard.builder()
                    .summary("3일 내 특이 기상 정보 없음")
                    .subSummary("-")
                    .build();
        }
    }

    @Getter
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class RainCard {
        private String label;
        private String value;

        public static RainCard empty() {
            return RainCard.builder()
                    .label("강수확률")
                    .value("0%")
                    .build();
        }
    }


    @Getter
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class AirQualityCard {
        private boolean available;
        private Integer value;
        private Integer pm10;
        private String grade;
        private String label;

        public static AirQualityCard empty() {
            return AirQualityCard.builder()
                    .available(false)
                    .value(null)
                    .pm10(null)
                    .grade("")
                    .label("대기질 API 미연동")
                    .build();
        }
    }

    @Getter
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class WeatherAnalysis {
        private String reportDate;
        private String sourceType;
        private boolean outOfRange;
        private Integer minTemperature;
        private Integer maxTemperature;
        private Integer avgAmTemperature;
        private Integer avgPmTemperature;
        private Integer precipitationProbability;
        private Double maxWindSpeed;
        private Integer fineDustValue;
        private boolean fineDustRisk;
        private boolean hasRain;
        private boolean hasSnow;
        private boolean heatRisk;
        private boolean coldRisk;
        private boolean windRisk;

        public static WeatherAnalysis empty(String reportDate) {
            return WeatherAnalysis.builder()
                    .reportDate(reportDate)
                    .sourceType("EMPTY")
                    .outOfRange(false)
                    .minTemperature(null)
                    .maxTemperature(null)
                    .avgAmTemperature(null)
                    .avgPmTemperature(null)
                    .precipitationProbability(0)
                    .maxWindSpeed(0.0)
                    .fineDustValue(null)
                    .fineDustRisk(false)
                    .hasRain(false)
                    .hasSnow(false)
                    .heatRisk(false)
                    .coldRisk(false)
                    .windRisk(false)
                    .build();
        }
    }

    @Getter
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class RiskItem {
        private String badge;
        private String title;
        private String subtitle;
        private String level;
        private String reason;
        private String action;
    }

    @Getter
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class AlertItem {
        private String title;
        private String level;
        private String message;
    }

    @Getter
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class ForecastDay {
        private String date;
        private String dayLabel;
        private String weatherLabel;
        private Integer minTemp;
        private Integer maxTemp;
        private Integer precipitationProbability;
        private Double windSpeed;
    }

    @Getter
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class TodaySimpleRes {
        private String amLabel;
        private String pmLabel;
        private String summary;
    }
}
