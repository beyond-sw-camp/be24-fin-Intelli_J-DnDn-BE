package org.example.dndncore.worker.repository;

import org.example.dndncore.worker.model.entity.AttendanceRecord;
import org.example.dndncore.worker.model.enums.AttendanceStatus;
import org.example.dndncore.worker.model.enums.JobRank;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDate;
import java.util.Collection;
import java.util.List;
import java.util.Optional;

public interface AttendanceRecordRepository extends JpaRepository<AttendanceRecord, Long> {
    // 당일 스냅샷 전체 조회 — JOIN FETCH로 N+1 방지 (workDate는 항상 오늘, 배치 재실행 중 잔여 데이터 방어용)
    @Query("SELECT ar FROM AttendanceRecord ar JOIN FETCH ar.worker WHERE ar.workDate = :workDate")
    List<AttendanceRecord> findAllByWorkDate(@Param("workDate") LocalDate workDate);

    // 특정 날짜 + 현장코드 필터 (현장 분리 조회)
    @Query("""
            select ar from AttendanceRecord ar
                join fetch ar.worker w
            where ar.workDate = :workDate
                and ar.siteCode = :siteCode
            """)
    List<AttendanceRecord> findAllByWorkDateAndSiteCode(
            @Param("workDate") LocalDate workDate,
            @Param("siteCode") String siteCode);

    // STAFFING_008 명단 — 지각(LATE)·정상 출근(PRESENT), WORKER rank
    @Query("""
            select ar from AttendanceRecord ar
                join fetch ar.worker w
            where ar.workDate = :workDate
                and w.jobRank = :jobRank
                and ar.attendanceStatus in :attendanceStatuses
            """)
    List<AttendanceRecord> findAllByWorkDateAndWorkerJobRank(
            @Param("workDate") LocalDate workDate,
            @Param("jobRank") JobRank jobRank,
            @Param("attendanceStatuses") Collection<AttendanceStatus> attendanceStatuses);

    // STAFFING_008 명단 — siteCode 현장 필터 추가
    @Query("""
            select ar from AttendanceRecord ar
                join fetch ar.worker w
            where ar.workDate = :workDate
                and w.jobRank = :jobRank
                and ar.siteCode = :siteCode
                and ar.attendanceStatus in :attendanceStatuses
            """)
    List<AttendanceRecord> findAllByWorkDateAndWorkerJobRankAndSiteCode(
            @Param("workDate") LocalDate workDate,
            @Param("jobRank") JobRank jobRank,
            @Param("siteCode") String siteCode,
            @Param("attendanceStatuses") Collection<AttendanceStatus> attendanceStatuses);

    // STAFFING_006 선택 — 해당 일자 명단 행 일괄(bulk 비어 있으면 호출하지 말 것)
    @Query("""
            select ar from AttendanceRecord ar
                join fetch ar.worker w
            where ar.workDate = :workDate
                and w.idx in :workerIdxes
                and ar.attendanceStatus in :attendanceStatuses
            """)
    List<AttendanceRecord> findAllByWorkDateAndWorkerIdxIn(
            @Param("workDate") LocalDate workDate,
            @Param("workerIdxes") Collection<Long> workerIdxes,
            @Param("attendanceStatuses") Collection<AttendanceStatus> attendanceStatuses);

    // 일자별 근태 upsert
    Optional<AttendanceRecord> findByWorkerIdxAndWorkDate(Long workerIdx, LocalDate workDate);

    // 배치 벌크 동기화: 여러 근로자의 특정 날짜 기존 행 조회 (employment_kind 보존용)
    @Query("SELECT ar FROM AttendanceRecord ar WHERE ar.worker.idx IN :workerIdxes AND ar.workDate = :workDate")
    List<AttendanceRecord> findAllByWorkerIdxInAndWorkDate(
            @Param("workerIdxes") List<Long> workerIdxes,
            @Param("workDate") LocalDate workDate);

    // 배치 벌크 동기화: 여러 근로자의 특정 날짜 기존 행 일괄 삭제 (영속성 컨텍스트 우회)
    @Modifying
    @Query("DELETE FROM AttendanceRecord ar WHERE ar.worker.idx IN :workerIdxes AND ar.workDate = :workDate")
    int deleteAllByWorkerIdxInAndWorkDate(
            @Param("workerIdxes") List<Long> workerIdxes,
            @Param("workDate") LocalDate workDate);

}
