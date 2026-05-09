package org.example.dndn.ai.repository;

import org.example.dndn.ai.entity.WeatherAiAnalysis;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.LocalDate;
import java.util.Optional;

public interface WeatherAiAnalysisRepository extends JpaRepository<WeatherAiAnalysis, Long> {

    Optional<WeatherAiAnalysis> findByAnalysisDate(LocalDate analysisDate);
}