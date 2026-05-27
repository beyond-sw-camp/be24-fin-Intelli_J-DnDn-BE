package org.example.dndncore.ai;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.ArraySchema;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.example.dndncore.ai.model.AiScheduleRecommendationDto;
import org.example.dndncore.common.model.BaseResponse;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@Tag(name = "AI 스케줄 및 분석", description = "AI 스케줄 추천 및 관리 API")
@RestController
@RequestMapping("/ai/schedule-recommendations")
@RequiredArgsConstructor
public class AiScheduleRecommendationController {

    private final AiScheduleRecommendationService recommendationService;

    @Operation(summary = "AI 스케줄 추천 생성", description = "새로운 AI 스케줄 추천을 요청합니다.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "생성 성공",
                    content = @Content(schema = @Schema(implementation = AiScheduleRecommendationDto.Res.class))),
            @ApiResponse(responseCode = "500", description = "서버 오류")
    })
    @PostMapping
    public ResponseEntity<?> create(
            @io.swagger.v3.oas.annotations.parameters.RequestBody(
                    description = "AI 스케줄 추천 생성 요청 데이터",
                    required = true,
                    content = @Content(schema = @Schema(implementation = AiScheduleRecommendationDto.CreateReq.class))
            )
            @RequestBody AiScheduleRecommendationDto.CreateReq req) {
        return ResponseEntity.ok(BaseResponse.success(recommendationService.create(req)));
    }

    @Operation(summary = "AI 스케줄 추천 목록 조회", description = "특정 프로젝트의 AI 스케줄 추천 내역을 조회합니다.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "조회 성공",
                    content = @Content(array = @ArraySchema(schema = @Schema(implementation = AiScheduleRecommendationDto.Res.class)))),
            @ApiResponse(responseCode = "500", description = "서버 오류")
    })
    @GetMapping
    public ResponseEntity<?> list(
            @Parameter(description = "프로젝트 ID", required = true, example = "1")
            @RequestParam("projectId") Long projectId) {
        return ResponseEntity.ok(BaseResponse.success(recommendationService.list(projectId)));
    }

    @Operation(summary = "AI 스케줄 추천 상세 조회", description = "특정 AI 스케줄 추천의 상세 결과를 조회합니다.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "조회 성공",
                    content = @Content(schema = @Schema(implementation = AiScheduleRecommendationDto.Res.class))),
            @ApiResponse(responseCode = "500", description = "서버 오류")
    })
    @GetMapping("/{id}")
    public ResponseEntity<?> get(
            @Parameter(description = "AI 스케줄 추천 ID", required = true, example = "1")
            @PathVariable Long id) {
        return ResponseEntity.ok(BaseResponse.success(recommendationService.get(id)));
    }

    @Operation(summary = "AI 스케줄 추천 완료 처리", description = "AI 스케줄 추천 분석을 완료 상태로 처리합니다.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "처리 성공",
                    content = @Content(schema = @Schema(implementation = AiScheduleRecommendationDto.Res.class))),
            @ApiResponse(responseCode = "500", description = "서버 오류")
    })
    @PostMapping("/{id}/complete")
    public ResponseEntity<?> complete(
            @Parameter(description = "AI 스케줄 추천 ID", required = true, example = "1")
            @PathVariable Long id,
            @io.swagger.v3.oas.annotations.parameters.RequestBody(
                    description = "완료 처리 요청 데이터",
                    required = true,
                    content = @Content(schema = @Schema(implementation = AiScheduleRecommendationDto.CompleteReq.class))
            )
            @RequestBody AiScheduleRecommendationDto.CompleteReq req
    ) {
        return ResponseEntity.ok(BaseResponse.success(recommendationService.complete(id, req)));
    }

    @Operation(summary = "AI 스케줄 추천 실패 처리", description = "AI 스케줄 추천 분석을 실패 상태로 처리합니다.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "처리 성공",
                    content = @Content(schema = @Schema(implementation = AiScheduleRecommendationDto.Res.class))),
            @ApiResponse(responseCode = "500", description = "서버 오류")
    })
    @PostMapping("/{id}/fail")
    public ResponseEntity<?> fail(
            @Parameter(description = "AI 스케줄 추천 ID", required = true, example = "1")
            @PathVariable Long id,
            @io.swagger.v3.oas.annotations.parameters.RequestBody(
                    description = "실패 처리 요청 데이터",
                    required = true,
                    content = @Content(schema = @Schema(implementation = AiScheduleRecommendationDto.FailReq.class))
            )
            @RequestBody AiScheduleRecommendationDto.FailReq req
    ) {
        return ResponseEntity.ok(BaseResponse.success(recommendationService.fail(id, req)));
    }
}