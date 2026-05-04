package org.example.dndn.worker.repository;

import org.example.dndn.worker.model.entity.SafetyAccident;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.LocalDate;
import java.util.List;

public interface SafetyAccidentRepository extends JpaRepository<SafetyAccident, Long> {
    /** MANAGEMENT_009 안전 사고 이력 — 최근 발생 일자 우선 */
    List<SafetyAccident> findAllByWorkerIdxOrderByOccurredAtDesc(Long workerIdx);

    boolean existsByWorkerIdxAndOccurredAtAndAccidentTypeAndZoneMainAndZoneSub(
            Long workerIdx, LocalDate occurredAt, String accidentType, String zoneMain, String zoneSub);
}
