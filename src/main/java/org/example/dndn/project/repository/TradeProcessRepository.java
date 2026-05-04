package org.example.dndn.project.repository;

import org.example.dndn.project.model.entity.TradeProcess;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface TradeProcessRepository extends JpaRepository<TradeProcess, Long> {

    List<TradeProcess> findAllByMasterSchedule_Idx(Long masterScheduleId);

    List<TradeProcess> findAllByMasterSchedule_Project_Idx(Long projectId);

    List<TradeProcess> findAllByMasterSchedule_Project_IdxAndTradeName(Long projectId, String tradeName);
}