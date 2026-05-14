package org.example.dndn.workplan;

import lombok.RequiredArgsConstructor;
import org.example.dndn.common.model.BaseResponse;
import org.example.dndn.workplan.model.WorkPlanDto;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import java.time.LocalDate;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;

import java.util.List;

@RestController
@RequestMapping("/work-plan")
@RequiredArgsConstructor
public class WorkPlanController {

    private final WorkPlanService workPlanService;

    // 작업 계획 등록
    @PostMapping
    public ResponseEntity<?> create(@RequestBody WorkPlanDto.Req dto) {
        Long newIdx = workPlanService.create(dto);
        return ResponseEntity.ok(BaseResponse.success(newIdx));
    }

    // 작업 계획 단일 조회
    @GetMapping("/{planId}")
    public ResponseEntity<?> read(@PathVariable("planId") Long planId) {
        WorkPlanDto.Res dto = workPlanService.read(planId);
        return ResponseEntity.ok(BaseResponse.success(dto));
    }
    @GetMapping("/project/{projectId}")
    public ResponseEntity<?> listByProject(
            @PathVariable Long projectId,
            @RequestParam(value = "includeAllTrades", defaultValue = "false") boolean includeAllTrades) {
        return ResponseEntity.ok(BaseResponse.success(
                workPlanService.listByProject(projectId, includeAllTrades)
        ));
    }

    // 작업 계획 목록 조회 (계획 종류 + 공종/상태 필터)
    @GetMapping
    public ResponseEntity<?> list(
            @RequestParam(value = "planType", defaultValue = "월간") String planType,
            @RequestParam(value = "projectId", required = false) Long projectId,
            @RequestParam(value = "trade", required = false) String trade,
            @RequestParam(value = "status", required = false) String status,
            @RequestParam(value = "startDate", required = false) LocalDate startDate,
            @RequestParam(value = "endDate", required = false) LocalDate endDate) {
        List<WorkPlanDto.workPlanRes> dtos =
                workPlanService.list(projectId, planType, trade, status, startDate, endDate);

        return ResponseEntity.ok(BaseResponse.success(dtos));
    }

    // 작업 계획 정보 수정
    @PutMapping("/{planId}")
    public ResponseEntity<?> update(
            @PathVariable("planId") Long planId,
            @RequestBody WorkPlanDto.Req dto) {
        workPlanService.update(planId, dto);
        return ResponseEntity.ok(BaseResponse.success("작업 계획이 수정되었습니다."));
    }

    // 일정 연장 등록/수정
    @PutMapping("/{planId}/extension")
    public ResponseEntity<?> extend(
            @PathVariable("planId") Long planId,
            @RequestBody WorkPlanDto.ExtReq dto) {
        workPlanService.extend(planId, dto);
        return ResponseEntity.ok(BaseResponse.success("일정 연장이 반영되었습니다."));
    }

    // 주간 계획서 일괄 제출 (협력사 담당자가 한 번에 여러 일자 작업 등록)
    @PostMapping("/weekly")
    public ResponseEntity<?> submitWeekly(@RequestBody WorkPlanDto.WeeklySubmitReq dto) {
        List<Long> savedIds = workPlanService.submitWeekly(dto);
        return ResponseEntity.ok(BaseResponse.success(savedIds));
    }

    // 작업 착수 처리 (실제 시작일 기록)
    @PutMapping("/{planId}/start")
    public ResponseEntity<?> start(@PathVariable("planId") Long planId) {
        workPlanService.start(planId);
        return ResponseEntity.ok(BaseResponse.success("작업이 착수 처리되었습니다."));
    }

    // 작업 계획 삭제
    @DeleteMapping("/{planId}")
    public ResponseEntity<?> delete(@PathVariable("planId") Long planId) {
        workPlanService.delete(planId);
        return ResponseEntity.ok(BaseResponse.success("작업 계획이 삭제되었습니다."));
    }
}
