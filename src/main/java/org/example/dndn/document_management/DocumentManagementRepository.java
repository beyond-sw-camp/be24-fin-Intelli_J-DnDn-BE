package org.example.dndn.document_management;

import org.example.dndn.project.model.entity.MasterSchedule;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface DocumentManagementRepository extends JpaRepository<MasterSchedule, Long> {
    MasterSchedule findByProjectIdx(Long attr0);
}
