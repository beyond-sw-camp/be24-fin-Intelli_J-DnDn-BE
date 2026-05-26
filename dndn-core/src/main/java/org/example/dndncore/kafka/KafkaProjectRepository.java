package org.example.dndncore.kafka;

import org.example.dndncore.kafka.dto.KafkaProject;
import org.springframework.data.jpa.repository.JpaRepository;

public interface KafkaProjectRepository extends JpaRepository<KafkaProject, Long> {
}
