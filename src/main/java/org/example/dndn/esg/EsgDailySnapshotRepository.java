package org.example.dndn.esg;

import org.example.dndn.esg.model.EsgDailySnapshot;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

public interface EsgDailySnapshotRepository extends JpaRepository<EsgDailySnapshot, Long> {

    Optional<EsgDailySnapshot> findByProject_IdxAndReportDate(Long projectId, LocalDate reportDate);

    Optional<EsgDailySnapshot> findTopByProject_IdxAndReportDateBeforeOrderByReportDateDesc(
            Long projectId,
            LocalDate reportDate
    );

    Optional<EsgDailySnapshot> findTopByProject_IdxAndReportDateLessThanEqualOrderByReportDateDesc(
            Long projectId,
            LocalDate reportDate
    );

    List<EsgDailySnapshot> findAllByReportDate(LocalDate reportDate);
}
