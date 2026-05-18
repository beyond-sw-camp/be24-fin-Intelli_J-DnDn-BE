package org.example.dndncore.staffing.repository;

import org.example.dndncore.staffing.model.TradeNeed;
import org.springframework.data.jpa.repository.JpaRepository;

public interface TradeNeedRepository extends JpaRepository<TradeNeed, Long> {

    void deleteAllByZoneSub_Idx(Long zoneSubIdx);
}
