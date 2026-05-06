package org.example.dndn.worker.repository;

import org.example.dndn.worker.model.entity.AttendanceRecord;
import org.example.dndn.worker.model.enums.AttendanceStatus;
import org.example.dndn.worker.model.enums.JobRank;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDate;
import java.util.Collection;
import java.util.List;
import java.util.Optional;

public interface AttendanceRecordRepository extends JpaRepository<AttendanceRecord, Long> {
    // 특정 날짜의 모든 근태 (목록 N+1 방지용)
    List<AttendanceRecord> findAllByWorkDate(LocalDate workDate);

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

    // 상세 프로필 캘린더용 (workerId + 기간)
    List<AttendanceRecord> findAllByWorkerIdxAndWorkDateBetweenOrderByWorkDateDesc(
            Long workerIdx, LocalDate from, LocalDate to);

    /** MANAGEMENT_007 — 근태 행 기준 일자 내림차순 전체(구역·공종 표에 사용) */
    List<AttendanceRecord> findAllByWorkerIdxOrderByWorkDateDesc(Long workerIdx);

    // 일자별 근태 upsert
    Optional<AttendanceRecord> findByWorkerIdxAndWorkDate(Long workerIdx, LocalDate workDate);
}
