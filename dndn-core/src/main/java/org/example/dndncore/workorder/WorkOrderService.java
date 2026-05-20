package org.example.dndncore.workorder;

import lombok.RequiredArgsConstructor;
import org.example.dndncore.auth.security.AuthAccessService;
import org.example.dndncore.document_event.DocumentEventProducer;
import org.example.dndncore.workorder.model.WorkOrder;
import org.example.dndncore.workorder.model.WorkOrderDto;
import org.example.dndncore.workorder.model.WorkOrderEquipment;
import org.example.dndncore.workorder.model.WorkOrderEquipmentDto;
import org.example.dndncore.workplan.WorkPlanRepository;
import org.example.dndncore.workplan.model.entity.WorkPlan;
import org.example.dndncore.workplan.model.entity.WorkPlanEquipment;
import org.example.dndncore.workplan.model.entity.WorkPlanWorker;
import org.example.dndncore.workplan.model.enums.EquipmentType;
import org.example.dndncore.workplan.model.enums.PlanStatus;
import org.example.dndncore.workplan.model.enums.WorkerTrade;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.sql.Date;
import java.time.LocalDate;
import java.util.List;
import java.util.stream.Collectors;

// feat : 작업 지시서 비즈니스 로직 처리 클래스
@Service
@RequiredArgsConstructor
public class WorkOrderService {

    private final WorkOrderRepository workOrderRepository;
    private final WorkOrderEquipmentRepository workOrderEquipmentRepository;
    private final WorkPlanRepository workPlanRepository;
    private final AuthAccessService authAccessService;
    private final DocumentEventProducer documentEventProducer;

    // [WORKORDER_001] 1단계 : 작업 지시서 기본 작성 기능
    // feat : 작업 지시서 신규 생성
    @Transactional
    public void createWorkOrder(WorkOrderDto.Req req) {
        assertRequestAccess(req);

        WorkOrder workOrder = WorkOrder.builder()
                .siteIdx(req.getSiteIdx())
                .partnerCompanyIdx(req.getPartnerCompanyIdx())
                .workPlanId(req.getWorkPlanId())
                .tradeType(req.getTradeType())
                .title(req.getTitle())
                .instructionContent(req.getInstructionContent())
                .workDetail(req.getWorkDetail())
                .workTime(req.getWorkTime())
                .safetyContent(req.getSafetyContent())
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
        workOrder = workOrderRepository.save(workOrder);
        documentEventProducer.publishWorkOrderChanged("WORK_ORDER_CREATED", workOrder);
    }

    // [WORKORDER_003] 3단계 : 지시서 목록 조회 기능
    // feat : 작업 지시서 목록 전체 조회
    @Transactional(readOnly = true)
    public List<WorkOrderDto.Res> getWorkOrderList() {
        return workOrderRepository.findAll().stream()
                .filter(this::canAccessWorkOrder)
                .map(order -> {
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
                    .workDetail(firstNonBlank(order.getWorkDetail(), order.getInstructionContent()))
                    .workTime(order.getWorkTime())
                    .safetyContent(order.getSafetyContent())
                    .dueDate(order.getDueDate())
                    .statusCode(order.getStatusCode())
                    .workerCount(order.getWorkerCount())
                    .equipments(eqDtos)
                    .build();
        }).collect(Collectors.toList());
    }

