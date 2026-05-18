package org.example.dndncore.project.repository;

import org.example.dndncore.project.model.enums.DocType;
import org.example.dndncore.project.model.entity.MasterSchedule;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Collection;
import java.util.List;

public interface MasterScheduleRepository extends JpaRepository<MasterSchedule, Long> {

    List<MasterSchedule> findAllByProject_Idx(Long projectId);

    List<MasterSchedule> findAllByProject_IdxAndDocType(Long projectId, DocType docType);

    boolean existsByProject_Idx(Long projectId);

    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("delete from MasterSchedule ms where ms.idx in :scheduleIds")
    int deleteByIds(@Param("scheduleIds") Collection<Long> scheduleIds);
}
