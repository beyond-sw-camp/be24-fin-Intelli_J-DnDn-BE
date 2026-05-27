package org.example.dndncore.worker;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.example.dndncore.common.model.BaseResponse;
import org.example.dndncore.sse.SseEmitterRegistry;
import org.example.dndncore.worker.model.dto.WorkerDetailDto;
import org.example.dndncore.worker.model.dto.WorkerDto;
import org.example.dndncore.worker.model.enums.AttendanceStatus;
import org.example.dndncore.batch.BatchTriggerService;
import org.example.dndncore.worker.service.AttendanceBulkService;
import org.example.dndncore.worker.service.AttendanceSeedService;
import org.example.dndncore.worker.service.WorkerDetailService;
import org.example.dndncore.worker.service.WorkerService;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.time.LocalDate;
import java.util.List;


@RestController
@RequiredArgsConstructor
@RequestMapping("/management")
@Tag(name = "Worker Management", description = "작업자 및 근태 관리 API")
public class WorkerController {
    private final WorkerService workerService;
    private final WorkerDetailService workerDetailService;
    private final BatchTriggerService batchTriggerService;
    private final AttendanceSeedService attendanceSeedService;
    private final AttendanceBulkService attendanceBulkService;
    private final SseEmitterRegistry sseEmitterRegistry;

    // MANAGEMENT_SSE 출퇴근 실시간 스트림 — 관리자 웹에서 새로고침 없이 출근 반영
    // GET /management/sse/attendance-stream?siteCode=SITE01
    // EventSource(url, { withCredentials: true }) 로 연결; 이벤트명: attendance
    @GetMapping(value = "/sse/attendance-stream", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    @Operation(summary = "출퇴근 실시간 스트림", description = "SSE를 통해 현장별 출퇴근 정보를 실시간으로 스트리밍합니다.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "스트림 연결 성공")
    })
    public SseEmitter attendanceStream(
            @Parameter(description = "현장 코드", example = "SITE01")
            @RequestParam(required = false, defaultValue = "") String siteCode
    ) {
        return sseEmitterRegistry.subscribe(siteCode);
    }

