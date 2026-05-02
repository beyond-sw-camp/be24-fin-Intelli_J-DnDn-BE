package org.example.dndn.worker.repository;

import org.example.dndn.worker.model.entity.SafetyAccident;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface SafetyAccidentRepository extends JpaRepository<SafetyAccident, Long> {
    // MANAGEMENT_009 안전 사고 이력 — 중대 사고 우선 노출
    List<SafetyAccident> findAllByWorkerIdxOrderBySevereDescOccurredAtDesc(Long workerIdx);
}
