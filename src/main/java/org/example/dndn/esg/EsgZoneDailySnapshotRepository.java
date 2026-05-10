package org.example.dndn.esg;

import org.example.dndn.esg.model.EsgZoneDailySnapshot;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.LocalDate;
import java.util.List;

public interface EsgZoneDailySnapshotRepository extends JpaRepository<EsgZoneDailySnapshot, Long> {

    List<EsgZoneDailySnapshot> findAllByProject_IdxAndReportDate(Long projectId, LocalDate reportDate);

    List<EsgZoneDailySnapshot> findAllByReportDate(LocalDate reportDate);

    void deleteByProject_IdxAndReportDate(Long projectId, LocalDate reportDate);
}
