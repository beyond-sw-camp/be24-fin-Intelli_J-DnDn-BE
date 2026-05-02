package org.example.dndn.worker.repository;

import org.example.dndn.worker.model.entity.Worker;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface WorkerRepository extends JpaRepository<Worker, Long> {
    /** sync 시 dedup key — 시연 JSON 의 externalCode 또는 향후 연동 식별자 */
    Optional<Worker> findByExternalCode(String externalCode);

    // MANAGEMENT_003 작업자 목록 — 필터 없이 전체, 표시 순서 고정
    List<Worker> findAllByOrderByNameAsc();

    // MANAGEMENT_002 작업자 검색
    @Query("""
        select w from Worker w
        where (:partnerCompany is null or :partnerCompany = '' or w.partnerCompany like concat('%', :partnerCompany, '%'))
          and (:name is null or :name = '' or w.name like concat('%', :name, '%'))
        order by w.name asc
    """)
    List<Worker> search(
            @Param("partnerCompany") String partnerCompany,
            @Param("name") String name
    );
}