    // MANAGEMENT_001 전체 현장 동기화 수동 트리거 — dndn-batch K8s Job 실행
    @PostMapping("/sync/all")
    @Operation(summary = "전체 현장 동기화 트리거", description = "dndn-batch K8s Job을 실행하여 모든 현장의 인력 데이터를 동기화합니다.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "배치 트리거 성공")
    })
    public ResponseEntity<BaseResponse<String>> syncAllSites() {
        String jobName = batchTriggerService.triggerWorkerSync();
        return ResponseEntity.ok(BaseResponse.success("배치 트리거 완료: " + jobName));
    }

    // MANAGEMENT_010 게이트 출근 인식 (HTTP). 추후 동일 본문을 WebSocket 으로 수신하도록 전환.
    // POST `/management/attendance/gate-clock-in`
    @PostMapping("/attendance/gate-clock-in")
    @Operation(summary = "게이트 출근 인식", description = "게이트에서 인식한 작업자의 출근을 기록합니다.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "출근 기록 성공")
    })
    public ResponseEntity<BaseResponse<WorkerDto.GateAttendanceRes>> gateClockIn(
            @RequestBody @Valid WorkerDto.GateClockInReq req
    ) {
        WorkerDto.GateAttendanceRes dto = workerService.recordGateClockIn(req);
        return ResponseEntity.ok(BaseResponse.success(dto));
    }

    // MANAGEMENT_011 게이트 퇴근 인식 (HTTP).
    // POST `/management/attendance/gate-clock-out`
    @PostMapping("/attendance/gate-clock-out")
    @Operation(summary = "게이트 퇴근 인식", description = "게이트에서 인식한 작업자의 퇴근을 기록합니다.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "퇴근 기록 성공")
    })
    public ResponseEntity<BaseResponse<WorkerDto.GateAttendanceRes>> gateClockOut(
            @RequestBody @Valid WorkerDto.GateClockOutReq req
    ) {
        WorkerDto.GateAttendanceRes dto = workerService.recordGateClockOut(req);
        return ResponseEntity.ok(BaseResponse.success(dto));
    }

    // MANAGEMENT_002 근무자 검색
    @GetMapping("/search")
    @Operation(summary = "근무자 검색", description = "현장, 출결 상태, 이름으로 근무자를 검색합니다.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "검색 성공")
    })
    public ResponseEntity<BaseResponse<WorkerDto.ListRes>> search(
            @Parameter(description = "현장 코드", example = "SITE01")
            @RequestParam(required = false) String siteCode,
            @Parameter(description = "기준 일자", example = "2026-05-27")
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date,
            @Parameter(description = "출결 상태", example = "PRESENT")
            @RequestParam(required = false) AttendanceStatus attendanceStatus,
            @Parameter(description = "근무자 이름 검색어", example = "김")
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

    // MANAGEMENT_003 작업자 목록 조회 (페이징 + 공종·이름 필터)
    @GetMapping("/list")
    @Operation(summary = "작업자 목록 조회", description = "페이징과 필터를 적용하여 작업자 목록을 조회합니다.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "조회 성공")
    })
    public ResponseEntity<BaseResponse<WorkerDto.ListRes>> list(
            @Parameter(description = "현장 코드", example = "SITE01")
            @RequestParam(required = false) String siteCode,
            @Parameter(description = "기준 일자", example = "2026-05-27")
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date,
            @Parameter(description = "공종명", example = "목공")
            @RequestParam(required = false) String tradeName,
            @Parameter(description = "작업자 이름 검색어", example = "김")
            @RequestParam(required = false) String searchName,
            @Parameter(description = "페이지 번호", example = "0")
            @RequestParam(defaultValue = "0") int page,
            @Parameter(description = "페이지 크기", example = "20")
            @RequestParam(defaultValue = "20") int size
    ) {
        WorkerDto.ListRes dto = workerService.getList(siteCode, date, tradeName, searchName, page, size);
        return ResponseEntity.ok(BaseResponse.success(dto));
    }

    // MANAGEMENT_004 작업자 상세 프로필 조회 (기본 프로필 카드).
    @GetMapping("/{workerIdx}/detail")
    @Operation(summary = "작업자 상세 프로필 조회", description = "작업자의 기본 프로필 정보와 피로도를 조회합니다.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "조회 성공")
    })
    public ResponseEntity<BaseResponse<WorkerDetailDto.ProfileRes>> detail(
            @Parameter(description = "작업자 ID", example = "1")
            @PathVariable Long workerIdx
    ) {
        WorkerDetailDto.ProfileRes dto = workerDetailService.getProfile(workerIdx);
        return ResponseEntity.ok(BaseResponse.success(dto));
    }

    // MANAGEMENT_005 서류 현황 조회
    @GetMapping("/{workerIdx}/docs")
    @Operation(summary = "서류 현황 조회", description = "작업자의 안전 서류 현황을 조회합니다.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "조회 성공")
    })
    public ResponseEntity<BaseResponse<List<WorkerDetailDto.DocRes>>> docs(
            @Parameter(description = "작업자 ID", example = "1")
            @PathVariable Long workerIdx
    ) {
        List<WorkerDetailDto.DocRes> dto = workerDetailService.getDocuments(workerIdx);
        return ResponseEntity.ok(BaseResponse.success(dto));
    }

    // MANAGEMENT_006 최근 출결 이력 조회 (월별 캘린더)
    @GetMapping("/{workerIdx}/attendance")
    @Operation(summary = "출결 이력 조회", description = "작업자의 월별 출결 이력을 조회합니다.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "조회 성공")
    })
    public ResponseEntity<BaseResponse<List<WorkerDetailDto.AttendanceRes>>> attendance(
            @Parameter(description = "작업자 ID", example = "1")
            @PathVariable Long workerIdx,
            @Parameter(description = "조회 년월", example = "202605")
            @RequestParam(required = false) String yearMonth
    ) {
        List<WorkerDetailDto.AttendanceRes> dto = workerDetailService.getAttendance(workerIdx, yearMonth);
        return ResponseEntity.ok(BaseResponse.success(dto));
    }

    // MANAGEMENT_007 구역 배치 이력 조회
    @GetMapping("/{workerIdx}/deployments")
    @Operation(summary = "구역 배치 이력 조회", description = "작업자의 구역 배치 확정 이력을 조회합니다.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "조회 성공")
    })
    public ResponseEntity<BaseResponse<List<WorkerDetailDto.DeploymentRes>>> deployments(
            @Parameter(description = "작업자 ID", example = "1")
            @PathVariable Long workerIdx
    ) {
        List<WorkerDetailDto.DeploymentRes> dto = workerDetailService.getDeployments(workerIdx);
        return ResponseEntity.ok(BaseResponse.success(dto));
    }

    // MANAGEMENT_009 안전 사고 이력 조회
    @GetMapping("/{workerIdx}/accidents")
    @Operation(summary = "안전 사고 이력 조회", description = "작업자의 안전 사고 이력을 조회합니다.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "조회 성공")
    })
    public ResponseEntity<BaseResponse<List<WorkerDetailDto.AccidentRes>>> accidents(
            @Parameter(description = "작업자 ID", example = "1")
            @PathVariable Long workerIdx
    ) {
        List<WorkerDetailDto.AccidentRes> dto = workerDetailService.getAccidents(workerIdx);
        return ResponseEntity.ok(BaseResponse.success(dto));
    }

    // MANAGEMENT_DEMO 출결 더미 이력 시딩 — 근무자별 피로도 다양화용 (현장 단위)
    @PostMapping("/attendance/seed-demo-history")
    @Operation(summary = "출결 더미 이력 시딩", description = "데모용 출결 이력을 현장 단위로 생성합니다.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "시딩 성공")
    })
    public ResponseEntity<BaseResponse<AttendanceSeedService.SeedResult>> seedDemoHistory(
            @Parameter(description = "현장 코드", example = "SITE01")
            @RequestParam String siteCode
    ) {
        AttendanceSeedService.SeedResult result = attendanceSeedService.seedDemoHistory(siteCode);
        return ResponseEntity.ok(BaseResponse.success(result));
    }

    // MANAGEMENT_DEMO 현장+날짜 근태 일괄 변경
    // targetStatus: PENDING(미출근) | PRESENT(출근) | LATE(지각) | EARLY_LEAVE(조퇴) | LEAVE(퇴근)
    @PostMapping("/attendance/bulk-override")
    @Operation(summary = "근태 일괄 변경", description = "현장과 날짜 기준으로 근태를 일괄 변경합니다.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "변경 성공")
    })
    public ResponseEntity<BaseResponse<AttendanceBulkService.BulkResult>> bulkOverrideAttendance(
            @Parameter(description = "현장 코드", example = "SITE01")
            @RequestParam String siteCode,
            @Parameter(description = "기준 일자", example = "2026-05-27")
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date,
            @Parameter(description = "변경할 상태", example = "PRESENT")
            @RequestParam String targetStatus
    ) {
        AttendanceBulkService.BulkResult result = attendanceBulkService.bulkOverride(siteCode, date, targetStatus);
        return ResponseEntity.ok(BaseResponse.success(result));
    }
}
