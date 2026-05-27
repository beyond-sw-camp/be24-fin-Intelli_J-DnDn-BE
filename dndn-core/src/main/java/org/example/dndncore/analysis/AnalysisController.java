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
import org.example.dndncore.analysis.model.AnalysisDto;
import org.example.dndncore.common.model.BaseResponse;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@Tag(name = "Analysis", description = "공정 변경 분석 API")
@RestController
@RequestMapping("/analysis")
@RequiredArgsConstructor
public class AnalysisController {

    private final AnalysisService analysisService;

    // feat : 공정 진척률 비교
    @GetMapping("/progress")
    @Operation(summary = "공정 진척률 비교", description = "프로젝트 기준 공정 진척률 비교 목록을 조회합니다.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "조회 성공",
                    content = @Content(array = @ArraySchema(schema = @Schema(implementation = AnalysisDto.ProcessProgressRes.class)))),
            @ApiResponse(responseCode = "500", description = "서버 오류")
    })
    public ResponseEntity<?> progress(@RequestParam("projectId") Long projectId) {
        return ResponseEntity.ok(BaseResponse.success(
                analysisService.getProgressList(projectId)));
    }

    // feat : 세부 작업 지연 위험 목록
    @GetMapping("/delay-risk-tasks")
    @Operation(summary = "세부 작업 지연 위험 목록", description = "프로젝트 기준 세부 작업 지연 위험 목록을 조회합니다.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "조회 성공",
                    content = @Content(array = @ArraySchema(schema = @Schema(implementation = AnalysisDto.DelayRiskDetailRes.class)))),
            @ApiResponse(responseCode = "500", description = "서버 오류")
    })
    public ResponseEntity<?> delayRiskTasks(
            @Parameter(description = "프로젝트 ID", required = true, example = "1")
            @RequestParam("projectId") Long projectId,
            @Parameter(description = "공종/공정 ID", example = "10")
            @RequestParam(value = "tradeProcessId", required = false) Long tradeProcessId
    ) {
        return ResponseEntity.ok(BaseResponse.success(
                analysisService.getDelayRiskTasks(projectId, tradeProcessId)
        ));
    }
}