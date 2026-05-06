package org.example.dndn.staffing.repository;

import org.example.dndn.staffing.model.TradeNeed;
import org.springframework.data.jpa.repository.JpaRepository;

public interface TradeNeedRepository extends JpaRepository<TradeNeed, Long> {

    void deleteAllByZoneSub_Idx(Long zoneSubIdx);
}
