package org.example.dndn.project.service;

import lombok.RequiredArgsConstructor;
import org.example.dndn.project.model.dto.MasterScheduleDto;
import org.example.dndn.project.model.entity.MasterSchedule;
import org.example.dndn.project.model.entity.Project;
import org.example.dndn.project.model.enums.DocType;
import org.example.dndn.project.repository.MasterScheduleRepository;
import org.example.dndn.project.repository.ProjectRepository;
import org.example.dndn.project.repository.TradeProcessRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.util.List;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class MasterScheduleService {
    private final TradeProcessRepository tradeProcessRepository;
    private final MasterScheduleRepository masterScheduleRepository;
    private final ProjectRepository projectRepository;

    @Transactional
    public Long create(MasterScheduleDto.Req dto) {
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
        return MasterScheduleDto.Res.from(findSchedule(scheduleId));
    }

    public List<MasterScheduleDto.Res> listByProject(Long projectId, String docTypeLabel) {
        DocType docType = DocType.fromLabel(docTypeLabel);

        List<MasterSchedule> schedules = (docType == null)
                ? masterScheduleRepository.findAllByProject_Idx(projectId)
                : masterScheduleRepository.findAllByProject_IdxAndDocType(projectId, docType);

        return schedules.stream().map(MasterScheduleDto.Res::from).toList();
    }

    @Transactional
    public void delete(Long scheduleId) {
        masterScheduleRepository.delete(findSchedule(scheduleId));
    }

    private MasterSchedule findSchedule(Long scheduleId) {
        return masterScheduleRepository.findById(scheduleId)
                .orElseThrow(() -> new RuntimeException("공정표를 찾을 수 없습니다."));
    }
} 