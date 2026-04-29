package org.example.dndn.weather;

import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import org.example.dndn.weather.model.WeatherInfo;
import org.example.dndn.weather.model.WeatherInfoDto;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;

@Component
@RequiredArgsConstructor
public class WeatherSnapshotWriter {

    private final WeatherInfoRepository weatherInfoRepository;
    private final ObjectMapper objectMapper = new ObjectMapper();


    @Transactional
    public void save(LocalDate targetDate, String locationLabel, WeatherInfoDto.DashboardRes response) {
        try {
            String dashboardJson = objectMapper.writeValueAsString(response);
            Integer fineDustValue = response.getAirQuality() != null ? response.getAirQuality().getValue() : null;
            String fineDustGrade = response.getAirQuality() != null ? response.getAirQuality().getGrade() : "";

            WeatherInfo snapshot = weatherInfoRepository.findByReportDate(targetDate)
                    .orElseGet(() -> WeatherInfo.builder()
                            .reportDate(targetDate)
                            .locationLabel(locationLabel)
                            .amLabel("기상정보 없음")
                            .pmLabel("기상정보 없음")
                            .todayHeadlineTemp("")
                            .todaySummary("")
                            .weeklySummary("")
                            .precipitationProbability("")
                            .fineDustValue("")
                            .fineDustGrade("")
                            .dashboardJson("")
                            .build());

            snapshot.updateSnapshot(
                    locationLabel,
                    response.getToday().getHeadlineTemp(),
                    response.getToday().getSummary(),
                    response.getToday().getAmLabel(),
                    response.getToday().getPmLabel(),
                    response.getWeek().getSummary(),
                    response.getRain().getValue(),
                    fineDustValue == null ? "" : String.valueOf(fineDustValue),
                    fineDustGrade == null ? "" : fineDustGrade,
                    dashboardJson
            );

            weatherInfoRepository.save(snapshot);
        } catch (Exception ignored) {
        }
    }
}
