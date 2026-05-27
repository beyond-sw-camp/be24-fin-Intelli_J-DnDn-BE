package org.example.dndncore.analysis;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.ArraySchema;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.example.dndncore.common.model.BaseResponse;
import org.example.dndncore.analysis.model.ScheduleChangeDto;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@Tag(name = "Schedule Change", description = "일정 변경 요청 API")
@RestController
@RequestMapping("/schedule-change-request")
@RequiredArgsConstructor
public class ScheduleChangeController {

    private final ScheduleChangeService scheduleChangeService;

    @PostMapping
    @Operation(summary = "변경 요청 등록", description = "공정 책임자가 일정 변경 요청을 등록합니다.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "등록 성공",
                    content = @Content(schema = @Schema(implementation = Long.class))),
            @ApiResponse(responseCode = "500", description = "서버 오류")
    })
    public ResponseEntity<?> create(
            @io.swagger.v3.oas.annotations.parameters.RequestBody(
                    description = "일정 변경 요청 데이터",
                    required = true,
                    content = @Content(schema = @Schema(implementation = ScheduleChangeDto.Req.class))
            )
            @RequestBody ScheduleChangeDto.Req dto) {
        Long newIdx = scheduleChangeService.create(dto);
        return ResponseEntity.ok(BaseResponse.success(newIdx));
    }

    @GetMapping
    @Operation(summary = "변경 요청 목록", description = "프로젝트 기준 일정 변경 요청 목록을 조회합니다.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "조회 성공",
                    content = @Content(array = @ArraySchema(schema = @Schema(implementation = ScheduleChangeDto.Res.class)))),
            @ApiResponse(responseCode = "500", description = "서버 오류")
    })
    public ResponseEntity<?> list(
            @Parameter(description = "프로젝트 ID", required = true, example = "1")
            @RequestParam("projectId") Long projectId,
            @Parameter(description = "공정/공종 필터", example = "철근")
            @RequestParam(value = "process", required = false) String process,
            @Parameter(description = "요청자 필터", example = "김철수")
            @RequestParam(value = "requester", required = false) String requester) {
        return ResponseEntity.ok(BaseResponse.success(
                scheduleChangeService.listRequests(projectId, process, requester)));
    }

    @GetMapping("/history")
    @Operation(summary = "변경 이력 조회", description = "처리 완료된 일정 변경 이력을 조회합니다.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "조회 성공",
                    content = @Content(array = @ArraySchema(schema = @Schema(implementation = ScheduleChangeDto.Res.class)))),
            @ApiResponse(responseCode = "500", description = "서버 오류")
    })
    public ResponseEntity<?> history(
            @Parameter(description = "프로젝트 ID", required = true, example = "1")
            @RequestParam("projectId") Long projectId,
            @Parameter(description = "공정/공종 필터", example = "철근")
            @RequestParam(value = "process", required = false) String process) {
        return ResponseEntity.ok(BaseResponse.success(
                scheduleChangeService.listHistory(projectId, process)));
    }

    @PutMapping("/{requestId}/approve")
    @Operation(summary = "변경 요청 승인", description = "총 책임자가 일정 변경 요청을 승인합니다.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "승인 성공",
                    content = @Content(schema = @Schema(implementation = String.class))),
            @ApiResponse(responseCode = "500", description = "서버 오류")
    })
    public ResponseEntity<?> approve(
            @Parameter(description = "요청 ID", required = true, example = "1")
            @PathVariable("requestId") Long requestId,
            @io.swagger.v3.oas.annotations.parameters.RequestBody(
                    description = "승인 요청 데이터",
                    required = true,
                    content = @Content(schema = @Schema(implementation = ScheduleChangeDto.ApproveReq.class))
            )
            @RequestBody ScheduleChangeDto.ApproveReq dto) {
        scheduleChangeService.approve(requestId, dto);
        return ResponseEntity.ok(BaseResponse.success("승인되었습니다."));
    }

    @PutMapping("/{requestId}/reject")
    @Operation(summary = "변경 요청 반려", description = "총 책임자가 일정 변경 요청을 반려합니다.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "반려 성공",
                    content = @Content(schema = @Schema(implementation = String.class))),
            @ApiResponse(responseCode = "500", description = "서버 오류")
    })
    public ResponseEntity<?> reject(
            @Parameter(description = "요청 ID", required = true, example = "1")
            @PathVariable("requestId") Long requestId,
            @io.swagger.v3.oas.annotations.parameters.RequestBody(
                    description = "반려 요청 데이터",
                    required = true,
                    content = @Content(schema = @Schema(implementation = ScheduleChangeDto.RejectReq.class))
            )
            @RequestBody ScheduleChangeDto.RejectReq dto) {
        scheduleChangeService.reject(requestId, dto);
        return ResponseEntity.ok(BaseResponse.success("반려되었습니다."));
    }

    @PutMapping("/{requestId}/apply")
    @Operation(summary = "공정표 반영", description = "승인된 일정 변경 요청을 공정표에 반영합니다.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "반영 성공",
                    content = @Content(schema = @Schema(implementation = String.class))),
            @ApiResponse(responseCode = "500", description = "서버 오류")
    })
    public ResponseEntity<?> apply(
            @Parameter(description = "요청 ID", required = true, example = "1")
            @PathVariable("requestId") Long requestId) {
        scheduleChangeService.applyToSchedule(requestId);
        return ResponseEntity.ok(BaseResponse.success("공정표에 반영되었습니다."));
    }
}