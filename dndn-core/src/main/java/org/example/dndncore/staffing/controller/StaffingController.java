package org.example.dndncore.staffing.controller;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.example.dndncore.common.model.BaseResponse;
import org.example.dndncore.staffing.model.StaffingDto;
import org.example.dndncore.staffing.service.StaffingService;
import org.example.dndncore.worker.model.enums.AffiliationKind;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.util.List;

@RestController
@RequiredArgsConstructor
@RequestMapping("/staffing")
@Tag(name = "Staffing", description = "인력 배치 및 매칭 API")
public class StaffingController {

    private final StaffingService staffingService;

    // STAFFING_003 — 기본 구역 정보 조회 (siteCode 로 현장 분리).
    @GetMapping("/zones")
    @Operation(summary = "기본 구역 정보 조회", description = "현장 코드와 기준 일자로 인력 배치 기본 구역 트리를 조회합니다.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "조회 성공")
    })
    public ResponseEntity<BaseResponse<List<StaffingDto.ZoneMainRes>>> getZones(
            @Parameter(description = "현장 코드", example = "SITE-001")
            @RequestParam(required = false) String siteCode,
            @Parameter(description = "명단 기준 일자", example = "2026-05-27")
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate rosterDate
    ) {
        List<StaffingDto.ZoneMainRes> dto = staffingService.loadZoneMainTree(rosterDate, siteCode);
        return ResponseEntity.ok(BaseResponse.success(dto));
    }

    /** STAFFING board — 구역 트리 + 직종별 필요 + 배치 작업자 일괄 조회 */
    @GetMapping("/board")
    @Operation(summary = "인력 배치 보드 조회", description = "구역 트리, 직종별 필요 인원, 배치 작업자 정보를 한 번에 조회합니다.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "조회 성공")
    })
    public ResponseEntity<BaseResponse<StaffingDto.BoardRes>> getBoard(
            @Parameter(description = "현장 코드", example = "SITE-001")
            @RequestParam(required = false) String siteCode,
            @Parameter(description = "명단 기준 일자", example = "2026-05-27")
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate rosterDate
    ) {
        StaffingDto.BoardRes dto = staffingService.loadStaffingBoard(rosterDate, siteCode);
        return ResponseEntity.ok(BaseResponse.success(dto));
    }

    // STAFFING_004 — 상세 구역(ZoneSub) 정보 조회
    @GetMapping("/zones/{zoneSubIdx}")
    @Operation(summary = "상세 구역 정보 조회", description = "상세 구역의 요약 정보와 직종별 필요/충원 정보를 조회합니다.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "조회 성공")
    })
    public ResponseEntity<BaseResponse<StaffingDto.ZoneSubRes>> getZoneSub(
            @Parameter(description = "상세 구역 ID", example = "1")
            @PathVariable Long zoneSubIdx,
            @Parameter(description = "명단 기준 일자", example = "2026-05-27")
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate rosterDate
    ) {
        StaffingDto.ZoneSubRes dto = staffingService.loadZoneSubDetail(zoneSubIdx, rosterDate);
        return ResponseEntity.ok(BaseResponse.success(dto));
    }

    // STAFFING_005 — 상세 구역(ZoneSub) 제목·직종별 필요 인원 수정
    @PatchMapping("/zones/{zoneSubIdx}")
    @Operation(summary = "상세 구역 수정", description = "상세 구역 제목과 직종별 필요 인원을 수정합니다.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "수정 성공")
    })
    public ResponseEntity<BaseResponse<Void>> patchZoneSub(
            @Parameter(description = "상세 구역 ID", example = "1")
            @PathVariable Long zoneSubIdx,
            @RequestBody StaffingDto.ZoneUpdateReq req
    ) {
        staffingService.updateZoneSub(zoneSubIdx, req);
        return ResponseEntity.ok(BaseResponse.success(null));
    }

    // STAFFING_006 — 해당 ZoneSub 배치 작업자 조회
    @GetMapping("/zones/{zoneSubIdx}/workers")
    @Operation(summary = "상세 구역 배치 작업자 조회", description = "해당 상세 구역에 배치된 작업자 목록을 조회합니다.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "조회 성공")
    })
    public ResponseEntity<BaseResponse<List<StaffingDto.AssignedWorkerRes>>> getAssignedWorkers(
            @Parameter(description = "상세 구역 ID", example = "1")
            @PathVariable Long zoneSubIdx,
            @Parameter(description = "명단 기준 일자", example = "2026-05-27")
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate rosterDate
    ) {
        List<StaffingDto.AssignedWorkerRes> dto =
                staffingService.loadAssignedWorkersForZoneSub(zoneSubIdx, rosterDate);
        return ResponseEntity.ok(BaseResponse.success(dto));
    }

    // STAFFING_006 — 해당 ZoneSub 에서 작업자 미투입
    @DeleteMapping("/zones/{zoneSubIdx}/workers/{workerIdx}")
    @Operation(summary = "작업자 배치 해제", description = "상세 구역에서 특정 작업자의 배치를 해제합니다.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "해제 성공")
    })
    public ResponseEntity<BaseResponse<Void>> unassignWorker(
            @Parameter(description = "상세 구역 ID", example = "1")
            @PathVariable Long zoneSubIdx,
            @Parameter(description = "작업자 ID", example = "101")
            @PathVariable Long workerIdx,
            @Parameter(description = "명단 기준 일자", example = "2026-05-27")
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate rosterDate
    ) {
        staffingService.unassignWorkerFromZoneSub(zoneSubIdx, workerIdx, rosterDate);
        return ResponseEntity.ok(BaseResponse.success(null));
    }

    // STAFFING_007 — 상세 구역에 작업자 수동 배치
    @PostMapping("/zones/{zoneSubIdx}/assign")
    @Operation(summary = "작업자 수동 배치", description = "상세 구역에 작업자를 수동으로 배치합니다.")
    @ApiResponses({
            @ApiResponse(responseCode = "201", description = "배치 성공")
    })
    public ResponseEntity<BaseResponse<Void>> assignWorkers(
            @Parameter(description = "상세 구역 ID", example = "1")
            @PathVariable Long zoneSubIdx,
            @RequestBody StaffingDto.AssignReq req,
            @Parameter(description = "명단 기준 일자", example = "2026-05-27")
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate rosterDate
    ) {
        req.setSubZoneIdx(zoneSubIdx);
        staffingService.assignWorkers(zoneSubIdx, req, rosterDate);
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(BaseResponse.success("배치 완료", null));
    }

    // STAFFING_008 — 작업자 현황 (명단 근태 기준 일자 + 미배치 필터 등)
    @GetMapping("/workers")
    @Operation(summary = "작업자 현황 조회", description = "현장, 소속, 키워드, 미배치 여부로 작업자 현황을 조회합니다.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "조회 성공")
    })
    public ResponseEntity<BaseResponse<StaffingDto.WorkerPoolRes>> getWorkerPool(
            @Parameter(description = "현장 코드", example = "SITE-001")
            @RequestParam(required = false) String siteCode,
            @Parameter(description = "소속 구분", example = "DIRECT")
            @RequestParam(required = false) AffiliationKind affiliationKind,
            @Parameter(description = "이름 또는 협력사 검색어", example = "김")
            @RequestParam(required = false) String keyword,
            @Parameter(description = "미배치 작업자만 조회 여부", example = "false")
            @RequestParam(required = false, defaultValue = "false") boolean unassignedOnly,
            @Parameter(description = "명단 기준 일자", example = "2026-05-27")
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate rosterDate
    ) {
        StaffingDto.PoolSearchReq req = StaffingDto.PoolSearchReq.builder()
                .siteCode(siteCode)
                .affiliationKind(affiliationKind)
                .keyword(keyword)
                .unassignedOnly(unassignedOnly)
                .build();
        StaffingDto.WorkerPoolRes dto = staffingService.getWorkerPool(req, rosterDate);
        return ResponseEntity.ok(BaseResponse.success(dto));
    }

    // STAFFING_001 — 인력 자동 추천 배치(본사 DIRECT·피로도 기준 저위험 공종 우선)
    @PostMapping("/auto-recommend")
    @Operation(summary = "인력 자동 추천 배치", description = "기준 일자와 현장을 기반으로 자동 추천 배치를 수행합니다.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "자동 추천 배치 성공")
    })
    public ResponseEntity<BaseResponse<StaffingDto.SaveSummaryRes>> autoRecommend(
            @Parameter(description = "현장 코드", example = "SITE-001")
            @RequestParam(required = false) String siteCode,
            @Parameter(description = "명단 기준 일자", example = "2026-05-27")
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate rosterDate
    ) {
        StaffingDto.SaveSummaryRes dto = staffingService.autoRecommend(rosterDate, siteCode);
        return ResponseEntity.ok(BaseResponse.success(dto));
    }

    /**
     * 최종배치(확정): {@code staffing_assignment} → {@code staffing_log} 스냅샷 기록.
     * siteCode 전달 시 해당 현장 배치만 확정한다.
     * POST /staffing/save
     */
    @PostMapping("/save")
    @Operation(summary = "최종 배치 확정", description = "현재 배치 정보를 확정하여 확정 이력으로 저장합니다.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "확정 저장 성공")
    })
    public ResponseEntity<BaseResponse<StaffingDto.SaveSummaryRes>> savePlacements(
            @Parameter(description = "현장 코드", example = "SITE-001")
            @RequestParam(required = false) String siteCode,
            @Parameter(description = "명단 기준 일자", example = "2026-05-27")
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate rosterDate
    ) {
        StaffingDto.SaveSummaryRes dto = staffingService.finalizePlacementsToAttendance(rosterDate, siteCode);
        return ResponseEntity.ok(BaseResponse.success("배치 저장 완료", dto));
    }

    // staffing_log 기준 확정 배치 근무자 조회 (당일 + 현장 필터)
    @GetMapping("/logs")
    @Operation(summary = "확정 배치 근무자 조회", description = "확정 이력 기준으로 근무자 배치 결과를 조회합니다.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "조회 성공")
    })
    public ResponseEntity<BaseResponse<List<StaffingDto.ConfirmedWorkerRes>>> getConfirmedWorkers(
            @Parameter(description = "현장 코드", example = "SITE-001")
            @RequestParam(required = false) String siteCode,
            @Parameter(description = "명단 기준 일자", example = "2026-05-27")
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate rosterDate
    ) {
        List<StaffingDto.ConfirmedWorkerRes> dto = staffingService.getConfirmedWorkers(rosterDate, siteCode);
        return ResponseEntity.ok(BaseResponse.success(dto));
    }

    // STAFFING_002 — 투입 인원 초기화 (siteCode 전달 시 해당 현장만 초기화).
    @PostMapping("/reset")
    @Operation(summary = "인력 배치 초기화", description = "기준 일자와 현장 기준으로 현재 배치를 초기화합니다.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "초기화 성공")
    })
    public ResponseEntity<BaseResponse<Void>> reset(
            @Parameter(description = "현장 코드", example = "SITE-001")
            @RequestParam(required = false) String siteCode,
            @Parameter(description = "명단 기준 일자", example = "2026-05-27")
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate rosterDate
    ) {
        staffingService.resetBoard(rosterDate, siteCode);
        return ResponseEntity.ok(BaseResponse.success(null));
    }
}
