package org.example.dndncore.weather.model;

import io.swagger.v3.oas.annotations.media.Schema;
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
    @Schema(description = "날씨 대시보드 응답 DTO")
    public static class DashboardRes {
        @Schema(description = "기준 일자", example = "2026-05-27")
        private String reportDate;
        @Schema(description = "위치 라벨", example = "서울특별시")
        private String locationLabel;
        @Schema(description = "오늘 날씨 카드")
        private TodayCard today;
        @Schema(description = "주간 요약 카드")
        private WeekCard week;
        @Schema(description = "강수 정보 카드")
        private RainCard rain;
        @Schema(description = "대기질 카드")
        private AirQualityCard airQuality;
        @Schema(description = "상세 기상 분석")
        private WeatherAnalysis analysis;
        @Schema(description = "장비 리스크 목록")
        private List<RiskItem> equipmentRisks;
        @Schema(description = "공정 리스크 목록")
        private List<RiskItem> planRisks;
        @Schema(description = "알림 목록")
        private List<AlertItem> alerts;
        @Schema(description = "예보 일별 목록")
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
    @Schema(description = "오늘 날씨 카드 DTO")
    public static class TodayCard {
        @Schema(description = "헤드라인 온도", example = "23°C / 16°C")
        private String headlineTemp;
        @Schema(description = "요약 문구", example = "대체로 맑음")
        private String summary;
        @Schema(description = "오전 상태", example = "맑음")
        private String amLabel;
        @Schema(description = "오후 상태", example = "구름많음")
        private String pmLabel;
        @Schema(description = "관측 시각", example = "09:00")
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
    @Schema(description = "주간 날씨 카드 DTO")
    public static class WeekCard {
        @Schema(description = "주간 요약", example = "3일 내 특이 기상 없음")
        private String summary;
        @Schema(description = "주간 보조 요약", example = "평년과 유사")
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
    @Schema(description = "강수 정보 카드 DTO")
    public static class RainCard {
        @Schema(description = "강수 항목 라벨", example = "강수확률")
        private String label;
        @Schema(description = "강수 값", example = "30%")
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
    @Schema(description = "대기질 카드 DTO")
    public static class AirQualityCard {
        @Schema(description = "대기질 정보 제공 여부", example = "true")
        private boolean available;
        @Schema(description = "대기질 지수 값", example = "52", nullable = true)
        private Integer value;
        @Schema(description = "미세먼지(PM10) 값", example = "41", nullable = true)
        private Integer pm10;
        @Schema(description = "대기질 등급", example = "보통")
        private String grade;
        @Schema(description = "대기질 라벨", example = "보통")
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
    @Schema(description = "기상 분석 DTO")
    public static class WeatherAnalysis {
        @Schema(description = "기준 일자", example = "2026-05-27")
        private String reportDate;
        @Schema(description = "데이터 소스 유형", example = "API")
        private String sourceType;
        @Schema(description = "관측 범위 이탈 여부", example = "false")
        private boolean outOfRange;
        @Schema(description = "최저 기온", example = "16", nullable = true)
        private Integer minTemperature;
        @Schema(description = "최고 기온", example = "23", nullable = true)
        private Integer maxTemperature;
        @Schema(description = "오전 평균 기온", example = "18", nullable = true)
        private Integer avgAmTemperature;
        @Schema(description = "오후 평균 기온", example = "22", nullable = true)
        private Integer avgPmTemperature;
        @Schema(description = "강수확률", example = "30")
        private Integer precipitationProbability;
        @Schema(description = "최대 풍속", example = "3.5", nullable = true)
        private Double maxWindSpeed;
        @Schema(description = "미세먼지 수치", example = "41", nullable = true)
        private Integer fineDustValue;
        @Schema(description = "미세먼지 리스크 여부", example = "false")
        private boolean fineDustRisk;
        @Schema(description = "강우 여부", example = "false")
        private boolean hasRain;
        @Schema(description = "강설 여부", example = "false")
        private boolean hasSnow;
        @Schema(description = "폭염 리스크 여부", example = "false")
        private boolean heatRisk;
        @Schema(description = "한파 리스크 여부", example = "false")
        private boolean coldRisk;
        @Schema(description = "강풍 리스크 여부", example = "false")
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
                    .maxWindSpeed(null)
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
    @Schema(description = "리스크 항목 DTO")
    public static class RiskItem {
        @Schema(description = "리스크 배지", example = "HIGH")
        private String badge;
        @Schema(description = "리스크 제목", example = "강풍 주의")
        private String title;
        @Schema(description = "리스크 부제", example = "타워크레인 운용 점검")
        private String subtitle;
        @Schema(description = "리스크 레벨", example = "HIGH")
        private String level;
        @Schema(description = "리스크 사유", example = "최대 풍속 상승")
        private String reason;
        @Schema(description = "권장 조치", example = "고소 작업 전 안전 점검")
        private String action;
    }

    @Getter
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    @Schema(description = "알림 항목 DTO")
    public static class AlertItem {
        @Schema(description = "알림 제목", example = "주의보")
        private String title;
        @Schema(description = "알림 레벨", example = "WARN")
        private String level;
        @Schema(description = "알림 메시지", example = "오후 강수 확률이 높습니다")
        private String message;
    }

    @Getter
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    @Schema(description = "일별 예보 DTO")
    public static class ForecastDay {
        @Schema(description = "일자", example = "2026-05-28")
        private String date;
        @Schema(description = "요일 라벨", example = "목")
        private String dayLabel;
        @Schema(description = "날씨 라벨", example = "맑음")
        private String weatherLabel;
        @Schema(description = "최저 기온", example = "15", nullable = true)
        private Integer minTemp;
        @Schema(description = "최고 기온", example = "24", nullable = true)
        private Integer maxTemp;
        @Schema(description = "강수확률", example = "20", nullable = true)
        private Integer precipitationProbability;
        @Schema(description = "풍속", example = "2.8", nullable = true)
        private Double windSpeed;
    }

    @Getter
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    @Schema(description = "당일 간단 날씨 응답 DTO")
    public static class TodaySimpleRes {
        @Schema(description = "오전 상태", example = "맑음")
        private String amLabel;
        @Schema(description = "오후 상태", example = "구름많음")
        private String pmLabel;
        @Schema(description = "요약 문구", example = "오전 맑고 오후 구름 많음")
        private String summary;
    }
}
