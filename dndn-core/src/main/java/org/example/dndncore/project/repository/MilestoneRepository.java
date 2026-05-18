package org.example.dndncore.project.repository;

import org.example.dndncore.project.model.entity.Milestone;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Collection;

public interface MilestoneRepository extends JpaRepository<Milestone, Long> {

    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("delete from Milestone m where m.tradeProcess.idx in :tradeProcessIds")
    int deleteByTradeProcessIds(@Param("tradeProcessIds") Collection<Long> tradeProcessIds);
}
