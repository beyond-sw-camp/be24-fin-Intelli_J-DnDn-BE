package org.example.dndn.staffing.repository;

import org.example.dndn.staffing.model.ZoneMain;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface ZoneMainRepository extends JpaRepository<ZoneMain, Long> {

    // STAFFING_003 — 표시 순서대로 ZoneMain 과 소속 ZoneSub 를 한 번에 로드
    @EntityGraph(attributePaths = {"zoneSubs"})
    List<ZoneMain> findAllByOrderByDisplayOrderAsc();

    Optional<ZoneMain> findBySourceKey(String sourceKey);
}
