package org.example.dndn.domain.project.repository;

import org.example.dndn.domain.project.model.entity.TradeProcess;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Collection;
import java.util.List;

public interface TradeProcessRepository extends JpaRepository<TradeProcess, Long> {

    List<TradeProcess> findAllByMasterSchedule_IdxIn(Collection<Long> masterScheduleIds);
}
