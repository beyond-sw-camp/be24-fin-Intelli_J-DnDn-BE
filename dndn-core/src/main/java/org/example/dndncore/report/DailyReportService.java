package org.example.dndncore.report;

import lombok.RequiredArgsConstructor;
import org.example.dndncore.auth.security.AuthAccessService;
import org.example.dndncore.esg.event.EsgSnapshotRefreshEventPublisher;
import org.example.dndncore.document_event.DocumentEventProducer;
import org.example.dndncore.report.model.DailyReport;
import org.example.dndncore.report.model.ReportDto;
import org.example.dndncore.workplan.WorkPlanRepository;
import org.example.dndncore.workplan.model.entity.WorkPlan;
import org.example.dndncore.workplan.model.entity.WorkPlanEquipment;
import org.example.dndncore.workplan.model.entity.WorkPlanExtension;
import org.example.dndncore.workplan.model.entity.WorkPlanWorker;
import org.example.dndncore.workplan.model.enums.EquipmentType;
import org.example.dndncore.workplan.model.enums.WorkerTrade;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

// feat : 공사일보 비즈니스 로직 서비스
@Service
@RequiredArgsConstructor
@Transactional
public class DailyReportService {

    private final DailyReportRepository dailyReportRepository;
    private final WorkPlanRepository workPlanRepository;
    private final AuthAccessService authAccessService;
    private final EsgSnapshotRefreshEventPublisher esgSnapshotRefreshEventPublisher;
    private final DocumentEventProducer documentEventProducer;

    // feat : 공사일보 제출 및 명일 작업계획 자동 연동
    public Long submitReport(ReportDto.Req dto) {

        // feat : 공사일보 DB 조회 및 저장
        WorkPlan workPlan = workPlanRepository.findById(dto.getWorkPlanId())
                .orElseThrow(() -> new RuntimeException("WorkPlan not found"));
        authAccessService.assertWorkPlanAccess(workPlan);

        DailyReport dailyReport = dailyReportRepository.findByWorkPlan_IdxAndReportDate(workPlan.getIdx(), dto.getReportDate())
                .orElse(DailyReport.builder()
                        .workPlan(workPlan)
                        .reportDate(dto.getReportDate())
                        .build());

        WorkPlan monthlyPlan = null;
        if (dto.getMonthlyWorkPlanId() != null) {
            monthlyPlan = workPlanRepository.findById(dto.getMonthlyWorkPlanId())
                    .orElseThrow(() -> new RuntimeException("월간 세부계획을 찾을 수 없습니다. id=" + dto.getMonthlyWorkPlanId()));
        }

        if (monthlyPlan != null) {
            authAccessService.assertWorkPlanAccess(monthlyPlan);
        }

        Double monthlyProgressPct = clampPercent(dto.getMonthlyProgressPct() != null
                ? dto.getMonthlyProgressPct()
                : dto.getActualProgress());
        Double progressIncrementPct = clampPercent(dto.getProgressIncrementPct() != null
                ? dto.getProgressIncrementPct()
                : 0.0);

        // feat : 금일 진척률과 월간 세부계획 누적 진척률을 포함하여 공사일보 정보 업데이트
        dailyReport.updateReport(
                monthlyPlan,
                monthlyProgressPct,
                dto.getTodayProgress(),
                progressIncrementPct,
                monthlyProgressPct,
                dto.getActualWorkerCount(),
                nonBlank(dto.getLocation(), workPlan.getLocation()),
                dto.getIssue(),
                dto.getTodayWork(),
                dto.getTomorrowPlan()
        );
        DailyReport savedReport = dailyReportRepository.save(dailyReport);

        // feat : 월간 세부계획 누적 진척률 갱신
        if (monthlyPlan != null) {
            monthlyPlan.updateActualProgressPct(monthlyProgressPct);
            workPlanRepository.save(monthlyPlan);
        }
        documentEventProducer.publishDailyReportChanged("DAILY_REPORT_SUBMITTED", savedReport);

        // feat : 금일 작업 진척률 100% 미달 시 해당 주간 계획 기간 자동 연장
        double todayProgress = dto.getTodayProgress() == null ? 0.0 : dto.getTodayProgress();
        if (todayProgress < 100.0) {
            WorkPlanExtension extension = workPlan.getExtension();
            if (extension == null) {
                extension = WorkPlanExtension.builder().build();
                workPlan.attachExtension(extension);
            }
            int addedDays = extension.getAddedDays() == null ? 1 : extension.getAddedDays() + 1;
            if (workPlan.getEndDate() != null) {
                LocalDate extendedEnd = workPlan.getEndDate().plusDays(addedDays);
                extension.update(extendedEnd, addedDays, dto.getIssue(), LocalDate.now());
            }
        }

        // feat : 명일 계획 내용이나 인원 또는 장비가 입력된 경우에만 주간 계획 연동 처리
        boolean hasTomorrowPlan = (dto.getTomorrowPlan() != null && !dto.getTomorrowPlan().isBlank())
                || (dto.getTomorrowWorkerCount() != null && dto.getTomorrowWorkerCount() > 0)
                || (dto.getTomorrowEquipments() != null && !dto.getTomorrowEquipments().isEmpty());

        // 복잡한 스케줄 추측 로직 다 지우고, 프론트에서 넘어온 대상 ID로 콕 집어서 덮어쓰기!
        if (hasTomorrowPlan && dto.getTomorrowWorkPlanId() != null) {

            WorkPlan tomorrowPlan = workPlanRepository.findById(dto.getTomorrowWorkPlanId())
                    .orElseThrow(() -> new RuntimeException("대상 주간계획을 찾을 수 없습니다."));

            // feat : 기존 주간 계획을 유지하되, '비고(note)'와 인원/장비만 명일 작업 예정으로 덮어씀
            authAccessService.assertWorkPlanAccess(tomorrowPlan);

            tomorrowPlan.updateInfo(
                    tomorrowPlan.getName(), tomorrowPlan.getTrade(), tomorrowPlan.getLocation(),
                    tomorrowPlan.getStartDate(), tomorrowPlan.getEndDate(), tomorrowPlan.getStatus(),
                    tomorrowPlan.getPartner(), tomorrowPlan.getManager(), tomorrowPlan.getContact(),
                    dto.getTomorrowPlan() // 화면에 입력한 '명일 작업 예정'을 note에 덮어쓰기
            );

            // feat : 명일 투입 예정 인원 설정
            if (dto.getTomorrowWorkerCount() != null && dto.getTomorrowWorkerCount() > 0) {
                WorkPlanWorker worker = WorkPlanWorker.builder()
                        .workPlan(tomorrowPlan)
                        .trade(WorkerTrade.COMMON)
                        .count(dto.getTomorrowWorkerCount())
                        .build();
                tomorrowPlan.replaceWorkers(List.of(worker));
            } else {
                tomorrowPlan.replaceWorkers(new ArrayList<>());
            }

            // feat : 명일 투입 예정 장비 설정
            if (dto.getTomorrowEquipments() != null && !dto.getTomorrowEquipments().isEmpty()) {
                List<WorkPlanEquipment> eqList = dto.getTomorrowEquipments().stream().map(eq -> {
                    EquipmentType type = null;
                    try {
                        type = EquipmentType.valueOf(eq.getType());
                    } catch (Exception e) {
                        try {
                            type = EquipmentType.fromLabel(eq.getType());
                        } catch (Exception ex) {
                            return null;
                        }
                    }
                    if (type == null) return null;

                    return WorkPlanEquipment.builder()
                            .workPlan(tomorrowPlan)
                            .type(type)
                            .count(eq.getCount())
                            .build();
                }).filter(java.util.Objects::nonNull).collect(Collectors.toList());
                tomorrowPlan.replaceEquipment(eqList);
            } else {
                tomorrowPlan.replaceEquipment(new ArrayList<>());
            }

            workPlanRepository.save(tomorrowPlan);
        }

        Long projectId = resolveProjectId(workPlan);
        esgSnapshotRefreshEventPublisher.publishProjectDate(projectId, savedReport.getReportDate());
        return savedReport.getIdx();
    }


