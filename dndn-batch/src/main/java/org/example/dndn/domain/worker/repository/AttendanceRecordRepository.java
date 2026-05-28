package org.example.dndn.domain.worker.repository;

import org.example.dndn.domain.worker.model.entity.AttendanceRecord;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

public interface AttendanceRecordRepository extends JpaRepository<AttendanceRecord, Long> {

    Optional<AttendanceRecord> findByWorkerIdxAndWorkDate(Long workerIdx, LocalDate workDate);

    // 벌크 동기화 전 employment_kind 보존용 경량 조회 (엔티티 전체 로딩 없음)
    @Query("SELECT r.worker.idx AS workerIdx, r.employmentKind AS ek " +
           "FROM AttendanceRecord r WHERE r.worker.idx IN :workerIdxes AND r.workDate = :date")
    List<WorkerEmploymentKindProjection> findEmploymentKindsByWorkerIdxes(
            @Param("workerIdxes") List<Long> workerIdxes,
            @Param("date") LocalDate date);

    // clearAutomatically=false 유지: true 시 Worker detached → recalculateFatigue 오류
    @Modifying
    @Query("DELETE FROM AttendanceRecord r WHERE r.worker.idx IN :workerIdxes AND r.workDate = :date")
    int deleteAllByWorkerIdxInAndWorkDate(
            @Param("workerIdxes") List<Long> workerIdxes,
            @Param("date") LocalDate date);

    // 테스트 중복 방지용 사전 정리 — RosterCleanupStep(단일 트랜잭션)에서만 호출
    @Modifying
    @Query("DELETE FROM AttendanceRecord r WHERE r.workDate = :date")
    int deleteAllByWorkDate(@Param("date") LocalDate date);

    /** 당일 로스터 전용 정책 — 과거 work_date 스냅샷 잔존 분 정리 */
    @Modifying
    @Query("DELETE FROM AttendanceRecord r WHERE r.workDate < :date")
    int deleteAllByWorkDateBefore(@Param("date") LocalDate date);
}
