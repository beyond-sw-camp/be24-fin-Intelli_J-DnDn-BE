package org.example.dndn.report;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.example.dndn.common.model.BaseResponse;
import org.example.dndn.report.model.ReportDto;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import java.time.LocalDate;
import java.util.List;

@RestController
@RequestMapping("/report")
@RequiredArgsConstructor
public class DailyReportController {

    private final DailyReportService dailyReportService;

    // [REPORT_003] 3단계 : 공사일보 제출(Upsert) 기본 로직 구현
    // feat : 공사일보 제출 및 저장 API
    @PostMapping("/")
    public ResponseEntity<BaseResponse<Long>> submitReport(@Valid @RequestBody ReportDto.Req dto) {
        Long reportId = dailyReportService.submitReport(dto);
        return ResponseEntity.ok(BaseResponse.success(reportId));
    }

    // [REPORT_002] 2단계 : 특정 일자 공사일보 목록 조회 기능
    // feat : 특정 날짜의 공사일보 데이터를 반환하는 GET API 추가
    @GetMapping("/")
    public ResponseEntity<BaseResponse<List<ReportDto.Res>>> getReports(@RequestParam("date") LocalDate date) {
        return ResponseEntity.ok(BaseResponse.success(dailyReportService.getReportsByDate(date)));
    }
}