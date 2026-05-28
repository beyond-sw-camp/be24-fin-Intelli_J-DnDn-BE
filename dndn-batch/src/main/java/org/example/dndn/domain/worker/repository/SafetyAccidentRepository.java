package org.example.dndn.domain.worker.repository;

import org.example.dndn.domain.worker.model.entity.SafetyAccident;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDate;
import java.util.List;

public interface SafetyAccidentRepository extends JpaRepository<SafetyAccident, Long> {

    boolean existsByWorkerIdxAndOccurredAtBetween(Long workerIdx, LocalDate fromInclusive, LocalDate toInclusive);

    boolean existsByWorkerIdxAndOccurredAtAndAccidentTypeAndZoneMainAndZoneSub(
            Long workerIdx, LocalDate occurredAt, String accidentType, String zoneMain, String zoneSub);

    // 사고 이력이 있는 workerIdx만 반환 (벌크 피로도 계산용 — N명 → 1회 IN 쿼리)
    @Query("SELECT DISTINCT a.worker.idx FROM SafetyAccident a " +
           "WHERE a.worker.idx IN :workerIdxes AND a.occurredAt BETWEEN :from AND :to")
    List<Long> findWorkerIdxesWithAccidentBetween(
            @Param("workerIdxes") List<Long> workerIdxes,
            @Param("from") LocalDate from,
            @Param("to") LocalDate to);
}