    // [WORKORDER_008] 중장비 입출차/기상관제/ESG 연동용 장비 조회 기능
    // feat : 작업지시서 장비와 작업구역/상세내역을 화면 연동용으로 반환
    @Transactional(readOnly = true)
    public List<WorkOrderDto.GateEquipmentRes> getGateEquipments(LocalDate targetDate) {
        return workOrderRepository.findGateEquipmentsByTargetDate(targetDate).stream()
                .filter(row -> authAccessService.canAccessProjectId(toLong(row[12]))
                        && authAccessService.canAccessTradeName(toStringValue(row[3])))
                .map(row -> {
                    LocalDate workDate = toLocalDate(row[5]);
                    String title = toStringValue(row[2]);
                    String workPlanName = toStringValue(row[7]);
                    String equipmentName = toStringValue(row[9]);

                    return WorkOrderDto.GateEquipmentRes.builder()
                            .idx(toLong(row[0]))
                            .workOrderIdx(toLong(row[1]))
                            .workOrderRef("WO-" + toLong(row[1]))
                            .title(firstNonBlank(title, workPlanName, "작업 지시서"))
                            .tradeType(toStringValue(row[3]))
                            .workDetail(toStringValue(row[4]))
                            .workDate(workDate)
                            .workLocation(firstNonBlank(toStringValue(row[6]), "작업구역 미지정"))
                            .gateIdx(toInteger(row[8]))
                            .equipmentName(firstNonBlank(equipmentName, "장비 미지정"))
                            .equipmentType(firstNonBlank(equipmentName, "중장비"))
                            .equipmentCount(defaultInteger(toInteger(row[10]), 1))
                            .statusLabel(resolveEquipmentStatus(toStringValue(row[11]), workDate))
                            .build();
                })
                .collect(Collectors.toList());
    }

    // [WORKORDER_004] 4단계 : 작업 지시서 단건 수정 기능
    // feat : 작업 지시서 내용 및 연관 장비 수정
    @Transactional
    public void updateWorkOrder(Long id, WorkOrderDto.Req req) {
        WorkOrder workOrder = workOrderRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("작업 지시서를 찾을 수 없습니다."));

        // feat : 기본 정보 업데이트
        assertWorkOrderAccess(workOrder);
        assertRequestAccess(req);

        workOrder.setSiteIdx(req.getSiteIdx());
        workOrder.setPartnerCompanyIdx(req.getPartnerCompanyIdx());
        workOrder.setWorkPlanId(req.getWorkPlanId());
        workOrder.setTradeType(req.getTradeType());
        workOrder.setTitle(req.getTitle());
        workOrder.setInstructionContent(req.getInstructionContent());
        workOrder.setWorkDetail(req.getWorkDetail());
        workOrder.setWorkTime(req.getWorkTime());
        workOrder.setSafetyContent(req.getSafetyContent());
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

