package org.example.dndncore.staffing.repository;

import org.example.dndncore.staffing.model.ZoneMain;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Collection;
import java.util.List;

public interface ZoneMainRepository extends JpaRepository<ZoneMain, Long> {

    // STAFFING_003 — 표시 순서대로 ZoneMain + ZoneSub 전체 로드 (현장 필터 없음)
    @EntityGraph(attributePaths = {"zoneSubs"})
    List<ZoneMain> findAllByOrderByDisplayOrderAsc();

    // STAFFING_003 — 현장 코드(project.name LIKE %[siteCode]%) 기준 필터
    @EntityGraph(attributePaths = {"zoneSubs"})
    List<ZoneMain> findAllByProject_NameContainingOrderByDisplayOrderAsc(String siteCodeFragment);

    /** sync 루프 전 일괄 선조회 — sourceKey IN (...) 1번으로 K번 개별 SELECT 대체 */
    List<ZoneMain> findAllBySourceKeyIn(Collection<String> sourceKeys);
}
