package org.example.dndn.staffing.repository;

import org.example.dndn.staffing.model.StaffingAssignment;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.util.List;

public interface StaffingAssignmentRepository extends JpaRepository<StaffingAssignment, Long> {

    @Query("select distinct a.workerIdx from StaffingAssignment a")
    List<Long> findDistinctAssignedWorkerIdxes();
}