        workOrder = workOrderRepository.save(workOrder);
        documentEventProducer.publishWorkOrderChanged("WORK_ORDER_UPDATED", workOrder);
    }

    // [WORKORDER_006] 6단계 : 주간계획 연동 초안 장비 불러오기 기능
    // feat : 초안 생성 시 장비만 별도로 조회하여 반환
    @Transactional(readOnly = true)
    public List<WorkOrderEquipmentDto> getDraftEquipments(Long planIdx) {
        workPlanRepository.findById(planIdx).ifPresent(authAccessService::assertWorkPlanAccess);
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
    // feat : 작업 지시서 승인 시 연결된 주간 계획에 내용, 인원, 장비 반영
    @Transactional
    public void approveWorkOrder(Long id) {
        WorkOrder workOrder = workOrderRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("작업 지시서를 찾을 수 없습니다."));

        assertWorkOrderAccess(workOrder);

        if (workOrder.getWorkPlanId() == null) {
            throw new RuntimeException("연결된 주간 작업 계획이 없습니다.");
        }

        WorkPlan weeklyPlan = workPlanRepository.findById(workOrder.getWorkPlanId())
                .orElseThrow(() -> new RuntimeException("주간 작업 계획을 찾을 수 없습니다."));

        // 1. 기본 정보 및 '비고'에 지시서 내용 반영
        // feat : 승인된 작업지시서 내용을 주간 작업 계획의 비고에 반영
        authAccessService.assertWorkPlanAccess(weeklyPlan);

        weeklyPlan.updateInfo(
                weeklyPlan.getName(),
                weeklyPlan.getTrade(),
                weeklyPlan.getLocation(),
                weeklyPlan.getStartDate(),
                weeklyPlan.getEndDate(),
                PlanStatus.PLANNED,
                weeklyPlan.getPartner(),
                weeklyPlan.getManager(),
                weeklyPlan.getContact(),
                firstNonBlank(workOrder.getWorkDetail(), workOrder.getInstructionContent())
        );

        //  2. 인원 정보 반영 (replaceWorkers를 사용해야 requiredCount가 자동 계산됨)
        // feat : 작업지시서의 인원수를 주간 계획에 반영 (Workers 리스트 생성)
        if (workOrder.getWorkerCount() != null) {
            WorkPlanWorker worker = WorkPlanWorker.builder()
                    .workPlan(weeklyPlan)
                    .trade(WorkerTrade.COMMON) // 기본 직종 설정 (엔티티 필드 상황에 맞게 조정)
                    .count(workOrder.getWorkerCount())
                    .build();

            // replaceWorkers 내부에서 recalculateRequiredCount()가 호출되어 인원수가 갱신됩니다.
            weeklyPlan.replaceWorkers(List.of(worker));
        }

        //  3. 장비 정보 반영
        // feat : 작업지시서 장비를 주간 계획 장비로 교체
        if (workOrder.getEquipments() != null && !workOrder.getEquipments().isEmpty()) {
            List<WorkPlanEquipment> newEquipList = workOrder.getEquipments().stream()
                    .filter(eq -> eq.getEquipmentName() != null && !eq.getEquipmentName().isBlank())
                    .map(eq -> WorkPlanEquipment.builder()
                            .workPlan(weeklyPlan)
                            .type(EquipmentType.fromLabel(eq.getEquipmentName())) // 한글명으로 변환
                            .count(eq.getEquipmentCount())
                            .build())
                    .toList();

            weeklyPlan.replaceEquipment(newEquipList);
        }

        workOrder.setStatusCode("APPROVED");
        documentEventProducer.publishWorkOrderChanged("WORK_ORDER_UPDATED", workOrder);
        // JPA 감지로 인해 weeklyPlan 변경사항이 자동 저장됩니다.
    }
    private void assertRequestAccess(WorkOrderDto.Req req) {
        if (req == null) {
            return;
        }
        authAccessService.assertProjectAccess(req.getSiteIdx());
        authAccessService.assertTradeAccess(req.getTradeType());
        if (req.getWorkPlanId() != null) {
            WorkPlan workPlan = workPlanRepository.findById(req.getWorkPlanId())
                    .orElseThrow(() -> new RuntimeException("WorkPlan not found. id=" + req.getWorkPlanId()));
            authAccessService.assertWorkPlanAccess(workPlan);
        }
    }

    private void assertWorkOrderAccess(WorkOrder workOrder) {
        if (!canAccessWorkOrder(workOrder)) {
            throw new org.springframework.web.server.ResponseStatusException(
                    org.springframework.http.HttpStatus.FORBIDDEN,
                    "No permission for this site or trade.");
        }
    }

    private boolean canAccessWorkOrder(WorkOrder workOrder) {
        return workOrder != null
                && authAccessService.canAccessProjectId(workOrder.getSiteIdx())
                && authAccessService.canAccessTradeName(workOrder.getTradeType());
    }

    private Long toLong(Object value) {
        if (value == null) {
            return null;
        }
        if (value instanceof Number number) {
            return number.longValue();
        }
        return Long.parseLong(value.toString());
    }

    private Integer toInteger(Object value) {
        if (value == null) {
            return null;
        }
        if (value instanceof Number number) {
            return number.intValue();
        }
        return Integer.parseInt(value.toString());
    }

    private Integer defaultInteger(Integer value, Integer defaultValue) {
        return value == null ? defaultValue : value;
    }

    private LocalDate toLocalDate(Object value) {
        if (value == null) {
            return null;
        }
        if (value instanceof LocalDate localDate) {
            return localDate;
        }
        if (value instanceof Date date) {
            return date.toLocalDate();
        }
        return LocalDate.parse(value.toString());
    }

    private String toStringValue(Object value) {
        return value == null ? null : value.toString();
    }

    private String firstNonBlank(String... values) {
        if (values == null) {
            return null;
        }
        for (String value : values) {
            if (value != null && !value.isBlank()) {
                return value;
            }
        }
        return null;
    }

    private String resolveEquipmentStatus(String statusCode, LocalDate workDate) {
        if ("COMPLETED".equalsIgnoreCase(statusCode) || "DONE".equalsIgnoreCase(statusCode)) {
            return "작업중";
        }
        if (workDate != null && workDate.isBefore(LocalDate.now())) {
            return "작업중";
        }
        return "입차예정";
    }

}
