package org.example.dndn.analysis;

import lombok.RequiredArgsConstructor;
import org.example.dndn.common.model.BaseResponse;
import org.example.dndn.analysis.model.ScheduleChangeDto;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/schedule-change-request")
@RequiredArgsConstructor
public class ScheduleChangeController {

    private final ScheduleChangeService scheduleChangeService;

    // 변경 요청 등록 (공정 책임자)
    @PostMapping
    public ResponseEntity<?> create(@RequestBody ScheduleChangeDto.Req dto) {
        Long newIdx = scheduleChangeService.create(dto);
        return ResponseEntity.ok(BaseResponse.success(newIdx));
    }

    // 변경 요청 목록
    // GET /schedule-change-request?projectId=1&process=철근&requester=김철수
    @GetMapping
    public ResponseEntity<?> list(
            @RequestParam("projectId") Long projectId,
            @RequestParam(value = "process", required = false) String process,
            @RequestParam(value = "requester", required = false) String requester) {
        return ResponseEntity.ok(BaseResponse.success(
                scheduleChangeService.listRequests(projectId, process, requester)));
    }

    // 변경 이력 (처리 완료된 것만)
    // GET /schedule-change-request/history?projectId=1&process=철근
    @GetMapping("/history")
    public ResponseEntity<?> history(
            @RequestParam("projectId") Long projectId,
            @RequestParam(value = "process", required = false) String process) {
        return ResponseEntity.ok(BaseResponse.success(
                scheduleChangeService.listHistory(projectId, process)));
    }

    // 승인 (총 책임자)
    @PutMapping("/{requestId}/approve")
    public ResponseEntity<?> approve(
            @PathVariable("requestId") Long requestId,
            @RequestBody ScheduleChangeDto.ApproveReq dto) {
        scheduleChangeService.approve(requestId, dto);
        return ResponseEntity.ok(BaseResponse.success("승인되었습니다."));
    }

    // 반려 (총 책임자)
    @PutMapping("/{requestId}/reject")
    public ResponseEntity<?> reject(
            @PathVariable("requestId") Long requestId,
            @RequestBody ScheduleChangeDto.RejectReq dto) {
        scheduleChangeService.reject(requestId, dto);
        return ResponseEntity.ok(BaseResponse.success("반려되었습니다."));
    }

    // 공정표 반영 (총 책임자 — APPROVED 상태에서만 가능)
    @PutMapping("/{requestId}/apply")
    public ResponseEntity<?> apply(@PathVariable("requestId") Long requestId) {
        scheduleChangeService.applyToSchedule(requestId);
        return ResponseEntity.ok(BaseResponse.success("공정표에 반영되었습니다."));
    }
}