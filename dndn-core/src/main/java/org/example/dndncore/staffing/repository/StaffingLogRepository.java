package org.example.dndncore.staffing.repository;

import org.example.dndncore.staffing.model.StaffingLog;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDate;
import java.util.List;

public interface StaffingLogRepository extends JpaRepository<StaffingLog, Long> {

    /** MANAGEMENT_007 구역 배치 이력 — 작업자별 전체, 최신순 */
    List<StaffingLog> findAllByWorkerIdxOrderByCreatedAtDesc(Long workerIdx);

    /** MANAGEMENT_006 출결 탭 구역 표시 — 작업자별 날짜 범위 */
    List<StaffingLog> findAllByWorkerIdxAndWorkDateBetween(Long workerIdx, LocalDate from, LocalDate to);

    /** 당일 + 현장별 확정 배치 목록 — 최신순(dedup 기준) */
    List<StaffingLog> findAllBySiteCodeAndWorkDateOrderByCreatedAtDesc(String siteCode, LocalDate workDate);

    /** 당일 전체 확정 배치 목록 — 최신순 */
    List<StaffingLog> findAllByWorkDateOrderByCreatedAtDesc(LocalDate workDate);

    /** 더미 시딩: 특정 날짜 범위 배치 이력 일괄 삭제 */
    @Modifying
    @Query("DELETE FROM StaffingLog sl WHERE sl.workerIdx IN :workerIdxes AND sl.workDate BETWEEN :from AND :to")
    int deleteAllByWorkerIdxInAndWorkDateBetween(
            @Param("workerIdxes") List<Long> workerIdxes,
            @Param("from") LocalDate from,
            @Param("to") LocalDate to);
}
