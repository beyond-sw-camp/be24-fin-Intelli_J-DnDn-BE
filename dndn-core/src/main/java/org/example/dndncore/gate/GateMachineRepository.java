package org.example.dndncore.gate;

import org.example.dndncore.gate.model.GateMachine;
import org.springframework.data.jpa.repository.JpaRepository;

public interface GateMachineRepository extends JpaRepository<GateMachine, Long> {
}