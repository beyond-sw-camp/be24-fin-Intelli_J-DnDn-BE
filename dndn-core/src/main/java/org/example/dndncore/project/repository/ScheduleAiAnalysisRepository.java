package org.example.dndncore.project.repository;

import org.example.dndncore.project.model.entity.ScheduleAiAnalysis;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Collection;
import java.util.List;

public interface ScheduleAiAnalysisRepository extends JpaRepository<ScheduleAiAnalysis, Long> {

    List<ScheduleAiAnalysis> findByMasterSchedule_Idx(Long masterScheduleId);

    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("delete from ScheduleAiAnalysis saa where saa.masterSchedule.idx in :masterScheduleIds")
    int deleteByMasterScheduleIds(@Param("masterScheduleIds") Collection<Long> masterScheduleIds);
}
