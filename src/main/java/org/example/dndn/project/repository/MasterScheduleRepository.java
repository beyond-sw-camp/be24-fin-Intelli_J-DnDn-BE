package org.example.dndn.project.repository;

import org.example.dndn.project.model.enums.DocType;
import org.example.dndn.project.model.entity.MasterSchedule;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface MasterScheduleRepository extends JpaRepository<MasterSchedule, Long> {

    List<MasterSchedule> findAllByProject_Idx(Long projectId);

    List<MasterSchedule> findAllByProject_IdxAndDocType(Long projectId, DocType docType);
}