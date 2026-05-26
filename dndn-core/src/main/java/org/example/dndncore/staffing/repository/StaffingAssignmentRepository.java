package org.example.dndncore.staffing.repository;

import org.example.dndncore.staffing.model.StaffingAssignment;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDate;
import java.util.Collection;
import java.util.List;

public interface StaffingAssignmentRepository extends JpaRepository<StaffingAssignment, Long> {

    @Query("""
            select distinct a from StaffingAssignment a
                join fetch a.zoneSub zs
                join fetch zs.zoneMain zm
            where zs.idx = :zoneSubIdx
              and a.workDate = :workDate
            order by a.idx asc
            """)
    List<StaffingAssignment> findAllByZoneSubAndWorkDateWithHierarchy(
            @Param("zoneSubIdx") Long zoneSubIdx,
            @Param("workDate") LocalDate workDate);

    void deleteByZoneSub_IdxAndWorkerIdxAndWorkDate(Long zoneSubIdx, Long workerIdx, LocalDate workDate);

    boolean existsByZoneSub_IdxAndWorkerIdxAndWorkDate(Long zoneSubIdx, Long workerIdx, LocalDate workDate);

    boolean existsByWorkerIdxAndWorkDate(Long workerIdx, LocalDate workDate);

    int countByZoneSub_IdxAndWorkDate(Long zoneSubIdx, LocalDate workDate);

    /** STAFFING_008 우측 패널 — 명단(workerIdx 들) 기준 최초 배치 행을 찾을 때 로드(IN 비어 있으면 호출하지 말 것). */
    @Query("""
            select a from StaffingAssignment a
                join fetch a.zoneSub zs
                join fetch zs.zoneMain zm
            where a.workerIdx in :workerIdxes
              and a.workDate = :workDate
            order by a.workerIdx asc, a.idx asc
            """)
    List<StaffingAssignment> findAllWithZonesByWorkerIdxInAndWorkDate(
            @Param("workerIdxes") Collection<Long> workerIdxes,
            @Param("workDate") LocalDate workDate);

    @Query("""
            select a from StaffingAssignment a
                join fetch a.zoneSub zs
                join fetch zs.zoneMain zm
            where a.workDate = :workDate
            order by a.idx asc
            """)
    List<StaffingAssignment> findAllWithZoneHierarchyByWorkDateOrderByIdxAsc(
            @Param("workDate") LocalDate workDate);

    @Query("""
            select a from StaffingAssignment a
                join fetch a.zoneSub zs
                join fetch zs.zoneMain zm
            where a.workDate = :workDate
              and a.siteCode = :siteCode
            order by a.idx asc
            """)
    List<StaffingAssignment> findAllWithZoneHierarchyByWorkDateAndSiteCodeOrderByIdxAsc(
            @Param("workDate") LocalDate workDate,
            @Param("siteCode") String siteCode);

    void deleteAllByWorkDate(LocalDate workDate);

    void deleteAllByWorkDateAndSiteCode(LocalDate workDate, String siteCode);

    /** buildZoneMainResponses 용 — ZoneSub별 배치 수를 GROUP BY 1쿼리로 집계 (M번 COUNT 대체) */
    @Query("""
            select a.zoneSub.idx as zoneSubIdx, count(a) as cnt
            from StaffingAssignment a
            where a.zoneSub.idx in :zoneSubIdxes and a.workDate = :workDate
            group by a.zoneSub.idx
            """)
    List<ZoneSubCountProjection> countGroupedByZoneSubIdxAndWorkDate(
            @Param("zoneSubIdxes") Collection<Long> zoneSubIdxes,
            @Param("workDate") LocalDate workDate);

    /** STAFFING board — 구역 트리에 속한 배치 행 일괄 로드 */
    @Query("""
            select a from StaffingAssignment a
                join fetch a.zoneSub zs
                join fetch zs.zoneMain zm
            where a.workDate = :workDate
              and zs.idx in :zoneSubIdxes
            order by zm.displayOrder asc, zs.displayOrder asc, a.idx asc
            """)
    List<StaffingAssignment> findAllWithZoneHierarchyByWorkDateAndZoneSubIdxIn(
            @Param("workDate") LocalDate workDate,
            @Param("zoneSubIdxes") Collection<Long> zoneSubIdxes);

    interface ZoneSubCountProjection {
        Long getZoneSubIdx();
        Long getCnt();
    }
}
