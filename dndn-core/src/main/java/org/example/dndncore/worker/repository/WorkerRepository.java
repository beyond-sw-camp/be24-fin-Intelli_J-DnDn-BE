package org.example.dndncore.worker.repository;

import org.example.dndncore.worker.model.entity.Worker;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;


public interface WorkerRepository extends JpaRepository<Worker, Long> {

    // MANAGEMENT_002 작업자 검색 — 이름·현장코드 기준 (siteCode null/빈값이면 현장 무관)
    @Query("""
        select w from Worker w
        where (:name is null or :name = '' or w.name like concat('%', :name, '%'))
          and (:siteCode is null or :siteCode = '' or w.siteCode = :siteCode)
        order by w.name asc
    """)
    List<Worker> search(@Param("name") String name, @Param("siteCode") String siteCode);

    List<Worker> findAllBySiteCode(String siteCode);

    /**
     * 모바일 로그인 — 이름 + 전화번호 매칭.
     * 전화번호는 하이픈 유무 두 가지 형태를 모두 허용한다.
     * 더미데이터는 externalCode 기반으로 전화번호를 생성하므로 전체 현장에 걸쳐 유일하다.
     */
    @Query("""
        select w from Worker w
        where w.name = :name
          and (w.phone = :phone or w.phone = :phoneDigits)
    """)
    Optional<Worker> findByNameAndPhone(
            @Param("name") String name,
            @Param("phone") String phone,
            @Param("phoneDigits") String phoneDigits);
}
