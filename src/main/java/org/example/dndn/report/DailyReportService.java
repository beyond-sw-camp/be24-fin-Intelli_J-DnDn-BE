package org.example.dndn.report;

import lombok.RequiredArgsConstructor;
import org.example.dndn.report.model.DailyReport;
import org.example.dndn.report.model.ReportDto;
import org.example.dndn.workplan.WorkPlanRepository;
import org.example.dndn.workplan.model.*;
import org.example.dndn.workplan.model.entity.WorkPlan;
import org.example.dndn.workplan.model.entity.WorkPlanEquipment;
import org.example.dndn.workplan.model.entity.WorkPlanExtension;
import org.example.dndn.workplan.model.entity.WorkPlanWorker;
import org.example.dndn.workplan.model.enums.EquipmentType;
import org.example.dndn.workplan.model.enums.PlanStatus;
import org.example.dndn.workplan.model.enums.PlanType;
import org.example.dndn.workplan.model.enums.WorkerTrade;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.List;
import java.util.ArrayList;
import java.util.stream.Collectors;

// feat : 공사일보 비즈니스 로직을 처리하는 서비스 클래스
@Service
@RequiredArgsConstructor
@Transactional
public class DailyReportService {

    private final DailyReportRepository dailyReportRepository;
    private final WorkPlanRepository workPlanRepository;

    // [REPORT_003] 3단계 : 공사일보 제출(Upsert) 기본 로직 구현
    // feat : 공사일보 제출 및 명일 작업계획(WorkPlan) 달력 자동 연동 로직
    public Long submitReport(ReportDto.Req dto) {

        // feat : 1. 공사일보 DB 조회 및 저장 로직
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

        // [REPORT_004] 4단계 : 진척률 미달 시 공정 기간 자동 연장 기능
        // feat : 2. 진척률 100% 미달 시 원본 공정 기간 자동 연장 로직
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

        // [REPORT_005] 5단계 : 명일 작업 스케줄(WorkPlan) 기본 정보 자동 생성 기능
        // feat : 3. 명일 스케줄 달력 반영을 위한 1일짜리 예정 데이터 자동 생성
        LocalDate tomorrow = dto.getReportDate().plusDays(1);
        String tomorrowName = workPlan.getName() + " (명일 예정)";

        List<WorkPlan> existingPlans = workPlanRepository.findAllByPlanType(PlanType.WEEKLY);
        WorkPlan tomorrowPlan = existingPlans.stream()
                .filter(p -> p.getStartDate() != null && p.getStartDate().equals(tomorrow))
                .filter(p -> p.getTrade() == workPlan.getTrade())
                .filter(p -> p.getName() != null && p.getName().equals(tomorrowName))
                .findFirst()
                .orElse(WorkPlan.builder()
                        .name(tomorrowName)
                        .trade(workPlan.getTrade())
                        .location(workPlan.getLocation())
                        .planType(PlanType.WEEKLY)
                        .status(PlanStatus.PLANNED)
                        .startDate(tomorrow)
                        .endDate(tomorrow)
                        .partner(workPlan.getPartner())
                        .manager(workPlan.getManager())
                        .contact(workPlan.getContact())
                        .build());

        // feat : 달력에 표시될 명일 작업 내용 텍스트 업데이트
        tomorrowPlan.updateInfo(
                tomorrowName, workPlan.getTrade(), workPlan.getLocation(),
                tomorrow, tomorrow, PlanStatus.PLANNED,
                workPlan.getPartner(), workPlan.getManager(), workPlan.getContact(),
                dto.getTomorrowPlan()
        );

        // [REPORT_006] 6단계 : 명일 스케줄 투입 인원 연동 기능
        // feat : 명일 투입 예정 인원 세팅 및 JPA 부모 매핑(workPlan) 추가
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

        // [REPORT_007] 7단계 : 명일 스케줄 투입 장비 연동 기능
        // feat : 명일 투입 예정 장비 세팅 및 JPA 부모 매핑(workPlan) 추가
        if (dto.getTomorrowEquipments() != null && !dto.getTomorrowEquipments().isEmpty()) {
            List<WorkPlanEquipment> eqList = dto.getTomorrowEquipments().stream().map(eq -> {
                EquipmentType type = null;
                try {
                    type = EquipmentType.valueOf(eq.getType());
                } catch (Exception e) {
                    try {
                        type = EquipmentType.fromLabel(eq.getType());
                    } catch (Exception ex) {
                        // feat : 유효하지 않은 장비명일 경우 null 반환하여 저장 방지
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

        return savedReport.getIdx();
    }

    // [REPORT_002] 2단계 : 특정 일자 공사일보 목록 조회 기능
    // feat : 특정 날짜의 공사일보 목록을 조회하여 프론트엔드 응답용 DTO로 변환
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