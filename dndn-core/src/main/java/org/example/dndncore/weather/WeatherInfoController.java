package org.example.dndncore.weather;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.example.dndncore.common.model.BaseResponse;
import org.example.dndncore.weather.model.WeatherInfoDto;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDate;

@RestController
@RequestMapping("/weather")
@RequiredArgsConstructor
@Tag(name = "Weather", description = "기상 및 날씨 정보 API")
public class WeatherInfoController {

    private final WeatherInfoService weatherInfoService;

    @GetMapping("/dashboard")
    @Operation(summary = "대시보드 날씨 정보 조회", description = "기준 일자의 날씨 대시보드 정보를 조회합니다.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "조회 성공")
    })
    public ResponseEntity<WeatherInfoDto.DashboardRes> readDashboard(
            @Parameter(description = "조회 기준 일자", example = "2026-05-27")
            @RequestParam(required = false)
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE)
            LocalDate reportDate
    ) {
        WeatherInfoDto.DashboardRes dto = weatherInfoService.readDashboard(reportDate);
        return ResponseEntity.ok(dto);
    }

    @GetMapping("/today")
    @Operation(summary = "당일 간단 날씨 정보 조회", description = "기준 일자의 오전/오후/요약 날씨 정보를 조회합니다.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "조회 성공")
    })
    public ResponseEntity<BaseResponse<WeatherInfoDto.TodaySimpleRes>> readToday(
            @Parameter(description = "조회 기준 일자", example = "2026-05-27")
            @RequestParam(required = false)
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE)
            LocalDate reportDate
    ) {
        WeatherInfoDto.TodaySimpleRes dto = weatherInfoService.readTodaySimple(reportDate);
        return ResponseEntity.ok(BaseResponse.success(dto));
    }
}
