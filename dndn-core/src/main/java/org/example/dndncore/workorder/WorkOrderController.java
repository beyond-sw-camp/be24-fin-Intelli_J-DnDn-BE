package org.example.dndncore.workorder;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.example.dndncore.common.model.BaseResponse;
import org.example.dndncore.workorder.model.WorkOrderDto;
import org.example.dndncore.workorder.model.WorkOrderEquipmentDto;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.util.List;

@RestController
@RequestMapping("/work-order")
@RequiredArgsConstructor
@Tag(name = "WorkOrder", description = "작업 지시서 관리 API")
public class WorkOrderController {

    private final WorkOrderService workOrderService;

    // [WORKORDER_001] 1단계 : 작업 지시서 기본 작성 기능 API
    // feat : 작업 지시서 생성 API
    @PostMapping
    @Operation(summary = "작업 지시서 생성", description = "새로운 작업 지시서를 생성합니다.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "생성 성공")
    })
    public ResponseEntity<?> createWorkOrder(@RequestBody WorkOrderDto.Req req) {
        workOrderService.createWorkOrder(req);
        return ResponseEntity.ok(BaseResponse.success("작업 지시서가 성공적으로 생성되었습니다."));
    }

    // [WORKORDER_003] 3단계 : 작업 지시서 목록 조회 기능 API
    // feat : 작업 지시서 목록 조회 API
    @GetMapping
    @Operation(summary = "작업 지시서 목록 조회", description = "모든 작업 지시서를 조회합니다.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "조회 성공")
    })
    public ResponseEntity<?> getWorkOrderList() {
        List<WorkOrderDto.Res> list = workOrderService.getWorkOrderList();
        return ResponseEntity.ok(BaseResponse.success(list));
    }

    @GetMapping("/slice")
    @Operation(summary = "작업 지시서 페이징 조회", description = "필터와 커서 방식 페이징으로 작업 지시서를 조회합니다.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "조회 성공")
    })
    public ResponseEntity<?> getWorkOrderSlice(
            @Parameter(description = "대상 일자", example = "2026-05-27")
            @RequestParam(required = false)
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE)
            LocalDate targetDate,
            @Parameter(description = "공사현장 ID", example = "2")
            @RequestParam(required = false) Long projectId,
            @Parameter(description = "공종 유형", example = "CARPENTRY")
            @RequestParam(required = false) String tradeType,
            @Parameter(description = "상태 코드", example = "PENDING")
            @RequestParam(required = false) String statusCode,
            @Parameter(description = "검색 키워드", example = "철근")
            @RequestParam(required = false) String keyword,
            @Parameter(description = "커서 마감 일자", example = "2026-05-27")
            @RequestParam(required = false)
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE)
            LocalDate cursorDueDate,
            @Parameter(description = "커서 ID", example = "100")
            @RequestParam(required = false) Long cursorId,
            @Parameter(description = "페이지 크기", example = "10")
            @RequestParam(defaultValue = "10") Integer size
    ) {
        WorkOrderDto.SliceRes slice = workOrderService.getWorkOrderSlice(
                targetDate,
                projectId,
                tradeType,
                statusCode,
                keyword,
                cursorDueDate,
                cursorId,
                size
        );
        return ResponseEntity.ok(BaseResponse.success(slice));
    }

    // [WORKORDER_008] 중장비 입출차/기상관제/ESG 연동용 장비 조회 API
    // feat : 작업 지시서에 등록된 장비와 작업구역/상세내역 조회 API
    @GetMapping("/gate-equipments")
    @Operation(summary = "게이트 장비 조회", description = "중장비 입출차를 위한 작업 지시서 장비 정보를 조회합니다.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "조회 성공")
    })
    public ResponseEntity<?> getGateEquipments(
            @Parameter(description = "대상 일자", example = "2026-05-27")
            @RequestParam(required = false)
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE)
            LocalDate targetDate,
            @Parameter(description = "공사현장 ID", example = "1")
            @RequestParam(required = false)
            Long projectId,
            @Parameter(description = "장비가 없는 작업지시 포함 여부", example = "false")
            @RequestParam(defaultValue = "false")
            boolean includeNoEquipment
    ) {
        List<WorkOrderDto.GateEquipmentRes> list = workOrderService.getGateEquipments(
                targetDate,
                projectId,
                includeNoEquipment
        );
        return ResponseEntity.ok(BaseResponse.success(list));
    }

    // [WORKORDER_004] 4단계 : 작업 지시서 단건 수정 기능 API
    // feat : 작업 지시서 단건 수정 API (장비 갱신 포함)
    @PutMapping("/{id}")
    @Operation(summary = "작업 지시서 수정", description = "기존 작업 지시서를 수정합니다.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "수정 성공")
    })
    public ResponseEntity<?> updateWorkOrder(
            @Parameter(description = "작업 지시서 ID", example = "1")
            @PathVariable Long id,
            @RequestBody WorkOrderDto.Req req) {
        workOrderService.updateWorkOrder(id, req);
        return ResponseEntity.ok(BaseResponse.success("작업 지시서가 성공적으로 수정되었습니다."));
    }

    // [WORKORDER_007] 7단계 : 작업 지시서 승인 및 주간 계획 반영 기능 API
    // feat : 작업 지시서 승인 API
    @PutMapping("/{id}/approve")
    @Operation(summary = "작업 지시서 승인", description = "작업 지시서를 승인하고 주간 계획에 반영합니다.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "승인 성공")
    })
    public ResponseEntity<?> approveWorkOrder(
            @Parameter(description = "작업 지시서 ID", example = "1")
            @PathVariable Long id) {
        workOrderService.approveWorkOrder(id);
        return ResponseEntity.ok(BaseResponse.success("작업지시서가 승인되어 주간 계획에 반영되었습니다."));
    }

    // [WORKORDER_006] 6단계 : 주간계획 연동 초안 장비 불러오기 기능 API
    // feat : 초안 작성을 위한 예정 장비 목록 조회 API
    @GetMapping("/draft-equipments/{planIdx}")
    @Operation(summary = "초안 장비 조회", description = "주간 계획의 예정 장비 목록을 조회합니다.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "조회 성공")
    })
    public ResponseEntity<?> getDraftEquipments(
            @Parameter(description = "작업 계획 ID", example = "1")
            @PathVariable Long planIdx) {
        List<WorkOrderEquipmentDto> list = workOrderService.getDraftEquipments(planIdx);
        return ResponseEntity.ok(BaseResponse.success(list));
    }
}
