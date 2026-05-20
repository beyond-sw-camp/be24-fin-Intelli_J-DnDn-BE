package org.example.dndncore.worker.repository;

import org.example.dndncore.worker.model.entity.Worker;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

public interface WorkerRepository extends JpaRepository<Worker, Long> {
    /** sync 시 dedup key — 시연 JSON 의 externalCode 또는 향후 연동 식별자 */
    Optional<Worker> findByExternalCode(String externalCode);

    /** sync 루프 전 일괄 선조회 — IN 쿼리 1번으로 N번 개별 SELECT 대체 */
    @Query("select w from Worker w where w.externalCode in :codes")
    List<Worker> findAllByExternalCodeIn(@Param("codes") Collection<String> codes);

    // MANAGEMENT_002 작업자 검색 — 이름·현장코드 기준 (siteCode null/빈값이면 현장 무관)
    @Query("""
        select w from Worker w
        where (:name is null or :name = '' or w.name like concat('%', :name, '%'))
          and (:siteCode is null or :siteCode = '' or w.siteCode = :siteCode)
        order by w.name asc
    """)
    List<Worker> search(@Param("name") String name, @Param("siteCode") String siteCode);
}
