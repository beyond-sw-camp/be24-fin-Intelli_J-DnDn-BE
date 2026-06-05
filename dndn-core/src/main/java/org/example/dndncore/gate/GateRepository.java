package org.example.dndncore.gate;

import org.example.dndncore.gate.model.Gate;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface GateRepository extends JpaRepository<Gate, Long> {

    List<Gate> findByProject_IdxOrderByIdxAsc(Long projectId);

    List<Gate> findByProjectIsNullOrderByIdxAsc();
}
