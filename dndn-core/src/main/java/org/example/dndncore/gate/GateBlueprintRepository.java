package org.example.dndncore.gate;

import org.example.dndncore.gate.model.GateBlueprint;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface GateBlueprintRepository extends JpaRepository<GateBlueprint, Long> {

    Optional<GateBlueprint> findByProject_Idx(Long projectId);
}
