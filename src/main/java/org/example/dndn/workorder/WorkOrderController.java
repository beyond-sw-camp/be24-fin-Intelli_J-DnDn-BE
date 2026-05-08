package org.example.dndn.workorder;

import lombok.RequiredArgsConstructor;
import org.example.dndn.common.model.BaseResponse;
import org.example.dndn.workorder.model.WorkOrderDto;
import org.example.dndn.workorder.model.WorkOrderEquipmentDto;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.util.List;

@RestController
@RequestMapping("/work-order")
@RequiredArgsConstructor
public class WorkOrderController {

    private final WorkOrderService workOrderService;

    // [WORKORDER_001] 1단계 : 작업 지시서 기본 작성 기능 API
    // feat : 작업 지시서 생성 API
    @PostMapping
    public ResponseEntity<?> createWorkOrder(@RequestBody WorkOrderDto.Req req) {
        workOrderService.createWorkOrder(req);
        return ResponseEntity.ok(BaseResponse.success("작업 지시서가 성공적으로 생성되었습니다."));
    }

    // [WORKORDER_003] 3단계 : 작업 지시서 목록 조회 기능 API
    // feat : 작업 지시서 목록 조회 API
    @GetMapping
    public ResponseEntity<?> getWorkOrderList() {
        List<WorkOrderDto.Res> list = workOrderService.getWorkOrderList();
        return ResponseEntity.ok(BaseResponse.success(list));
    }

    // [WORKORDER_008] 중장비 입출차/기상관제/ESG 연동용 장비 조회 API
    // feat : 작업 지시서에 등록된 장비와 작업구역/상세내역 조회 API
    @GetMapping("/gate-equipments")
    public ResponseEntity<?> getGateEquipments(
            @RequestParam(required = false)
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE)
            LocalDate targetDate
    ) {
        List<WorkOrderDto.GateEquipmentRes> list = workOrderService.getGateEquipments(targetDate);
        return ResponseEntity.ok(BaseResponse.success(list));
    }

    // [WORKORDER_004] 4단계 : 작업 지시서 단건 수정 기능 API
    // feat : 작업 지시서 단건 수정 API (장비 갱신 포함)
    @PutMapping("/{id}")
    public ResponseEntity<?> updateWorkOrder(@PathVariable Long id, @RequestBody WorkOrderDto.Req req) {
        workOrderService.updateWorkOrder(id, req);
        return ResponseEntity.ok(BaseResponse.success("작업 지시서가 성공적으로 수정되었습니다."));
    }

    // [WORKORDER_007] 7단계 : 작업 지시서 승인 및 주간 계획 반영 기능 API
    // feat : 작업 지시서 승인 API
    @PutMapping("/{id}/approve")
    public ResponseEntity<?> approveWorkOrder(@PathVariable Long id) {
        workOrderService.approveWorkOrder(id);
        return ResponseEntity.ok(BaseResponse.success("작업지시서가 승인되어 주간 계획에 반영되었습니다."));
    }

    // [WORKORDER_006] 6단계 : 주간계획 연동 초안 장비 불러오기 기능 API
    // feat : 초안 작성을 위한 예정 장비 목록 조회 API
    @GetMapping("/draft-equipments/{planIdx}")
    public ResponseEntity<?> getDraftEquipments(@PathVariable Long planIdx) {
        List<WorkOrderEquipmentDto> list = workOrderService.getDraftEquipments(planIdx);
        return ResponseEntity.ok(BaseResponse.success(list));
    }
}
