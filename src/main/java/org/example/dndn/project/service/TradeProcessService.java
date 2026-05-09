package org.example.dndn.project.service;

import lombok.RequiredArgsConstructor;
import org.example.dndn.project.model.entity.MasterSchedule;
import org.example.dndn.project.model.entity.TradeProcess;
import org.example.dndn.project.model.dto.TradeProcessDto;
import org.example.dndn.project.repository.MasterScheduleRepository;
import org.example.dndn.project.repository.TradeProcessRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class TradeProcessService {

    private final TradeProcessRepository tradeProcessRepository;
    private final MasterScheduleRepository masterScheduleRepository;

    @Transactional
    public Long create(TradeProcessDto.Req dto) {
        MasterSchedule schedule = masterScheduleRepository.findById(dto.getMasterScheduleId())
                .orElseThrow(() -> new RuntimeException("공정표를 찾을 수 없습니다."));

        TradeProcess tp = TradeProcess.builder()
                .masterSchedule(schedule)
                .tradeName(dto.getTradeName())
                .processName(dto.getProcessName())
                .partnerCompany(dto.getPartnerCompany())
                .plannedStart(dto.getPlannedStart())
                .plannedEnd(dto.getPlannedEnd())
                .weightPct(dto.getWeightPct())
                .isMilestone(dto.getIsMilestone() != null ? dto.getIsMilestone() : false)
                .build();

        return tradeProcessRepository.save(tp).getIdx();
    }

    public TradeProcessDto.Res read(Long tpId) {
        return TradeProcessDto.Res.from(findTradeProcess(tpId));
    }

    // 현장별 공정 목록 (공종 필터 선택) — WorkPlan 등록 시 선택용으로도 사용
    public List<TradeProcessDto.Res> listByProject(Long projectId, String tradeName) {
        List<TradeProcess> list = (tradeName == null || tradeName.isBlank())
                ? tradeProcessRepository.findAllByMasterSchedule_Project_Idx(projectId)
                : tradeProcessRepository.findAllByMasterSchedule_Project_IdxAndTradeName(projectId, tradeName);

        return list.stream().map(TradeProcessDto.Res::from).toList();
    }

    // 공정표별 공정 목록
    public List<TradeProcessDto.Res> listBySchedule(Long scheduleId) {
        return tradeProcessRepository.findAllByMasterSchedule_Idx(scheduleId).stream()
                .map(TradeProcessDto.Res::from)
                .toList();
    }

    /**
     * 계정 생성 시 공종 드롭다운 전용.
     * 현장(projectId) 기준으로 master_schedule → trade_process 를 조회하여
     * isMilestone = true 이고 '준공', '착공' 을 제외한 공종명 목록을 반환.
     */
    public List<String> listMilestoneTradeNamesByProject(Long projectId) {
        return tradeProcessRepository.findMilestoneTradeNamesByProjectId(projectId);
    }

    @Transactional
    public void update(Long tpId, TradeProcessDto.Req dto) {
        findTradeProcess(tpId).update(
                dto.getTradeName(),
                dto.getProcessName(),
                dto.getPartnerCompany(),
                dto.getPlannedStart(),
                dto.getPlannedEnd(),
                dto.getWeightPct(),
                dto.getIsMilestone()
        );
    }

    @Transactional
    public void delete(Long tpId) {
        tradeProcessRepository.delete(findTradeProcess(tpId));
    }

    private TradeProcess findTradeProcess(Long tpId) {
        return tradeProcessRepository.findById(tpId)
                .orElseThrow(() -> new RuntimeException("공정을 찾을 수 없습니다."));
    }
}