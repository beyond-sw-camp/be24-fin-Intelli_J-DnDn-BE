package org.example.dndncore.weather;

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
public class WeatherInfoController {

    private final WeatherInfoService weatherInfoService;

    @GetMapping("/dashboard")
    public ResponseEntity<WeatherInfoDto.DashboardRes> readDashboard(
            @RequestParam(required = false)
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE)
            LocalDate reportDate
    ) {
        WeatherInfoDto.DashboardRes dto = weatherInfoService.readDashboard(reportDate);
        return ResponseEntity.ok(dto);
    }

    @GetMapping("/today")
    public ResponseEntity<BaseResponse<WeatherInfoDto.TodaySimpleRes>> readToday(
            @RequestParam(required = false)
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE)
            LocalDate reportDate
    ) {
        WeatherInfoDto.TodaySimpleRes dto = weatherInfoService.readTodaySimple(reportDate);
        return ResponseEntity.ok(BaseResponse.success(dto));
    }
}
