package org.example.dndn.worker;

import lombok.RequiredArgsConstructor;
import org.example.dndn.common.model.BaseResponse;
import org.example.dndn.worker.model.dto.WorkerDetailDto;
import org.example.dndn.worker.model.dto.WorkerDto;
import org.example.dndn.worker.model.enums.AttendanceStatus;
import org.example.dndn.worker.service.WorkerDetailService;
import org.example.dndn.worker.service.WorkerService;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.util.List;


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

    // MANAGEMENT_002 근무자 검색
    @GetMapping("/search")
    public ResponseEntity<BaseResponse<WorkerDto.ListRes>> search(
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date,
            @RequestParam(required = false) AttendanceStatus attendanceStatus,
            @RequestParam(required = false) String partnerCompany,
            @RequestParam(required = false) String searchName
    ) {
        WorkerDto.SearchReq req = WorkerDto.SearchReq.builder()
                .date(date)
                .attendanceStatus(attendanceStatus)
                .partnerCompany(partnerCompany)
                .searchName(searchName)
                .build();
        WorkerDto.ListRes dto = workerService.search(req);
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

    // MANAGEMENT_004 작업자 상세 프로필 조회 (기본 프로필 카드).
    @GetMapping("/{workerIdx}/detail")
    public ResponseEntity<BaseResponse<WorkerDetailDto.ProfileRes>> detail(
            @PathVariable Long workerIdx
    ) {
        WorkerDetailDto.ProfileRes dto = workerDetailService.getProfile(workerIdx);
        return ResponseEntity.ok(BaseResponse.success(dto));
    }

    // MANAGEMENT_005 서류 현황 조회
    @GetMapping("/{workerIdx}/docs")
    public ResponseEntity<BaseResponse<List<WorkerDetailDto.DocRes>>> docs(
            @PathVariable Long workerIdx
    ) {
        List<WorkerDetailDto.DocRes> dto = workerDetailService.getDocuments(workerIdx);
        return ResponseEntity.ok(BaseResponse.success(dto));
    }

    // MANAGEMENT_006 최근 출결 이력 조회 (월별 캘린더)
    @GetMapping("/{workerIdx}/attendance")
    public ResponseEntity<BaseResponse<List<WorkerDetailDto.AttendanceRes>>> attendance(
            @PathVariable Long workerIdx,
            @RequestParam(required = false) String yearMonth
    ) {
        List<WorkerDetailDto.AttendanceRes> dto = workerDetailService.getAttendance(workerIdx, yearMonth);
        return ResponseEntity.ok(BaseResponse.success(dto));
    }

    // MANAGEMENT_007 구역 배치 이력 조회
    @GetMapping("/{workerIdx}/deployments")
    public ResponseEntity<BaseResponse<List<WorkerDetailDto.DeploymentRes>>> deployments(
            @PathVariable Long workerIdx
    ) {
        List<WorkerDetailDto.DeploymentRes> dto = workerDetailService.getDeployments(workerIdx);
        return ResponseEntity.ok(BaseResponse.success(dto));
    }
}
