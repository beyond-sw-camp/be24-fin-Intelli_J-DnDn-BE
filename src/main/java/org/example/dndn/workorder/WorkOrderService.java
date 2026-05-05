package org.example.dndn.workorder;

import lombok.RequiredArgsConstructor;
import org.example.dndn.workorder.model.WorkOrder;
import org.example.dndn.workorder.model.WorkOrderDto;
import org.example.dndn.workorder.model.WorkOrderEquipment;
import org.example.dndn.workorder.model.WorkOrderEquipmentDto;
import org.example.dndn.workplan.WorkPlanRepository;
import org.example.dndn.workplan.model.WorkPlanDto;
import org.example.dndn.workplan.model.entity.WorkPlan;
import org.example.dndn.workplan.model.enums.PlanStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

// feat : 작업 지시서 비즈니스 로직 처리 클래스
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class WorkOrderService {

    private final WorkOrderRepository workOrderRepository;
    private final WorkOrderEquipmentRepository workOrderEquipmentRepository;
    private final WorkPlanRepository workPlanRepository;
    // NOTE: GateRepository 미사용 — 게이트명 resolve는 프론트의 gates 배열에서 처리

    // [WORKORDER_001] 1단계 : 작업 지시서 기본 작성 기능
    // feat : 작업 지시서 신규 생성
    @Transactional
    public void createWorkOrder(WorkOrderDto.Req req) {
        WorkOrder workOrder = WorkOrder.builder()
                .siteIdx(req.getSiteIdx())
                .partnerCompanyIdx(req.getPartnerCompanyIdx())
                .workPlanId(req.getWorkPlanId())
                .tradeType(req.getTradeType())
                .title(req.getTitle())
                .instructionContent(req.getInstructionContent())
                .dueDate(req.getDueDate())
                .statusCode(req.getStatusCode())
                .workerCount(req.getWorkerCount())
                .build();

        workOrder = workOrderRepository.saveAndFlush(workOrder);
        workOrderEquipmentRepository.deleteAllByWorkOrderIdx(workOrder.getIdx());

        if (req.getEquipments() != null) {
            for (WorkOrderEquipmentDto eqDto : req.getEquipments()) {
                WorkOrderEquipment equipment = WorkOrderEquipment.builder()
                        .gateIdx(eqDto.getGateIdx())
                        .equipmentName(eqDto.getEquipmentName())
                        .equipmentCount(eqDto.getEquipmentCount())
                        .build();
                workOrder.addEquipment(equipment);
            }
        }

        workOrderRepository.save(workOrder);
    }

    // [WORKORDER_003] 3단계 : 지시서 목록 조회 기능
    // feat : 작업 지시서 목록 전체 조회
    public List<WorkOrderDto.Res> getWorkOrderList() {
        return workOrderRepository.findAll().stream().map(order -> {
            List<WorkOrderEquipmentDto> eqDtos = order.getEquipments().stream()
                    .map(eq -> WorkOrderEquipmentDto.builder()
                            .idx(eq.getIdx())
                            .gateIdx(eq.getGateIdx())
                            .equipmentName(eq.getEquipmentName())
                            .equipmentCount(eq.getEquipmentCount())
                            .build())
                    .collect(Collectors.toList());

            return WorkOrderDto.Res.builder()
                    .idx(order.getIdx())
                    .siteIdx(order.getSiteIdx())
                    .partnerCompanyIdx(order.getPartnerCompanyIdx())
                    .workPlanId(order.getWorkPlanId())
                    .tradeType(order.getTradeType())
                    .title(order.getTitle())
                    .instructionContent(order.getInstructionContent())
                    .dueDate(order.getDueDate())
                    .statusCode(order.getStatusCode())
                    .workerCount(order.getWorkerCount())
                    .equipments(eqDtos)
                    .build();
        }).collect(Collectors.toList());
    }

    // [WORKORDER_004] 4단계 : 작업 지시서 단건 수정 기능
    @Transactional
    public void updateWorkOrder(Long id, WorkOrderDto.Req req) {
        WorkOrder workOrder = workOrderRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("작업 지시서를 찾을 수 없습니다."));

        workOrder.setSiteIdx(req.getSiteIdx());
        workOrder.setPartnerCompanyIdx(req.getPartnerCompanyIdx());
        workOrder.setWorkPlanId(req.getWorkPlanId());
        workOrder.setTradeType(req.getTradeType());
        workOrder.setTitle(req.getTitle());
        workOrder.setInstructionContent(req.getInstructionContent());
        workOrder.setDueDate(req.getDueDate());
        workOrder.setStatusCode(req.getStatusCode());
        workOrder.setWorkerCount(req.getWorkerCount());

        workOrder.clearEquipments();
        workOrderRepository.flush();

        if (req.getEquipments() != null) {
            for (WorkOrderEquipmentDto eqDto : req.getEquipments()) {
                WorkOrderEquipment equipment = WorkOrderEquipment.builder()
                        .gateIdx(eqDto.getGateIdx())
                        .equipmentName(eqDto.getEquipmentName())
                        .equipmentCount(eqDto.getEquipmentCount())
                        .build();
                workOrder.addEquipment(equipment);
            }
        }

        workOrderRepository.save(workOrder);
    }

    // [WORKORDER_006] 6단계 : 주간계획 연동 초안 장비 불러오기 기능
    public List<WorkOrderEquipmentDto> getDraftEquipments(Long planIdx) {
        List<Object[]> results = workOrderRepository.findEquipmentsByPlanIdx(planIdx);

        return results.stream().map(row -> {
            String eqName = row[0] != null ? row[0].toString() : "EXCAVATOR";
            Integer eqCount = row[1] != null ? Integer.parseInt(row[1].toString()) : 1;

            return WorkOrderEquipmentDto.builder()
                    .gateIdx(1)
                    .equipmentName(eqName)
                    .equipmentCount(eqCount)
                    .build();
        }).collect(Collectors.toList());
    }

    // [WORKORDER_007] 7단계 : 작업 지시서 승인 및 주간 계획 반영 기능
    @Transactional
    public void approveWorkOrder(Long id) {
        WorkOrder workOrder = workOrderRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("작업 지시서를 찾을 수 없습니다."));

        if (workOrder.getWorkPlanId() == null) {
            throw new RuntimeException("연결된 주간 작업 계획이 없습니다.");
        }

        WorkPlan weeklyPlan = workPlanRepository.findById(workOrder.getWorkPlanId())
                .orElseThrow(() -> new RuntimeException("주간 작업 계획을 찾을 수 없습니다."));

        weeklyPlan.updateInfo(
                workOrder.getInstructionContent(),
                weeklyPlan.getTrade(),
                weeklyPlan.getLocation(),
                workOrder.getDueDate(),
                workOrder.getDueDate(),
                PlanStatus.PLANNED,
                weeklyPlan.getPartner(),
                weeklyPlan.getManager(),
                weeklyPlan.getContact(),
                "작업지시서 승인 반영"
        );

        if (workOrder.getEquipments() != null && !workOrder.getEquipments().isEmpty()) {
            weeklyPlan.replaceEquipment(
                    workOrder.getEquipments().stream()
                            .filter(eq -> eq.getEquipmentName() != null && !eq.getEquipmentName().isBlank())
                            .map(eq -> WorkPlanDto.EquipmentEntry.builder()
                                    .type(eq.getEquipmentName())
                                    .count(eq.getEquipmentCount())
                                    .build()
                                    .toEntity())
                            .toList()
            );
        }

        workOrder.setStatusCode("APPROVED");
    }

    // [GATE_EQUIP_001] 중장비 입출차 현황 페이지 테이블 연동
    // feat : 지정일(기본 오늘) 기준 투입 장비 목록 조회
    // NOTE: gateName은 null 반환 — 프론트의 gates 배열에서 gateIdx 기준으로 매칭
    public List<WorkOrderDto.GateEquipmentRes> getGateEquipments(LocalDate targetDate) {
        LocalDate date = targetDate != null ? targetDate : LocalDate.now();

        List<WorkOrder> orders = workOrderRepository.findAll().stream()
                .filter(o -> !Boolean.TRUE.equals(o.getIsDeleted()))
                .filter(o -> o.getDueDate() != null && o.getDueDate().isEqual(date))
                .toList();

        List<WorkOrderDto.GateEquipmentRes> result = new ArrayList<>();

        for (WorkOrder order : orders) {
            for (WorkOrderEquipment eq : order.getEquipments()) {
                if (Boolean.TRUE.equals(eq.getIsDeleted())) continue;

                String workOrderRef = String.format("WI-%d-%03d",
                        order.getDueDate().getYear(),
                        order.getIdx());

                result.add(WorkOrderDto.GateEquipmentRes.builder()
                        .workOrderIdx(order.getIdx())
                        .workOrderRef(workOrderRef)
                        .equipmentName(eq.getEquipmentName())
                        .equipmentType(WorkOrderDto.GateEquipmentRes.parseEquipmentType(eq.getEquipmentName()))
                        .equipmentCount(eq.getEquipmentCount())
                        .gateIdx(eq.getGateIdx())
                        .gateName(null)
                        .partnerCompanyIdx(order.getPartnerCompanyIdx())
                        .statusCode(order.getStatusCode())
                        .statusLabel(WorkOrderDto.GateEquipmentRes.resolveStatusLabel(order.getStatusCode()))
                        .dueDate(order.getDueDate())
                        .build());
            }
        }

        return result;
    }
}
