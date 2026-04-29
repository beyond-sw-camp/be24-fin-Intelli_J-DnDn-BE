package org.example.dndn.weather;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.example.dndn.weather.model.WeatherInfoDto;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.util.UriComponentsBuilder;

@Component
public class AirKoreaClient {

    @Value("${weather.air-korea.service-key:}")
    private String serviceKey;

    @Value("${weather.air-korea.sido-url:}")
    private String sidoUrl;

    @Value("${weather.air-korea.sido-name:서울}")
    private String sidoName;

    private final RestTemplate restTemplate = new RestTemplate();
    private final ObjectMapper objectMapper = new ObjectMapper();

    public WeatherInfoDto.AirQualityCard fetchSidoPm10() {
        if (serviceKey == null || serviceKey.isBlank()
                || sidoUrl == null || sidoUrl.isBlank()) {
            return WeatherInfoDto.AirQualityCard.empty();
        }

        try {
            String url = UriComponentsBuilder.fromHttpUrl(sidoUrl)
                    .queryParam("serviceKey", serviceKey)
                    .queryParam("returnType", "json")
                    .queryParam("numOfRows", 100)
                    .queryParam("pageNo", 1)
                    .queryParam("sidoName", sidoName)
                    .queryParam("ver", "1.0")
                    .build(false)
                    .toUriString();

            JsonNode root = objectMapper.readTree(restTemplate.getForObject(url, String.class));
            JsonNode itemsNode = root.path("response").path("body").path("items");

            if (itemsNode.isMissingNode() || !itemsNode.isArray() || itemsNode.size() == 0) {
                return WeatherInfoDto.AirQualityCard.empty();
            }

            int sum = 0;
            int count = 0;
            for (JsonNode item : itemsNode) {
                String stationName = item.path("stationName").asText("");
                String pm10Text = item.path("pm10Value").asText("");

                if (stationName.contains("평균") && !pm10Text.isBlank() && !"-".equals(pm10Text)) {
                    Integer pm10 = parsePm10(pm10Text);
                    if (pm10 != null) {
                        return buildCard(pm10);
                    }
                }

                Integer pm10 = parsePm10(pm10Text);
                if (pm10 != null) {
                    sum += pm10;
                    count++;
                }
            }

            if (count == 0) {
                return WeatherInfoDto.AirQualityCard.empty();
            }

            int avg = Math.round((float) sum / count);
            return buildCard(avg);
        } catch (Exception e) {
            return WeatherInfoDto.AirQualityCard.empty();
        }
    }

    private WeatherInfoDto.AirQualityCard buildCard(int pm10) {
        String grade = resolveGrade(pm10);

        return WeatherInfoDto.AirQualityCard.builder()
                .available(true)
                .value(pm10)
                .pm10(pm10)
                .grade(grade)
                .label(grade)
                .build();
    }

    private String resolveGrade(int pm10) {
        if (pm10 <= 30) {
            return "좋음";
        }
        if (pm10 <= 80) {
            return "보통";
        }
        if (pm10 <= 150) {
            return "나쁨";
        }
        return "매우 나쁨";
    }

    private Integer parsePm10(String value) {
        if (value == null || value.isBlank() || "-".equals(value)) {
            return null;
        }
        try {
            return Integer.parseInt(value.trim());
        } catch (Exception e) {
            return null;
        }
    }
}
