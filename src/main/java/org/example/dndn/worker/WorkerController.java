package org.example.dndn.worker;

import jakarta.validation.Valid;
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
    public ResponseEntity<BaseResponse<WorkerDto.SyncRes>> syncWorkforce(
            @RequestParam String siteCode,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date
    ) {
        WorkerDto.SyncRes dto = workerService.syncWorkforce(siteCode, date);
        return ResponseEntity.ok(BaseResponse.success(dto));
    }

    // MANAGEMENT_010 게이트 출근 인식 (HTTP). 추후 동일 본문을 WebSocket 으로 수신하도록 전환.
    // POST `/management/attendance/gate-clock-in`
    @PostMapping("/attendance/gate-clock-in")
    public ResponseEntity<BaseResponse<WorkerDto.GateAttendanceRes>> gateClockIn(
            @Valid @RequestBody WorkerDto.GateClockInReq req
    ) {
        WorkerDto.GateAttendanceRes dto = workerService.recordGateClockIn(req);
        return ResponseEntity.ok(BaseResponse.success(dto));
    }

    // MANAGEMENT_011 게이트 퇴근 인식 (HTTP).
    // POST `/management/attendance/gate-clock-out`
    @PostMapping("/attendance/gate-clock-out")
    public ResponseEntity<BaseResponse<WorkerDto.GateAttendanceRes>> gateClockOut(
            @Valid @RequestBody WorkerDto.GateClockOutReq req
    ) {
        WorkerDto.GateAttendanceRes dto = workerService.recordGateClockOut(req);
        return ResponseEntity.ok(BaseResponse.success(dto));
    }

    // MANAGEMENT_002 근무자 검색
    @GetMapping("/search")
    public ResponseEntity<BaseResponse<WorkerDto.ListRes>> search(
            @RequestParam(required = false) String siteCode,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date,
            @RequestParam(required = false) AttendanceStatus attendanceStatus,
            @RequestParam(required = false) String searchName
    ) {
        WorkerDto.SearchReq req = WorkerDto.SearchReq.builder()
                .siteCode(siteCode)
                .date(date)
                .attendanceStatus(attendanceStatus)
                .searchName(searchName)
                .build();
        WorkerDto.ListRes dto = workerService.search(req);
        return ResponseEntity.ok(BaseResponse.success(dto));
    }

    // MANAGEMENT_003 작업자 목록 조회
    @GetMapping("/list")
    public ResponseEntity<BaseResponse<WorkerDto.ListRes>> list(
            @RequestParam(required = false) String siteCode,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date
    ) {
        WorkerDto.ListRes dto = workerService.getList(siteCode, date);
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

    // MANAGEMENT_009 안전 사고 이력 조회
    @GetMapping("/{workerIdx}/accidents")
    public ResponseEntity<BaseResponse<List<WorkerDetailDto.AccidentRes>>> accidents(
            @PathVariable Long workerIdx
    ) {
        List<WorkerDetailDto.AccidentRes> dto = workerDetailService.getAccidents(workerIdx);
        return ResponseEntity.ok(BaseResponse.success(dto));
    }
}
