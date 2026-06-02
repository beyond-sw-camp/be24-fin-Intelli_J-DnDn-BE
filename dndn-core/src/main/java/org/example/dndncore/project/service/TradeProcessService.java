package org.example.dndncore.project.service;

import lombok.RequiredArgsConstructor;
import org.example.dndncore.auth.security.AuthAccessService;
import org.example.dndncore.project.model.entity.MasterSchedule;
import org.example.dndncore.project.model.entity.TradeProcess;
import org.example.dndncore.project.model.dto.TradeProcessDto;
import org.example.dndncore.project.repository.MasterScheduleRepository;
import org.example.dndncore.project.repository.TradeProcessRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.example.dndncore.ai.extractor.ScheduleDocumentExtractor;
import java.io.File;

import java.util.List;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class TradeProcessService {

    private final TradeProcessRepository tradeProcessRepository;
    private final MasterScheduleRepository masterScheduleRepository;
    private final ScheduleDocumentExtractor scheduleDocumentExtractor;
    private final AuthAccessService authAccessService;

    @Transactional
    public Long create(TradeProcessDto.Req dto) {
        MasterSchedule schedule = masterScheduleRepository.findById(dto.getMasterScheduleId())
                .orElseThrow(() -> new RuntimeException("공정표를 찾을 수 없습니다."));

        authAccessService.assertProjectWriteAccess(schedule.getProject() != null ? schedule.getProject().getIdx() : null);
        authAccessService.assertTradeAccess(dto.getTradeName());

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

    @Transactional
    public List<Long> createAll(Long masterScheduleId, List<TradeProcessDto.Req> dtoList) {
        if (dtoList == null || dtoList.isEmpty()) {
            return List.of();
        }

        MasterSchedule schedule = masterScheduleRepository.findById(masterScheduleId)
                .orElseThrow(() -> new RuntimeException("공정표를 찾을 수 없습니다."));

        authAccessService.assertProjectWriteAccess(schedule.getProject() != null ? schedule.getProject().getIdx() : null);

        List<TradeProcess> entities = dtoList.stream()
                .peek(dto -> authAccessService.assertTradeAccess(dto.getTradeName()))
                .map(dto -> TradeProcess.builder()
                        .masterSchedule(schedule)
                        .tradeName(dto.getTradeName())
                        .processName(dto.getProcessName())
                        .partnerCompany(dto.getPartnerCompany())
                        .plannedStart(dto.getPlannedStart())
                        .plannedEnd(dto.getPlannedEnd())
                        .weightPct(dto.getWeightPct())
                        .isMilestone(dto.getIsMilestone() != null ? dto.getIsMilestone() : false)
                        .build())
                .toList();

        return tradeProcessRepository.saveAll(entities)
                .stream()
                .map(TradeProcess::getIdx)
                .toList();
    }

    @Transactional
    public List<Long> analyzeAndSave(Long masterScheduleId, File file) {
        List<TradeProcessDto.Req> extractedList =
                scheduleDocumentExtractor.extract(file, masterScheduleId);

        return createAll(masterScheduleId, extractedList);
    }

    public TradeProcessDto.Res read(Long tpId) {
        TradeProcess tradeProcess = findTradeProcess(tpId);
        authAccessService.assertTradeProcessAccess(tradeProcess);
        return TradeProcessDto.Res.from(tradeProcess);
    }

    // 현장별 공정 목록 (공종 필터 선택) — WorkPlan 등록 시 선택용으로도 사용
    public List<TradeProcessDto.Res> listByProject(Long projectId, String tradeName) {
        return listByProject(projectId, tradeName, false);
    }

    public List<TradeProcessDto.Res> listByProject(Long projectId, String tradeName, boolean includeAllTrades) {
        authAccessService.assertProjectAccess(projectId);
        String effectiveTrade = includeAllTrades ? tradeName : authAccessService.effectiveTrade(tradeName);
        List<TradeProcess> list = tradeProcessRepository.findAllByMasterSchedule_Project_Idx(projectId);

        return list.stream()
                .filter(tp -> authAccessService.tradeMatches(tp.getTradeName(), effectiveTrade))
                .filter(tp -> includeAllTrades || authAccessService.canAccessTradeProcess(tp))
                .map(TradeProcessDto.Res::from)
                .toList();
    }

    // 공정표별 공정 목록
    public List<TradeProcessDto.Res> listBySchedule(Long scheduleId) {
        return listBySchedule(scheduleId, false);
    }

    public List<TradeProcessDto.Res> listBySchedule(Long scheduleId, boolean includeAllTrades) {
        MasterSchedule schedule = masterScheduleRepository.findById(scheduleId)
                .orElseThrow(() -> new RuntimeException("怨듭젙?쒕? 李얠쓣 ???놁뒿?덈떎."));
        authAccessService.assertProjectAccess(schedule.getProject() != null ? schedule.getProject().getIdx() : null);

        return tradeProcessRepository.findAllByMasterSchedule_Idx(scheduleId).stream()
                .filter(tp -> includeAllTrades || authAccessService.canAccessTradeProcess(tp))
                .map(TradeProcessDto.Res::from)
                .toList();
    }

    /**
     * 계정 생성 시 공종 드롭다운 전용.
     * 현장(projectId) 기준으로 master_schedule → trade_process 를 조회하여
     * isMilestone = true 이고 '준공', '착공' 을 제외한 공종명 목록을 반환.
     */
    public List<String> listMilestoneTradeNamesByProject(Long projectId) {
        authAccessService.assertProjectAccess(projectId);
        return tradeProcessRepository.findMilestoneTradeNamesByProjectId(projectId).stream()
                .filter(name -> !org.example.dndncore.staffing.model.StaffingTradeMatcher.isExcludedAccountTradeName(name))
                .filter(authAccessService::canAccessTradeName)
                .toList();
    }

    @Transactional
    public void update(Long tpId, TradeProcessDto.Req dto) {
        TradeProcess tradeProcess = findTradeProcess(tpId);
        authAccessService.assertTradeProcessWriteAccess(tradeProcess);
        authAccessService.assertTradeAccess(dto.getTradeName());
        tradeProcess.update(
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
        TradeProcess tradeProcess = findTradeProcess(tpId);
        authAccessService.assertTradeProcessWriteAccess(tradeProcess);
        tradeProcessRepository.delete(tradeProcess);
    }

    private TradeProcess findTradeProcess(Long tpId) {
        return tradeProcessRepository.findById(tpId)
                .orElseThrow(() -> new RuntimeException("공정을 찾을 수 없습니다."));
    }
}
