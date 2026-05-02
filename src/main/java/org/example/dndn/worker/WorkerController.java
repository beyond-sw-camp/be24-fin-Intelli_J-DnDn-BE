package org.example.dndn.worker;

import lombok.RequiredArgsConstructor;
import org.example.dndn.common.model.BaseResponse;
import org.example.dndn.worker.model.dto.WorkerDetailDto;
import org.example.dndn.worker.model.dto.WorkerDto;
import org.example.dndn.worker.service.WorkerDetailService;
import org.example.dndn.worker.service.WorkerService;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;


@RestController
@RequiredArgsConstructor
@RequestMapping("/management")
public class WorkerController {
    private final WorkerService workerService;
    private final WorkerDetailService workerDetailService;

    // MANAGEMENT_001 인력 데이터 불러오기
    @GetMapping("/sync")
    public ResponseEntity<BaseResponse<WorkerDto.SyncRes>> syncWorkforce(@RequestParam(required = false) String siteCode)
    {
        WorkerDto.SyncRes dto = workerService.syncWorkforce(siteCode);
        return ResponseEntity.ok(BaseResponse.success(dto));
    }

    // MANAGEMENT_003 작업자 목록 조회
    @GetMapping("/list")
    public ResponseEntity<BaseResponse<WorkerDto.ListRes>> list(
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date
    ) {
        WorkerDto.ListRes dto = workerService.getList(date);
        return ResponseEntity.ok(BaseResponse.success(dto));
    }

    //MANAGEMENT_004 작업자 상세 프로필 조회 (기본 프로필 카드).
    @GetMapping("/{workerIdx}/detail")
    public ResponseEntity<BaseResponse<WorkerDetailDto.ProfileRes>> detail(
            @PathVariable Long workerIdx
    ) {
        WorkerDetailDto.ProfileRes dto = workerDetailService.getProfile(workerIdx);
        return ResponseEntity.ok(BaseResponse.success(dto));
    }
}
