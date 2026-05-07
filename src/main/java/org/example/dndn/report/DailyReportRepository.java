package org.example.dndn.report;

import org.example.dndn.report.model.DailyReport;
import org.springframework.data.jpa.repository.JpaRepository;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

public interface DailyReportRepository extends JpaRepository<DailyReport, Long> {

    // [REPORT_002] 2단계 : 특정 일자 공사일보 목록 조회 기능
    // feat : 특정 날짜의 공사일보 전체 목록 조회 쿼리 추가
    List<DailyReport> findByReportDate(LocalDate reportDate);

    // [REPORT_003] 3단계 : 공사일보 제출(Upsert) 기본 로직 구현
    // feat : 중복 방지를 위한 특정 주간계획의 특정 날짜 공사일보 조회 쿼리 추가
    Optional<DailyReport> findByWorkPlan_IdxAndReportDate(Long workPlanId, LocalDate reportDate);
}