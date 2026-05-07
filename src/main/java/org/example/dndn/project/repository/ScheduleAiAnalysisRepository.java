package org.example.dndn.project.repository;

import org.example.dndn.project.model.entity.ScheduleAiAnalysis;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface ScheduleAiAnalysisRepository extends JpaRepository<ScheduleAiAnalysis, Long> {

    List<ScheduleAiAnalysis> findByMasterSchedule_Idx(Long masterScheduleId);
}