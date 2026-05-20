package org.example.dndncore.analysis;

import org.example.dndncore.analysis.model.ScheduleChange;
import org.example.dndncore.analysis.model.ScheduleChangeStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Collection;
import java.util.List;

public interface ScheduleChangeRepository extends JpaRepository<ScheduleChange, Long> {

    // 현장별 전체 목록 (총 책임자 뷰)
    List<ScheduleChange> findAllByProject_IdxOrderByCreatedAtDesc(Long projectId);

    List<ScheduleChange> findAllByProject_IdxAndStatusInOrderByCreatedAtDesc(
            Long projectId, List<ScheduleChangeStatus> statuses);

    // 현장 + 공종 필터 (총 책임자 공종 필터)
    List<ScheduleChange> findAllByProject_IdxAndProcessOrderByCreatedAtDesc(Long projectId, String process);

    List<ScheduleChange> findAllByProject_IdxAndProcessAndStatusInOrderByCreatedAtDesc(
            Long projectId, String process, List<ScheduleChangeStatus> statuses);

    // 현장 + 공종 + 상태 필터
    List<ScheduleChange> findAllByProject_IdxAndProcessAndStatusOrderByCreatedAtDesc(
            Long projectId, String process, ScheduleChangeStatus status);

    // 공정 책임자 본인 요청 목록 (requester 기준)
    List<ScheduleChange> findAllByProject_IdxAndProcessAndRequesterOrderByCreatedAtDesc(
            Long projectId, String process, String requester);

    List<ScheduleChange> findAllByProject_IdxAndProcessAndRequesterAndStatusInOrderByCreatedAtDesc(
            Long projectId, String process, String requester, List<ScheduleChangeStatus> statuses);

    // 이력 조회 (처리 완료된 것만)
    List<ScheduleChange> findAllByProject_IdxAndStatusInOrderByProcessedAtDesc(
            Long projectId, List<ScheduleChangeStatus> statuses);

    // 공종 필터 + 이력
    List<ScheduleChange> findAllByProject_IdxAndProcessAndStatusInOrderByProcessedAtDesc(
            Long projectId, String process, List<ScheduleChangeStatus> statuses);

    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("update ScheduleChange sc set sc.tradeProcess = null where sc.tradeProcess.idx in :tradeProcessIds")
    int clearTradeProcessByIds(@Param("tradeProcessIds") Collection<Long> tradeProcessIds);
}
