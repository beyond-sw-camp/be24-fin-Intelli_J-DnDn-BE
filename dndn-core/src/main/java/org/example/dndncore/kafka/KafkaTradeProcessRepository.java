package org.example.dndncore.kafka;

import org.example.dndncore.kafka.dto.KafkaTradeProcess;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Collection;
import java.util.List;

public interface KafkaTradeProcessRepository extends JpaRepository<KafkaTradeProcess, Long> {

    List<KafkaTradeProcess> findAllByKafkaProject_Idx(Long projectId);

    List<KafkaTradeProcess> findAllByKafkaProject_IdxAndTradeName(Long projectId, String tradeName);

    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("delete from KafkaTradeProcess tp where tp.kafkaProject.idx in :projectIdx")
    int deleteByMasterScheduleIdx(@Param("projectId") Collection<Long> projectId);

    /**
     * 현장(project) 에 연결된 master_schedule 의 trade_process 중
     * isMilestone = true 이고 '준공', '착공' 을 제외한 공종명(tradeName) 을 중복 없이 반환.
     * 계정 생성 시 공종 드롭다운 목록 전용.
     */
    @Query("SELECT DISTINCT t.tradeName FROM KafkaTradeProcess t " +
           "WHERE t.kafkaProject.idx = :projectId " +
           "AND t.isMilestone = true " +
           "AND t.tradeName NOT IN ('준공', '착공') " +
           "ORDER BY t.tradeName")
    List<String> findMilestoneTradeNamesByProjectId(@Param("projectId") Long projectId);
}
