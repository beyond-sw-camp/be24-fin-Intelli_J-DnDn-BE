package org.example.dndn.project.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import org.example.dndn.project.model.dto.MasterScheduleDto;
import org.example.dndn.project.model.dto.TradeProcessDto;
import org.example.dndn.project.model.entity.MasterSchedule;
import org.example.dndn.project.model.entity.Project;
import org.example.dndn.project.model.entity.TradeProcess;
import org.example.dndn.project.model.enums.DocType;
import org.example.dndn.project.repository.MasterScheduleRepository;
import org.example.dndn.project.repository.ProjectRepository;
import org.example.dndn.project.repository.TradeProcessRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class MasterScheduleService {

    private final MasterScheduleRepository masterScheduleRepository;
    private final TradeProcessRepository tradeProcessRepository;
    private final ProjectRepository projectRepository;
    private final PdfAnalysisService pdfAnalysisService;
    private final ObjectMapper objectMapper;

    // ─────────────────────────────────────────────────────────────
    // 기존 CRUD (파일 메타만 저장)
    // ─────────────────────────────────────────────────────────────

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

    // ─────────────────────────────────────────────────────────────
    // PDF 업로드 → AI 분석 → MasterSchedule + TradeProcess 저장
    // ─────────────────────────────────────────────────────────────

    /**
     * PDF 파일을 업로드하여 AI 분석 후 저장합니다.
     *
     * 처리 흐름:
     *  1. MasterSchedule 저장 (파일 메타 + docType)
     *  2. AI로 PDF 내용 분석 → TradeProcess 목록 추출
     *  3. TradeProcess 일괄 저장
     *
     * @param projectId 현장 ID
     * @param docTypeLabel "마스터 공정표" | "마일스톤 공정표" | "보할 공정표" | "공종별 시공계획서"
     * @param file 업로드된 PDF
     * @param fileUrl S3 등 스토리지에 저장된 URL (프론트에서 전달)
     * @return 저장된 MasterSchedule idx
     */
    @Transactional
    public MasterScheduleUploadResult uploadAndAnalyze(
            Long projectId,
            String docTypeLabel,
            MultipartFile file,
            String fileUrl) throws Exception {

        Project project = projectRepository.findById(projectId)
                .orElseThrow(() -> new RuntimeException("현장을 찾을 수 없습니다."));

        DocType docType = DocType.fromLabel(docTypeLabel);
        if (docType == null) {
            throw new RuntimeException("알 수 없는 공정표 종류입니다: " + docTypeLabel);
        }

        // 1. MasterSchedule 저장
        MasterSchedule schedule = MasterSchedule.builder()
                .project(project)
                .docType(docType)
                .fileUrl(fileUrl != null ? fileUrl : file.getOriginalFilename())
                .fileName(file.getOriginalFilename())
                .build();
        MasterSchedule savedSchedule = masterScheduleRepository.save(schedule);

        // 2. AI 분석
        JsonNode resultArray = pdfAnalysisService.analyzeSingleDocument(file, docType);

        // 3. TradeProcess 저장
        List<TradeProcess> savedProcesses = new ArrayList<>();
        for (JsonNode node : resultArray) {
            TradeProcess tp = parseTradeProcess(node, savedSchedule);
            savedProcesses.add(tradeProcessRepository.save(tp));
        }

        return new MasterScheduleUploadResult(
                MasterScheduleDto.Res.from(savedSchedule),
                savedProcesses.stream().map(TradeProcessDto.Res::from).toList()
        );
    }

    // ─────────────────────────────────────────────────────────────
    // 내부 유틸
    // ─────────────────────────────────────────────────────────────

    private TradeProcess parseTradeProcess(JsonNode node, MasterSchedule schedule) {
        return TradeProcess.builder()
                .masterSchedule(schedule)
                .tradeName(textOrNull(node, "tradeName"))
                .processName(textOrNull(node, "processName"))
                .partnerCompany(textOrNull(node, "partnerCompany"))
                .plannedStart(dateOrNull(node, "plannedStart"))
                .plannedEnd(dateOrNull(node, "plannedEnd"))
                .weightPct(floatOrNull(node, "weightPct"))
                .isMilestone(node.path("isMilestone").asBoolean(false))
                .build();
    }

    private String textOrNull(JsonNode node, String field) {
        JsonNode n = node.path(field);
        return n.isNull() || n.isMissingNode() ? null : n.asText();
    }

    private LocalDate dateOrNull(JsonNode node, String field) {
        String text = textOrNull(node, field);
        if (text == null || text.isBlank()) return null;
        try {
            return LocalDate.parse(text);
        } catch (Exception e) {
            return null;
        }
    }

    private Float floatOrNull(JsonNode node, String field) {
        JsonNode n = node.path(field);
        if (n.isNull() || n.isMissingNode()) return null;
        return (float) n.asDouble();
    }

    private MasterSchedule findSchedule(Long scheduleId) {
        return masterScheduleRepository.findById(scheduleId)
                .orElseThrow(() -> new RuntimeException("공정표를 찾을 수 없습니다."));
    }

    // ─────────────────────────────────────────────────────────────
    // 결과 DTO
    // ─────────────────────────────────────────────────────────────

    public record MasterScheduleUploadResult(
            MasterScheduleDto.Res schedule,
            List<TradeProcessDto.Res> tradeProcesses
    ) {}
}
