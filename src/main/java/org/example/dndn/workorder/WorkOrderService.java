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

import java.util.List;
import java.util.stream.Collectors;

// feat : 작업 지시서 비즈니스 로직 처리 클래스
@Service
@RequiredArgsConstructor
public class WorkOrderService {

    private final WorkOrderRepository workOrderRepository;
    private final WorkOrderEquipmentRepository workOrderEquipmentRepository;
    private final WorkPlanRepository workPlanRepository;

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

        // feat : 신규 지시서 저장 및 ID 발급
        workOrder = workOrderRepository.saveAndFlush(workOrder);

        // feat : 기존 장비 데이터 초기화
        workOrderEquipmentRepository.deleteAllByWorkOrderIdx(workOrder.getIdx());

        // [WORKORDER_002] 2단계 : 장비 매핑 로직 추가
        // feat : 신규 장비 데이터 연관관계 매핑 및 추가
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

        // feat : 최종 저장
        workOrderRepository.save(workOrder);
    }

    // [WORKORDER_003] 3단계 : 지시서 목록 조회 기능
    // feat : 작업 지시서 목록 전체 조회
    @Transactional(readOnly = true)
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
    // feat : 작업 지시서 내용 및 연관 장비 수정
    @Transactional
    public void updateWorkOrder(Long id, WorkOrderDto.Req req) {
        WorkOrder workOrder = workOrderRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("작업 지시서를 찾을 수 없습니다."));

        // feat : 기본 정보 업데이트
        workOrder.setSiteIdx(req.getSiteIdx());
        workOrder.setPartnerCompanyIdx(req.getPartnerCompanyIdx());
        workOrder.setWorkPlanId(req.getWorkPlanId());
        workOrder.setTradeType(req.getTradeType());
        workOrder.setTitle(req.getTitle());
        workOrder.setInstructionContent(req.getInstructionContent());
        workOrder.setDueDate(req.getDueDate());
        workOrder.setStatusCode(req.getStatusCode());
        workOrder.setWorkerCount(req.getWorkerCount());

        // [WORKORDER_005] 5단계 : 장비 초기화 로직 추가 (수정 기능 고도화)
        // feat : JPA의 정상적인 삭제 사이클을 이용해 기존 장비 목록 초기화
        workOrder.clearEquipments();
        workOrderRepository.flush(); // 기존 장비 DELETE 쿼리를 DB에 먼저 전송하여 충돌 방지

        // [WORKORDER_002] 2단계 : 장비 매핑 로직 추가
        // feat : 클라이언트로부터 전달받은 신규 장비 목록 연관관계 매핑 및 추가
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
    // feat : 초안 생성 시 장비만 별도로 조회하여 반환
    @Transactional(readOnly = true)
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
    // feat : 작업 지시서 승인 시 연결된 주간 계획에 내용과 장비 반영
    @Transactional
    public void approveWorkOrder(Long id) {
        WorkOrder workOrder = workOrderRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("작업 지시서를 찾을 수 없습니다."));

        if (workOrder.getWorkPlanId() == null) {
            throw new RuntimeException("연결된 주간 작업 계획이 없습니다.");
        }

        WorkPlan weeklyPlan = workPlanRepository.findById(workOrder.getWorkPlanId())
                .orElseThrow(() -> new RuntimeException("주간 작업 계획을 찾을 수 없습니다."));

        // feat : 승인된 작업지시서 내용을 주간 작업 계획에 반영
        weeklyPlan.updateInfo(
                workOrder.getInstructionContent(),   // 주간 계획의 작업 내용
                weeklyPlan.getTrade(),               // 기존 공종 유지
                weeklyPlan.getLocation(),            // 기존 작업 위치 유지
                workOrder.getDueDate(),              // 작업일
                workOrder.getDueDate(),              // 작업일
                PlanStatus.PLANNED,
                weeklyPlan.getPartner(),
                weeklyPlan.getManager(),
                weeklyPlan.getContact(),
                "작업지시서 승인 반영"
        );

        // feat : 작업지시서 장비를 주간 계획 장비로 반영
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

        // feat : 작업 지시서 승인 상태 변경
        workOrder.setStatusCode("APPROVED");
    }
}
