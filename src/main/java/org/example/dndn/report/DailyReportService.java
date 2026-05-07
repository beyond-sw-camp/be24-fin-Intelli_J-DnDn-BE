package org.example.dndn.report;

import lombok.RequiredArgsConstructor;
import org.example.dndn.report.model.DailyReport;
import org.example.dndn.report.model.ReportDto;
import org.example.dndn.workplan.WorkPlanRepository;
import org.example.dndn.workplan.model.entity.WorkPlan;
import org.example.dndn.workplan.model.entity.WorkPlanEquipment;
import org.example.dndn.workplan.model.entity.WorkPlanExtension;
import org.example.dndn.workplan.model.entity.WorkPlanWorker;
import org.example.dndn.workplan.model.enums.EquipmentType;
import org.example.dndn.workplan.model.enums.WorkerTrade;
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

    // feat : 공사일보 제출 및 명일 작업계획 자동 연동
    public Long submitReport(ReportDto.Req dto) {

        // feat : 공사일보 DB 조회 및 저장
        WorkPlan workPlan = workPlanRepository.findById(dto.getWorkPlanId())
                .orElseThrow(() -> new RuntimeException("WorkPlan not found"));

        DailyReport dailyReport = dailyReportRepository.findByWorkPlan_IdxAndReportDate(workPlan.getIdx(), dto.getReportDate())
                .orElse(DailyReport.builder()
                        .workPlan(workPlan)
                        .reportDate(dto.getReportDate())
                        .build());

        // feat : 금일 진척률을 포함하여 공사일보 정보 업데이트
        dailyReport.updateReport(dto.getActualProgress(), dto.getTodayProgress(), dto.getActualWorkerCount(), dto.getIssue(), dto.getTodayWork(), dto.getTomorrowPlan());
        DailyReport savedReport = dailyReportRepository.save(dailyReport);

        // feat : 진척률 100% 미달 시 공정 기간 자동 연장
        if (dto.getActualProgress() < 100.0) {
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

        return savedReport.getIdx();
    }

    // feat : 특정 일자 공사일보 목록 조회 및 DTO 변환
    @Transactional(readOnly = true)
    public List<ReportDto.Res> getReportsByDate(LocalDate date) {
        return dailyReportRepository.findByReportDate(date).stream().map(r ->
                ReportDto.Res.builder()
                        .idx(r.getIdx())
                        .workPlanId(r.getWorkPlan().getIdx())
                        .process(r.getWorkPlan().getTrade() != null ? r.getWorkPlan().getTrade().getLabel() : "")
                        .actualProgress(r.getActualProgress())
                        .todayProgress(r.getTodayProgress())
                        .actualWorkerCount(r.getActualWorkerCount())
                        .issue(r.getIssue())
                        .reportDate(r.getReportDate())
                        .todayWork(r.getTodayWork())
                        .tomorrowPlan(r.getTomorrowPlan())
                        .build()
        ).collect(Collectors.toList());
    }
}