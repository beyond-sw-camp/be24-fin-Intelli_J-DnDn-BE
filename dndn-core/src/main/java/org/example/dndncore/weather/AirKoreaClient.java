package org.example.dndncore.weather;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.example.dndncore.weather.model.WeatherInfoDto;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.util.UriComponentsBuilder;

import java.util.ArrayList;
import java.util.List;

@Slf4j
@Component
public class AirKoreaClient {

    @Value("${weather.air-korea.service-key:}")
    private String serviceKey;

    @Value("${weather.air-korea.sido-url:}")
    private String sidoUrl;

    @Value("${weather.air-korea.sido-name:서울}")
    private String sidoName;

    private final RestTemplate restTemplate = createRestTemplate();
    private final ObjectMapper objectMapper = new ObjectMapper();

    private RestTemplate createRestTemplate() {
        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(5_000);
        factory.setReadTimeout(10_000);
        return new RestTemplate(factory);
    }

    public WeatherInfoDto.AirQualityCard fetchSidoPm10() {
        if (serviceKey == null || serviceKey.isBlank()
                || sidoUrl == null || sidoUrl.isBlank()) {
            log.warn("[AirKorea] 인증키 또는 URL 미설정 — service-key blank: {}, sido-url blank: {}",
                    serviceKey == null || serviceKey.isBlank(),
                    sidoUrl == null || sidoUrl.isBlank());
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

            log.debug("[AirKorea] 호출 시작 - sidoName={}", sidoName);

            String rawResponse = restTemplate.getForObject(url, String.class);
            if (rawResponse == null || rawResponse.isBlank()) {
                log.warn("[AirKorea] 응답 본문 비어있음");
                return WeatherInfoDto.AirQualityCard.empty();
            }

            log.debug("[AirKorea] 원시 응답 (앞 500자): {}",
                    rawResponse.substring(0, Math.min(500, rawResponse.length())));

            JsonNode root = objectMapper.readTree(rawResponse);
            String resultCode = root.path("response").path("header").path("resultCode").asText("");
            String resultMsg = root.path("response").path("header").path("resultMsg").asText("");

            if (!("00".equals(resultCode) || "0".equals(resultCode))) {
                log.warn("[AirKorea] API 응답 비정상 — resultCode: {}, resultMsg: {}", resultCode, resultMsg);
                return WeatherInfoDto.AirQualityCard.empty();
            }

            List<JsonNode> items = extractItems(root.path("response").path("body").path("items"));
            if (items.isEmpty()) {
                log.warn("[AirKorea] items 비어있음");
                return WeatherInfoDto.AirQualityCard.empty();
            }

            int sum = 0;
            int count = 0;

            for (JsonNode item : items) {
                String stationName = item.path("stationName").asText("");
                Integer pm10 = parsePm10(firstNonBlank(
                        item.path("pm10Value").asText(""),
                        item.path("pm10Value24").asText("")
                ));

                if (stationName.contains("평균") && pm10 != null) {
                    log.info("[AirKorea] 평균 행 사용 — pm10: {}", pm10);
                    return buildCard(pm10);
                }

                if (pm10 != null) {
                    sum += pm10;
                    count++;
                }
            }

            if (count == 0) {
                log.warn("[AirKorea] 유효한 pm10 값 0건 — items.size: {}", items.size());
                return WeatherInfoDto.AirQualityCard.empty();
            }

            int avg = Math.round((float) sum / count);
            log.info("[AirKorea] 측정소 평균 산정 완료 — pm10 평균: {} (측정소 {}개)", avg, count);
            return buildCard(avg);
        } catch (HttpClientErrorException.TooManyRequests e) {
            log.warn("[AirKorea] 일일 호출량 초과로 미세먼지 조회를 건너뜁니다. - status={}, body={}",
                    e.getStatusCode(), e.getResponseBodyAsString());
            return WeatherInfoDto.AirQualityCard.empty();
        } catch (Exception e) {
            log.warn("[AirKorea] API 호출 실패 - reason={}", summarizeException(e));
            return WeatherInfoDto.AirQualityCard.empty();
        }
    }

    private List<JsonNode> extractItems(JsonNode itemsNode) {
        List<JsonNode> result = new ArrayList<>();

        if (itemsNode == null || itemsNode.isMissingNode() || itemsNode.isNull()) {
            return result;
        }

        if (itemsNode.isArray()) {
            itemsNode.forEach(result::add);
            return result;
        }

        JsonNode itemNode = itemsNode.path("item");
        if (itemNode.isArray()) {
            itemNode.forEach(result::add);
            return result;
        }

        if (itemNode.isObject()) {
            result.add(itemNode);
            return result;
        }

        if (itemsNode.isObject()) {
            result.add(itemsNode);
        }

        return result;
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

    private String summarizeException(Exception e) {
        if (e == null) {
            return "unknown";
        }

        Throwable target = e.getCause() != null ? e.getCause() : e;
        String message = target.getMessage();
        String simpleMessage = message == null || message.isBlank() ? "no message" : message;
        return target.getClass().getSimpleName() + ": " + simpleMessage;
    }

    private String firstNonBlank(String... values) {
        if (values == null) {
            return "";
        }

        for (String value : values) {
            if (value != null && !value.isBlank() && !"-".equals(value)) {
                return value;
            }
        }
        return "";
    }
}
