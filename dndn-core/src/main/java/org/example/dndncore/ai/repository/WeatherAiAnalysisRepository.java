package org.example.dndncore.ai.repository;

import org.example.dndncore.ai.entity.WeatherAiAnalysis;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.LocalDate;
import java.util.Optional;

public interface WeatherAiAnalysisRepository extends JpaRepository<WeatherAiAnalysis, Long> {

    Optional<WeatherAiAnalysis> findByProjectIdAndAnalysisDate(Long projectId, LocalDate analysisDate);

    Optional<WeatherAiAnalysis> findFirstByProjectIdIsNullAndAnalysisDate(LocalDate analysisDate);

    default Optional<WeatherAiAnalysis> findSnapshot(Long projectId, LocalDate analysisDate) {
        if (projectId == null) {
            return findFirstByProjectIdIsNullAndAnalysisDate(analysisDate);
        }
        return findByProjectIdAndAnalysisDate(projectId, analysisDate);
    }
}
