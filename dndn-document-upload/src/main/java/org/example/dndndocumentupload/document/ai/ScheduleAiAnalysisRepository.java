package org.example.dndndocumentupload.document.ai;

import org.example.dndndocumentupload.document.model.entity.ScheduleAiAnalysis;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Collection;
import java.util.List;

public interface ScheduleAiAnalysisRepository extends JpaRepository<ScheduleAiAnalysis, Long> {

    List<ScheduleAiAnalysis> findByMasterScheduleIdx(Long masterScheduleId);

    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("delete from ScheduleAiAnalysis saa where saa.masterScheduleIdx in :masterScheduleIds")
    int deleteByMasterScheduleIds(@Param("masterScheduleIds") Collection<Long> masterScheduleIds);
}
