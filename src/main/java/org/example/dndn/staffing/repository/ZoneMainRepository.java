package org.example.dndn.staffing.repository;

import org.example.dndn.staffing.model.ZoneMain;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface ZoneMainRepository extends JpaRepository<ZoneMain, Long> {

    // STAFFING_003 — 표시 순서대로 ZoneMain + ZoneSub 전체 로드 (현장 필터 없음)
    @EntityGraph(attributePaths = {"zoneSubs"})
    List<ZoneMain> findAllByOrderByDisplayOrderAsc();

    // STAFFING_003 — 현장 코드(project.name LIKE %[siteCode]%) 기준 필터
    @EntityGraph(attributePaths = {"zoneSubs"})
    List<ZoneMain> findAllByProject_NameContainingOrderByDisplayOrderAsc(String siteCodeFragment);

    Optional<ZoneMain> findBySourceKey(String sourceKey);
}
