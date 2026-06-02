package org.example.dndncore.project.service;

import lombok.RequiredArgsConstructor;
import org.example.dndncore.ai.extractor.ScheduleDocumentExtractor;
import org.example.dndncore.analysis.ScheduleChangeRepository;
import org.example.dndncore.auth.security.AuthAccessService;
import org.example.dndncore.project.model.dto.MasterScheduleDto;
import org.example.dndncore.project.model.dto.TradeProcessDto;
import org.example.dndncore.project.model.entity.MasterSchedule;
import org.example.dndncore.project.model.entity.Project;
import org.example.dndncore.project.model.entity.TradeProcess;
import org.example.dndncore.project.model.enums.DocType;
import org.example.dndncore.project.repository.MasterScheduleRepository;
import org.example.dndncore.project.repository.MilestoneRepository;
import org.example.dndncore.project.repository.ProjectRepository;
import org.example.dndncore.project.repository.ScheduleAiAnalysisRepository;
import org.example.dndncore.project.repository.TradeProcessRepository;
import org.example.dndncore.workplan.WorkPlanRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class MasterScheduleService {
    private final ScheduleDocumentExtractor scheduleDocumentExtractor;
    private final TradeProcessRepository tradeProcessRepository;
    private final MasterScheduleRepository masterScheduleRepository;
    private final ProjectRepository projectRepository;
    private final MilestoneRepository milestoneRepository;
    private final ScheduleAiAnalysisRepository scheduleAiAnalysisRepository;
    private final WorkPlanRepository workPlanRepository;
    private final ScheduleChangeRepository scheduleChangeRepository;
    private final AuthAccessService authAccessService;

    @Transactional
    public Long create(MasterScheduleDto.Req dto) {
        authAccessService.assertProjectWriteAccess(dto.getProjectId());

        Project project = projectRepository.findById(dto.getProjectId())
                .orElseThrow(() -> new RuntimeException("현장을 찾을 수 없습니다."));

        DocType docType = DocType.fromLabel(dto.getDocType());
        if (docType == null) {
            throw new RuntimeException("알 수 없는 공정표 종류입니다: " + dto.getDocType());
        }

        MasterSchedule schedule = MasterSchedule.builder()
                .project(project)
                .docType(docType)
                .fileUrl(dto.getFileUrl())
                .fileName(dto.getFileName())
                .build();

        return masterScheduleRepository.save(schedule).getIdx();
    }

    public MasterScheduleDto.Res read(Long scheduleId) {
        MasterSchedule schedule = findSchedule(scheduleId);
        authAccessService.assertProjectAccess(schedule.getProject() != null ? schedule.getProject().getIdx() : null);
        return MasterScheduleDto.Res.from(schedule);
    }

    public List<MasterScheduleDto.Res> listByProject(Long projectId, String docTypeLabel) {
        authAccessService.assertProjectAccess(projectId);

        DocType docType = DocType.fromLabel(docTypeLabel);

        List<MasterSchedule> schedules = (docType == null)
                ? masterScheduleRepository.findAllByProject_Idx(projectId)
                : masterScheduleRepository.findAllByProject_IdxAndDocType(projectId, docType);

        return schedules.stream().map(MasterScheduleDto.Res::from).toList();
    }

    @Transactional
    public void delete(Long scheduleId) {
        MasterSchedule schedule = findSchedule(scheduleId);
        authAccessService.assertProjectWriteAccess(schedule.getProject() != null ? schedule.getProject().getIdx() : null);
        masterScheduleRepository.delete(schedule);
    }

    private MasterSchedule findSchedule(Long scheduleId) {
        return masterScheduleRepository.findById(scheduleId)
                .orElseThrow(() -> new RuntimeException("공정표를 찾을 수 없습니다."));
    }

    // 새로 추가
    @Transactional
    public List<TradeProcessDto.Res> uploadAndExtract(
            Long projectId,
            String docTypeLabel,
            MultipartFile file
    ) {
        authAccessService.assertProjectWriteAccess(projectId);

        Project project = projectRepository.findById(projectId)
                .orElseThrow(() -> new RuntimeException("현장을 찾을 수 없습니다."));

        DocType docType = DocType.fromLabel(docTypeLabel);
        if (docType == null) {
            throw new RuntimeException("알 수 없는 공정표 종류입니다: " + docTypeLabel);
        }

        if (file == null || file.isEmpty()) {
            throw new RuntimeException("업로드된 파일이 없습니다.");
        }

        try {
            String uploadDir = System.getProperty("user.dir") + "/uploads/master-schedule/";
            Path uploadPath = Paths.get(uploadDir).toAbsolutePath().normalize();

            Files.createDirectories(uploadPath);

            String originalFileName = file.getOriginalFilename();
            String storedFileName = UUID.randomUUID() + "_" + originalFileName;

            Path filePath = uploadPath.resolve(storedFileName).normalize();

            file.transferTo(filePath.toFile());

            replacePreviousExtraction(projectId, docType);

            MasterSchedule schedule = MasterSchedule.builder()
                    .project(project)
                    .docType(docType)
                    .fileUrl(filePath.toString())
                    .fileName(originalFileName)
                    .build();

            MasterSchedule savedSchedule = masterScheduleRepository.save(schedule);

            List<TradeProcessDto.Req> extractedList =
                    scheduleDocumentExtractor.extract(filePath.toFile(), savedSchedule.getIdx());

            List<TradeProcess> tradeProcesses = extractedList.stream()
                    .map(dto -> TradeProcess.builder()
                            .masterSchedule(savedSchedule)
                            .tradeName(dto.getTradeName())
                            .processName(dto.getProcessName())
                            .partnerCompany(dto.getPartnerCompany())
                            .plannedStart(dto.getPlannedStart())
                            .plannedEnd(dto.getPlannedEnd())
                            .weightPct(dto.getWeightPct())
                            .isMilestone(dto.getIsMilestone() != null ? dto.getIsMilestone() : false)
                            .build())
                    .toList();

            List<TradeProcess> savedTradeProcesses =
                    tradeProcessRepository.saveAll(tradeProcesses);

            return savedTradeProcesses.stream()
                    .map(TradeProcessDto.Res::from)
                    .toList();

        } catch (Exception e) {
            throw new RuntimeException("공정표 업로드 및 AI 분석 중 오류가 발생했습니다.", e);
        }
    }

    private void replacePreviousExtraction(Long projectId, DocType docType) {
        List<MasterSchedule> previousSchedules = docType == DocType.MASTER
                ? masterScheduleRepository.findAllByProject_Idx(projectId)
                : masterScheduleRepository.findAllByProject_IdxAndDocType(projectId, docType);

        if (previousSchedules.isEmpty()) {
            return;
        }

        List<Long> scheduleIds = previousSchedules.stream()
                .map(MasterSchedule::getIdx)
                .toList();

        List<TradeProcess> previousTradeProcesses =
                tradeProcessRepository.findAllByMasterSchedule_IdxIn(scheduleIds);
        List<Long> tradeProcessIds = previousTradeProcesses.stream()
                .map(TradeProcess::getIdx)
                .toList();

        if (!tradeProcessIds.isEmpty()) {
            workPlanRepository.clearTradeProcessByIds(tradeProcessIds);
            scheduleChangeRepository.clearTradeProcessByIds(tradeProcessIds);
            milestoneRepository.deleteByTradeProcessIds(tradeProcessIds);
            tradeProcessRepository.deleteByMasterScheduleIds(scheduleIds);
        }

        scheduleAiAnalysisRepository.deleteByMasterScheduleIds(scheduleIds);
        masterScheduleRepository.deleteByIds(scheduleIds);
    }
}
