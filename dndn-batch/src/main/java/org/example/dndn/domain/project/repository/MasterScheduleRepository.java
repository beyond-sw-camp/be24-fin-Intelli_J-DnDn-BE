package org.example.dndn.domain.project.repository;

import org.example.dndn.domain.project.model.entity.MasterSchedule;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface MasterScheduleRepository extends JpaRepository<MasterSchedule, Long> {

    List<MasterSchedule> findAllByProject_Idx(Long projectId);
}
