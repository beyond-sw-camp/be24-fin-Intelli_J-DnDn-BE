package org.example.dndn.worker;

import org.example.dndn.worker.model.entity.Worker;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface WorkerRepository extends JpaRepository<Worker, Long> {
    /** sync 시 dedup key — 시연 JSON 의 externalCode 또는 향후 연동 식별자 */
    Optional<Worker> findByExternalCode(String externalCode);
}
