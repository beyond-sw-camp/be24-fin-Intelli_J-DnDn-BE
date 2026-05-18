package org.example.dndncore.report;

import org.example.dndncore.report.model.DailyReport;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import java.time.LocalDate;
import java.util.Collection;
import java.util.List;
import java.util.Optional;

public interface DailyReportRepository extends JpaRepository<DailyReport, Long> {

    // [REPORT_002] 2단계 : 특정 일자 공사일보 목록 조회 기능
    // feat : 특정 날짜의 공사일보 전체 목록 조회 쿼리 추가
    List<DailyReport> findByReportDate(LocalDate reportDate);

    // [REPORT_003] 3단계 : 공사일보 제출(Upsert) 기본 로직 구현
    // feat : 중복 방지를 위한 특정 주간계획의 특정 날짜 공사일보 조회 쿼리 추가
    Optional<DailyReport> findByWorkPlan_IdxAndReportDate(Long workPlanId, LocalDate reportDate);

    Optional<DailyReport> findTopByWorkPlan_IdxAndReportDateLessThanEqualOrderByReportDateDesc(
            Long workPlanId,
            LocalDate reportDate
    );

    Optional<DailyReport> findTopByMonthlyWorkPlan_IdxAndReportDateLessThanEqualOrderByReportDateDesc(
            Long monthlyWorkPlanId,
            LocalDate reportDate
    );

    @Query("SELECT dr FROM DailyReport dr " +
            "JOIN FETCH dr.monthlyWorkPlan mwp " +
            "WHERE mwp.idx IN :monthlyWorkPlanIds " +
            "AND dr.reportDate <= :reportDate " +
            "ORDER BY mwp.idx ASC, dr.reportDate DESC")
    List<DailyReport> findAllByMonthlyPlanIdsUntilDate(
            @Param("monthlyWorkPlanIds") Collection<Long> monthlyWorkPlanIds,
            @Param("reportDate") LocalDate reportDate
    );

    @Query("SELECT dr FROM DailyReport dr " +
            "JOIN FETCH dr.workPlan wp " +
            "WHERE wp.idx IN :workPlanIds " +
            "AND dr.reportDate <= :reportDate " +
            "ORDER BY wp.idx ASC, dr.reportDate DESC")
    List<DailyReport> findAllByWorkPlanIdsUntilDate(
            @Param("workPlanIds") Collection<Long> workPlanIds,
            @Param("reportDate") LocalDate reportDate
    );
}
