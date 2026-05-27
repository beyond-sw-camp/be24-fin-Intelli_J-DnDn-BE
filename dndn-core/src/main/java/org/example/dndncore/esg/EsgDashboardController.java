package org.example.dndncore.esg;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.example.dndncore.common.model.BaseResponse;
import org.example.dndncore.esg.model.EsgDashboardDto;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDate;

@RestController
@RequestMapping("/esg")
@RequiredArgsConstructor
@Tag(name = "ESG Dashboard", description = "ESG 대시보드 API")
public class EsgDashboardController {

    private final EsgDashboardService esgDashboardService;

    @PostMapping("/snapshots")
    @Operation(summary = "ESG 스냅샷 저장", description = "프로젝트 기준 ESG 일별 스냅샷을 생성하거나 갱신합니다.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "저장 성공",
                    content = @Content(schema = @Schema(implementation = EsgDashboardDto.SnapshotResponseDto.class))),
            @ApiResponse(responseCode = "400", description = "잘못된 요청"),
            @ApiResponse(responseCode = "500", description = "서버 오류")
    })
    public ResponseEntity<?> createOrUpdateSnapshot(
            @Valid @RequestBody EsgDashboardDto.SaveSnapshotRequestDto request
    ) {
        return ResponseEntity.ok(BaseResponse.success(esgDashboardService.createOrUpdateSnapshot(request)));
    }

    @GetMapping("/dashboard")
    @Operation(summary = "ESG 대시보드 조회", description = "기준일/프로젝트 기준 ESG 대시보드를 조회합니다.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "조회 성공",
                    content = @Content(schema = @Schema(implementation = EsgDashboardDto.DashboardResponseDto.class))),
            @ApiResponse(responseCode = "500", description = "서버 오류")
    })
    public ResponseEntity<?> readDashboard(
            @Parameter(description = "기준 보고일", example = "2026-05-27")
            @RequestParam(required = false)
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE)
            LocalDate reportDate,
            @Parameter(description = "프로젝트 ID", example = "1")
            @RequestParam(required = false) Long projectId
    ) {
        return ResponseEntity.ok(BaseResponse.success(esgDashboardService.readDashboard(reportDate, projectId)));
    }
}
