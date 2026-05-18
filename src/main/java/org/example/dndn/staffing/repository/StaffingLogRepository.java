package org.example.dndn.staffing.repository;

import org.example.dndn.staffing.model.StaffingLog;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.LocalDate;
import java.util.List;

public interface StaffingLogRepository extends JpaRepository<StaffingLog, Long> {

    /** MANAGEMENT_007 구역 배치 이력 — 작업자별 전체, 최신순 */
    List<StaffingLog> findAllByWorkerIdxOrderByCreatedAtDesc(Long workerIdx);

    /** 당일 + 현장별 확정 배치 목록 — 최신순(dedup 기준) */
    List<StaffingLog> findAllBySiteCodeAndWorkDateOrderByCreatedAtDesc(String siteCode, LocalDate workDate);

    /** 당일 전체 확정 배치 목록 — 최신순 */
    List<StaffingLog> findAllByWorkDateOrderByCreatedAtDesc(LocalDate workDate);
}
