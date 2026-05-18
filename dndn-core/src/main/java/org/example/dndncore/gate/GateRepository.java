package org.example.dndncore.gate;

import org.example.dndncore.gate.model.Gate;
import org.springframework.data.jpa.repository.JpaRepository;

public interface GateRepository extends JpaRepository<Gate, Long> {
}