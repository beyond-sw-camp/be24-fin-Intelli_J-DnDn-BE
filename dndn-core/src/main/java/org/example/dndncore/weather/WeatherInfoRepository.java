package org.example.dndncore.weather;

import org.example.dndncore.weather.model.WeatherInfo;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.LocalDate;
import java.util.Optional;

public interface WeatherInfoRepository extends JpaRepository<WeatherInfo, Long> {

    Optional<WeatherInfo> findByReportDate(LocalDate reportDate);
}
