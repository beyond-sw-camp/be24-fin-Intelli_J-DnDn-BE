package org.example.dndncore.project.controller;

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
import org.example.dndncore.project.model.dto.TradeProcessDto;
import org.example.dndncore.project.service.TradeProcessService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/trade-process")
@RequiredArgsConstructor
@Tag(name = "Trade Process", description = "공정 관리 API")
public class TradeProcessController {

    private final TradeProcessService tradeProcessService;

    // feat : 공정 등록 API
    @PostMapping
    @Operation(summary = "공정 등록", description = "공정을 등록합니다.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "등록 성공",
                    content = @Content(schema = @Schema(implementation = Long.class))),
            @ApiResponse(responseCode = "400", description = "잘못된 요청"),
            @ApiResponse(responseCode = "500", description = "서버 오류")
    })
    public ResponseEntity<?> create(@RequestBody TradeProcessDto.Req dto) {
        Long newIdx = tradeProcessService.create(dto);
        return ResponseEntity.ok(BaseResponse.success(newIdx));
    }

    // feat : 공정 단일 조회 API
    @GetMapping("/{tpId}")
    @Operation(summary = "공정 단건 조회", description = "공정 ID로 단건 정보를 조회합니다.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "조회 성공",
                    content = @Content(schema = @Schema(implementation = TradeProcessDto.Res.class))),
            @ApiResponse(responseCode = "404", description = "대상을 찾을 수 없음"),
            @ApiResponse(responseCode = "500", description = "서버 오류")
    })
    public ResponseEntity<?> read(@PathVariable("tpId") Long tpId) {
        return ResponseEntity.ok(BaseResponse.success(tradeProcessService.read(tpId)));
    }

    // feat : 현장별 공정 목록 조회 API
    @GetMapping
    @Operation(summary = "공정 목록 조회", description = "현장별 공정 목록을 조회합니다.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "조회 성공",
                    content = @Content(array = @ArraySchema(schema = @Schema(implementation = TradeProcessDto.Res.class)))),
            @ApiResponse(responseCode = "500", description = "서버 오류")
    })
    public ResponseEntity<?> list(
            @Parameter(description = "프로젝트 ID", required = true, example = "1")
            @RequestParam("projectId") Long projectId,
            @Parameter(description = "공종명 필터", example = "골조")
            @RequestParam(value = "tradeName", required = false) String tradeName,
            @Parameter(description = "전체 공종 포함 여부", example = "false")
            @RequestParam(value = "includeAllTrades", defaultValue = "false") boolean includeAllTrades) {
        return ResponseEntity.ok(BaseResponse.success(
                tradeProcessService.listByProject(projectId, tradeName, includeAllTrades)));
    }

    // feat : 계정 생성 공종 드롭다운 조회 API
    @GetMapping("/milestone-trades")
    @Operation(summary = "마일스톤 공종명 목록 조회", description = "계정 생성 드롭다운용 마일스톤 공종명을 조회합니다.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "조회 성공",
                    content = @Content(array = @ArraySchema(schema = @Schema(implementation = String.class)))),
            @ApiResponse(responseCode = "500", description = "서버 오류")
    })
    public ResponseEntity<?> getMilestoneTradeNames(@RequestParam("projectId") Long projectId) {
        return ResponseEntity.ok(BaseResponse.success(
                tradeProcessService.listMilestoneTradeNamesByProject(projectId)));
    }

    // feat : 공정표별 공정 목록 조회 API
    @GetMapping("/by-schedule/{scheduleId}")
    @Operation(summary = "공정표별 공정 목록 조회", description = "공정표 기준 공정 목록을 조회합니다.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "조회 성공",
                    content = @Content(array = @ArraySchema(schema = @Schema(implementation = TradeProcessDto.Res.class)))),
            @ApiResponse(responseCode = "500", description = "서버 오류")
    })
    public ResponseEntity<?> listBySchedule(
            @Parameter(description = "공정표 ID", required = true, example = "1")
            @PathVariable("scheduleId") Long scheduleId,
            @Parameter(description = "전체 공종 포함 여부", example = "false")
            @RequestParam(value = "includeAllTrades", defaultValue = "false") boolean includeAllTrades) {
        return ResponseEntity.ok(BaseResponse.success(
                tradeProcessService.listBySchedule(scheduleId, includeAllTrades)));
    }

    @PutMapping("/{tpId}")
    @Operation(summary = "공정 수정", description = "공정 정보를 수정합니다.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "수정 성공",
                    content = @Content(schema = @Schema(implementation = String.class))),
            @ApiResponse(responseCode = "400", description = "잘못된 요청"),
            @ApiResponse(responseCode = "404", description = "대상을 찾을 수 없음"),
            @ApiResponse(responseCode = "500", description = "서버 오류")
    })
    public ResponseEntity<?> update(
            @Parameter(description = "공정 ID", required = true, example = "1")
            @PathVariable("tpId") Long tpId,
            @RequestBody TradeProcessDto.Req dto) {
        tradeProcessService.update(tpId, dto);
        return ResponseEntity.ok(BaseResponse.success("공정이 수정되었습니다."));
    }

    @DeleteMapping("/{tpId}")
    @Operation(summary = "공정 삭제", description = "공정을 삭제합니다.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "삭제 성공",
                    content = @Content(schema = @Schema(implementation = String.class))),
            @ApiResponse(responseCode = "404", description = "대상을 찾을 수 없음"),
            @ApiResponse(responseCode = "500", description = "서버 오류")
    })
    public ResponseEntity<?> delete(@PathVariable("tpId") Long tpId) {
        tradeProcessService.delete(tpId);
        return ResponseEntity.ok(BaseResponse.success("공정이 삭제되었습니다."));
    }
}
