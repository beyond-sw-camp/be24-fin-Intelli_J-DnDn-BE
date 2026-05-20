package org.example.dndncore.worker.repository;

import org.example.dndncore.worker.model.entity.AttendanceLog;
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

    // 배치 벌크 동기화: 여러 근로자의 특정 날짜 로그 일괄 삭제 (영속성 컨텍스트 우회)
    @Modifying
    @Query("DELETE FROM AttendanceLog al WHERE al.workerIdx IN :workerIdxes AND al.workDate = :workDate")
    int deleteAllByWorkerIdxInAndWorkDate(
            @Param("workerIdxes") List<Long> workerIdxes,
            @Param("workDate") LocalDate workDate);

    // 더미 시딩: 특정 날짜 범위의 로그 일괄 삭제
    @Modifying
    @Query("DELETE FROM AttendanceLog al WHERE al.workerIdx IN :workerIdxes AND al.workDate BETWEEN :from AND :to")
    int deleteAllByWorkerIdxInAndWorkDateBetween(
            @Param("workerIdxes") List<Long> workerIdxes,
            @Param("from") LocalDate from,
            @Param("to") LocalDate to);
}
