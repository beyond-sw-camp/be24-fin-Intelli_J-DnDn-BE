package org.example.dndn.domain.worker.repository;

import org.example.dndn.domain.worker.model.entity.AttendanceLog;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDate;
import java.util.List;

public interface AttendanceLogRepository extends JpaRepository<AttendanceLog, Long> {

    List<AttendanceLog> findAllByWorkerIdxAndWorkDateBetween(Long workerIdx, LocalDate from, LocalDate to);

    // 벌크 피로도 계산용 — N명 전체 로그를 1회 IN 쿼리로 조회
    List<AttendanceLog> findAllByWorkerIdxInAndWorkDateBetween(List<Long> workerIdxes, LocalDate from, LocalDate to);

    void deleteAllByWorkerIdxAndWorkDate(Long workerIdx, LocalDate workDate);

    // clearAutomatically=false 유지: Worker 컨텍스트 보호 (AttendanceLog는 workerIdx 기반)
    @Modifying
    @Query("DELETE FROM AttendanceLog l WHERE l.workerIdx IN :workerIdxes AND l.workDate = :date")
    int deleteAllByWorkerIdxInAndWorkDate(
            @Param("workerIdxes") List<Long> workerIdxes,
            @Param("date") LocalDate date);

    // 테스트 중복 방지용 사전 정리 — RosterCleanupStep(단일 트랜잭션)에서만 호출
    @Modifying
    @Query("DELETE FROM AttendanceLog l WHERE l.workDate = :date")
    int deleteAllByWorkDate(@Param("date") LocalDate date);
}
