package org.example.dndncore.ai;

import org.example.dndncore.ai.model.AiScheduleRecommendation;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface AiScheduleRecommendationRepository extends JpaRepository<AiScheduleRecommendation, Long> {
    List<AiScheduleRecommendation> findAllByProject_IdxOrderByCreatedAtDesc(Long projectId);
}