    private Long resolveProjectId(WorkPlan workPlan) {
        if (workPlan == null
                || workPlan.getTradeProcess() == null
                || workPlan.getTradeProcess().getMasterSchedule() == null
                || workPlan.getTradeProcess().getMasterSchedule().getProject() == null) {
            return null;
        }

        return workPlan.getTradeProcess()
                .getMasterSchedule()
                .getProject()
                .getIdx();
    }

    private Double clampPercent(Double value) {
        if (value == null) return 0.0;
        return Math.max(0.0, Math.min(100.0, value));
    }

    private String nonBlank(String value, String fallback) {
        if (value != null && !value.isBlank()) return value;
        return fallback != null ? fallback : "";
    }

    // feat : 특정 일자 공사일보 목록 조회 및 DTO 변환
    @Transactional(readOnly = true)
    public List<ReportDto.Res> getReportsByDate(LocalDate date) {
        return dailyReportRepository.findByReportDate(date).stream()
                .filter(r -> authAccessService.canAccessWorkPlan(r.getWorkPlan()))
                .map(r ->
                        ReportDto.Res.builder()
                                .idx(r.getIdx())
                                .workPlanId(r.getWorkPlan().getIdx())
                                .process(authAccessService.workPlanTradeName(r.getWorkPlan()))
                                .actualProgress(r.getActualProgress())
                                .todayProgress(r.getTodayProgress())
                                .monthlyWorkPlanId(r.getMonthlyWorkPlan() != null ? r.getMonthlyWorkPlan().getIdx() : null)
                                .progressIncrementPct(r.getProgressIncrementPct())
                                .monthlyProgressPct(r.getMonthlyProgressPct())
                                .actualWorkerCount(r.getActualWorkerCount())
                                .location(nonBlank(r.getLocation(), r.getWorkPlan().getLocation()))
                                .issue(r.getIssue())
                                .reportDate(r.getReportDate())
                                .todayWork(r.getTodayWork())
                                .tomorrowPlan(r.getTomorrowPlan())
                                .build()
                ).collect(Collectors.toList());
    }
}