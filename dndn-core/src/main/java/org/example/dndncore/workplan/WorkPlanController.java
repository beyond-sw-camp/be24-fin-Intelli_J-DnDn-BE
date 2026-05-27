package org.example.dndncore.workplan;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.example.dndncore.common.model.BaseResponse;
import org.example.dndncore.workplan.model.WorkPlanDto;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;
import java.time.LocalDate;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;

import java.util.List;

@RestController
@RequestMapping("/work-plan")
@RequiredArgsConstructor
@Tag(name = "WorkPlan", description = "feat : 작업 계획 관리")
public class WorkPlanController {

    private final WorkPlanService workPlanService;

    @PostMapping
    @Operation(summary = "작업 계획 등록", description = "feat : 단일 작업 계획을 등록합니다")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "등록 성공", content = @Content(schema = @Schema(implementation = BaseResponse.class)))
    })
    public ResponseEntity<?> create(@RequestBody WorkPlanDto.Req dto) {
        Long newIdx = workPlanService.create(dto);
        return ResponseEntity.ok(BaseResponse.success(newIdx));
    }

    @PostMapping("/bulk")
    @Operation(summary = "작업 계획 일괄 등록", description = "feat : 여러 작업 계획을 한 번에 등록합니다")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "일괄 등록 성공", content = @Content(schema = @Schema(implementation = BaseResponse.class)))
    })
    public ResponseEntity<?> createBulk(@RequestBody List<WorkPlanDto.Req> dtos) {
        List<Long> savedIds = workPlanService.createBulk(dtos);
        return ResponseEntity.ok(BaseResponse.success(savedIds));
    }

    @PostMapping(value = "/upload", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @Operation(summary = "작업 계획 파일 업로드 및 추출", description = "feat : 엑셀 또는 PDF 파일에서 작업 계획 데이터를 추출합니다")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "파일 처리 성공", content = @Content(schema = @Schema(implementation = BaseResponse.class)))
    })
    public ResponseEntity<?> uploadAndExtract(
            @RequestParam("projectId") @Parameter(description = "현장 ID") Long projectId,
            @RequestParam("planType") @Parameter(description = "계획 유형 (연간/월간/주간)") String planType,
            @RequestParam(value = "trade", required = false) @Parameter(description = "공종") String trade,
            @RequestParam(value = "year", required = false) @Parameter(description = "연도") Integer year,
            @RequestParam(value = "month", required = false) @Parameter(description = "월") Integer month,
            @RequestParam("file") @Parameter(description = "업로드할 파일") MultipartFile file
    ) {
        List<WorkPlanDto.UploadExtractRes> rows =
                workPlanService.extractUpload(projectId, planType, trade, year, month, file);
        return ResponseEntity.ok(BaseResponse.success(rows));
    }

    @GetMapping("/{planId}")
    @Operation(summary = "작업 계획 단일 조회", description = "feat : 작업 계획 ID로 상세 정보를 조회합니다")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "조회 성공", content = @Content(schema = @Schema(implementation = BaseResponse.class)))
    })
    public ResponseEntity<?> read(@PathVariable("planId") @Parameter(description = "작업 계획 ID") Long planId) {
        WorkPlanDto.Res dto = workPlanService.read(planId);
        return ResponseEntity.ok(BaseResponse.success(dto));
    }

    @GetMapping("/project/{projectId}")
    @Operation(summary = "현장별 작업 계획 목록 조회", description = "feat : 특정 현장의 모든 작업 계획을 조회합니다")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "조회 성공", content = @Content(schema = @Schema(implementation = BaseResponse.class)))
    })
    public ResponseEntity<?> listByProject(
            @PathVariable @Parameter(description = "현장 ID") Long projectId,
            @RequestParam(value = "includeAllTrades", defaultValue = "false") @Parameter(description = "전체 공종 포함 여부") boolean includeAllTrades) {
        return ResponseEntity.ok(BaseResponse.success(
                workPlanService.listByProject(projectId, includeAllTrades)
        ));
    }

    @GetMapping
    @Operation(summary = "작업 계획 목록 조회", description = "feat : 계획 종류 및 필터 조건으로 작업 계획 목록을 조회합니다")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "조회 성공", content = @Content(schema = @Schema(implementation = BaseResponse.class)))
    })
    public ResponseEntity<?> list(
            @RequestParam(value = "planType", defaultValue = "월간") @Parameter(description = "계획 유형 (연간/월간/주간)") String planType,
            @RequestParam(value = "projectId", required = false) @Parameter(description = "현장 ID") Long projectId,
            @RequestParam(value = "trade", required = false) @Parameter(description = "공종") String trade,
            @RequestParam(value = "status", required = false) @Parameter(description = "상태 (진행 예정/진행 중)") String status,
            @RequestParam(value = "startDate", required = false) @Parameter(description = "시작일") LocalDate startDate,
            @RequestParam(value = "endDate", required = false) @Parameter(description = "종료일") LocalDate endDate) {
        List<WorkPlanDto.workPlanRes> dtos =
                workPlanService.list(projectId, planType, trade, status, startDate, endDate);

        return ResponseEntity.ok(BaseResponse.success(dtos));
    }

    @PutMapping("/{planId}")
    @Operation(summary = "작업 계획 정보 수정", description = "feat : 작업 계획의 기본 정보를 수정합니다")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "수정 성공", content = @Content(schema = @Schema(implementation = BaseResponse.class)))
    })
    public ResponseEntity<?> update(
            @PathVariable("planId") @Parameter(description = "작업 계획 ID") Long planId,
            @RequestBody WorkPlanDto.Req dto) {
        workPlanService.update(planId, dto);
        return ResponseEntity.ok(BaseResponse.success("작업 계획이 수정되었습니다."));
    }

    @PutMapping("/{planId}/extension")
    @Operation(summary = "일정 연장 등록 또는 수정", description = "feat : 작업 계획의 일정 연장 정보를 등록하거나 수정합니다")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "일정 연장 반영 성공", content = @Content(schema = @Schema(implementation = BaseResponse.class)))
    })
    public ResponseEntity<?> extend(
            @PathVariable("planId") @Parameter(description = "작업 계획 ID") Long planId,
            @RequestBody WorkPlanDto.ExtReq dto) {
        workPlanService.extend(planId, dto);
        return ResponseEntity.ok(BaseResponse.success("일정 연장이 반영되었습니다."));
    }

    @PostMapping("/weekly")
    @Operation(summary = "주간 계획서 일괄 제출", description = "feat : 협력사 담당자가 한 번에 여러 일자 작업을 등록합니다")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "주간 계획 제출 성공", content = @Content(schema = @Schema(implementation = BaseResponse.class)))
    })
    public ResponseEntity<?> submitWeekly(@RequestBody WorkPlanDto.WeeklySubmitReq dto) {
        List<Long> savedIds = workPlanService.submitWeekly(dto);
        return ResponseEntity.ok(BaseResponse.success(savedIds));
    }

    @PutMapping("/{planId}/start")
    @Operation(summary = "작업 착수 처리", description = "feat : 작업의 실제 시작일을 기록하고 상태를 진행 중으로 변경합니다")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "착수 처리 성공", content = @Content(schema = @Schema(implementation = BaseResponse.class)))
    })
    public ResponseEntity<?> start(@PathVariable("planId") @Parameter(description = "작업 계획 ID") Long planId) {
        workPlanService.start(planId);
        return ResponseEntity.ok(BaseResponse.success("작업이 착수 처리되었습니다."));
    }

    @DeleteMapping("/{planId}")
    @Operation(summary = "작업 계획 삭제", description = "feat : 작업 계획을 삭제합니다")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "삭제 성공", content = @Content(schema = @Schema(implementation = BaseResponse.class)))
    })
    public ResponseEntity<?> delete(@PathVariable("planId") @Parameter(description = "작업 계획 ID") Long planId) {
        workPlanService.delete(planId);
        return ResponseEntity.ok(BaseResponse.success("작업 계획이 삭제되었습니다."));
    }
}
