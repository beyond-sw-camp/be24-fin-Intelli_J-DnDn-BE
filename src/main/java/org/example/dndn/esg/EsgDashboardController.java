package org.example.dndn.esg;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.example.dndn.common.model.BaseResponse;
import org.example.dndn.esg.model.EsgDashboardDto;
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
public class EsgDashboardController {

    private final EsgDashboardService esgDashboardService;

    @PostMapping("/snapshots")
    public ResponseEntity<?> createOrUpdateSnapshot(
            @Valid @RequestBody EsgDashboardDto.SaveSnapshotRequestDto request
    ) {
        return ResponseEntity.ok(BaseResponse.success(esgDashboardService.createOrUpdateSnapshot(request)));
    }

    @GetMapping("/dashboard")
    public ResponseEntity<?> readDashboard(
            @RequestParam(required = false)
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE)
            LocalDate reportDate,
            @RequestParam(required = false) Long projectId
    ) {
        return ResponseEntity.ok(BaseResponse.success(esgDashboardService.readDashboard(reportDate, projectId)));
    }
}
