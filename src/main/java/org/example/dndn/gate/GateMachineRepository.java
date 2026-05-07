package org.example.dndn.gate;

import org.example.dndn.gate.model.GateMachine;
import org.springframework.data.jpa.repository.JpaRepository;

public interface GateMachineRepository extends JpaRepository<GateMachine, Long> {
}