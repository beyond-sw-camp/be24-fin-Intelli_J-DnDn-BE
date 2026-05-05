package org.example.dndn.staffing.repository;

import org.example.dndn.staffing.model.ZoneSub;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface ZoneSubRepository extends JpaRepository<ZoneSub, Long> {

    // STAFFING_004 — 상위 ZoneMain 과 직종 소요(trade_need)·투입(staffing_assignment) 묶음 로드
    // ZoneMain · trade_need 즉시 로드. staffing_assignment 는 LAZY 후 BatchSize 배치 로딩
    @EntityGraph(attributePaths = {"zoneMain", "tradeNeeds"})
    @Query("SELECT zs FROM ZoneSub zs WHERE zs.idx = :id")
    Optional<ZoneSub> findWithStaffingRelationsByIdx(@Param("id") Long id);

    /** STAFFING_001 자동 배치 — 표시 순 정렬. zoneMain·tradeNeeds만 그래프(fetch join); assignments는 List bag 이라 동시 fetch 불가 → 지연 로딩 + {@link org.hibernate.annotations.BatchSize} 배치 로드. */
    @EntityGraph(attributePaths = {"zoneMain", "tradeNeeds"})
    @Query("SELECT zs FROM ZoneSub zs JOIN zs.zoneMain zm ORDER BY zm.displayOrder ASC, zs.displayOrder ASC, zs.idx ASC")
    List<ZoneSub> findAllOrderedWithStaffingGraph();
}
