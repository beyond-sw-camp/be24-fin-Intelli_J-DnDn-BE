package org.example.dndncore.report;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.ArraySchema;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.example.dndncore.common.model.BaseResponse;
import org.example.dndncore.report.model.ReportDto;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import java.time.LocalDate;
import java.util.List;

@RestController
@RequestMapping("/report")
@RequiredArgsConstructor
@Tag(name = "Daily Report", description = "공사일보 API")
public class DailyReportController {

    private final DailyReportService dailyReportService;

    // [REPORT_003] 3단계 : 공사일보 제출(Upsert) 기본 로직 구현
    // feat : 공사일보 제출 및 저장 API
    @PostMapping("/")
    @Operation(summary = "공사일보 제출", description = "공사일보를 신규 생성하거나 기존 데이터를 수정하여 저장합니다.")
    @io.swagger.v3.oas.annotations.parameters.RequestBody(
            description = "공사일보 제출 요청",
            required = true,
            content = @Content(schema = @Schema(implementation = ReportDto.Req.class))
    )
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "저장 성공",
                    content = @Content(schema = @Schema(implementation = Long.class))),
            @ApiResponse(responseCode = "400", description = "잘못된 요청 데이터")
    })
    public ResponseEntity<BaseResponse<Long>> submitReport(@Valid @RequestBody ReportDto.Req dto) {
        Long reportId = dailyReportService.submitReport(dto);
        return ResponseEntity.ok(BaseResponse.success(reportId));
    }

    // [REPORT_002] 2단계 : 특정 일자 공사일보 목록 조회 기능
    // feat : 특정 날짜의 공사일보 데이터를 반환하는 GET API 추가
    @GetMapping("/")
    @Operation(summary = "특정 일자 공사일보 조회", description = "지정한 날짜의 공사일보 목록을 조회합니다.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "조회 성공",
                    content = @Content(array = @ArraySchema(schema = @Schema(implementation = ReportDto.Res.class)))),
            @ApiResponse(responseCode = "400", description = "잘못된 날짜 형식")
    })
    public ResponseEntity<BaseResponse<List<ReportDto.Res>>> getReports(
            @Parameter(description = "조회 기준 일자", example = "2026-05-27", required = true)
            @RequestParam("date") LocalDate date) {
        return ResponseEntity.ok(BaseResponse.success(dailyReportService.getReportsByDate(date)));
    }
}