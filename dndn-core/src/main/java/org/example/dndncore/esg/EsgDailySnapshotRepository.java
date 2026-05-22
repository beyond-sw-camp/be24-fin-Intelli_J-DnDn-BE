package org.example.dndncore.esg;

import org.example.dndncore.esg.model.EsgDailySnapshot;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

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

    @Query("""
            select snapshot
            from EsgDailySnapshot snapshot
            where snapshot.project.idx in :projectIds
              and snapshot.reportDate = (
                    select max(candidate.reportDate)
                    from EsgDailySnapshot candidate
                    where candidate.project.idx = snapshot.project.idx
                      and candidate.reportDate <= :reportDate
              )
            """)
    List<EsgDailySnapshot> findLatestByProjectIdsAndReportDateLessThanEqual(
            @Param("projectIds") List<Long> projectIds,
            @Param("reportDate") LocalDate reportDate
    );
}
