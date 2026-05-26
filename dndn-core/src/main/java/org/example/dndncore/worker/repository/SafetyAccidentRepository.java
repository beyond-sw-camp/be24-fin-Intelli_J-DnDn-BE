package org.example.dndncore.worker.repository;

import org.example.dndncore.worker.model.entity.SafetyAccident;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDate;
import java.util.List;

public interface SafetyAccidentRepository extends JpaRepository<SafetyAccident, Long> {
    /** MANAGEMENT_009 안전 사고 이력 — 최근 발생 건 존재(피로도 산정용) */
    boolean existsByWorkerIdxAndOccurredAtBetween(Long workerIdx, LocalDate fromInclusive, LocalDate toInclusive);

    /** MANAGEMENT_009 안전 사고 이력 — 최근 발생 일자 우선 */
    List<SafetyAccident> findAllByWorkerIdxOrderByOccurredAtDesc(Long workerIdx);

    // 벌크 피로도 계산용 — 사고 이력이 있는 workerIdx만 1회 IN 쿼리로 반환
    @Query("SELECT DISTINCT a.worker.idx FROM SafetyAccident a " +
           "WHERE a.worker.idx IN :workerIdxes AND a.occurredAt BETWEEN :from AND :to")
    List<Long> findWorkerIdxesWithAccidentBetween(
            @Param("workerIdxes") List<Long> workerIdxes,
            @Param("from") LocalDate from,
            @Param("to") LocalDate to);

    // 더미 시딩 중복 방지 — 특정 날짜+타입의 사고가 이미 있는 workerIdx 집합을 1회 쿼리로 반환
    @Query("SELECT DISTINCT a.worker.idx FROM SafetyAccident a " +
           "WHERE a.worker.idx IN :workerIdxes AND a.occurredAt = :occurredAt AND a.accidentType = :accidentType")
    List<Long> findWorkerIdxesWithAccidentOnDate(
            @Param("workerIdxes") List<Long> workerIdxes,
            @Param("occurredAt") LocalDate occurredAt,
            @Param("accidentType") String accidentType);
}
