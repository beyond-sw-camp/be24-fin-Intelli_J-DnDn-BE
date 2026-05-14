package org.example.dndn.staffing.controller;

import lombok.RequiredArgsConstructor;
import org.example.dndn.common.model.BaseResponse;
import org.example.dndn.staffing.model.StaffingDto;
import org.example.dndn.staffing.service.StaffingService;
import org.example.dndn.worker.model.enums.AffiliationKind;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.util.List;

@RestController
@RequiredArgsConstructor
@RequestMapping("/staffing")
public class StaffingController {

    private final StaffingService staffingService;

    // STAFFING_003 — 기본 구역 정보 조회.
    @GetMapping("/zones")
    public ResponseEntity<BaseResponse<List<StaffingDto.ZoneMainRes>>> getZones(
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate rosterDate
    ) {
        List<StaffingDto.ZoneMainRes> dto = staffingService.loadZoneMainTree(rosterDate);
        return ResponseEntity.ok(BaseResponse.success(dto));
    }

    // STAFFING_004 — 상세 구역(ZoneSub) 정보 조회
    @GetMapping("/zones/{zoneSubIdx}")
    public ResponseEntity<BaseResponse<StaffingDto.ZoneSubRes>> getZoneSub(
            @PathVariable Long zoneSubIdx,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate rosterDate
    ) {
        StaffingDto.ZoneSubRes dto = staffingService.loadZoneSubDetail(zoneSubIdx, rosterDate);
        return ResponseEntity.ok(BaseResponse.success(dto));
    }

    // STAFFING_005 — 상세 구역(ZoneSub) 제목·직종별 필요 인원 수정
    @PatchMapping("/zones/{zoneSubIdx}")
    public ResponseEntity<BaseResponse<Void>> patchZoneSub(
            @PathVariable Long zoneSubIdx,
            @RequestBody StaffingDto.ZoneUpdateReq req
    ) {
        staffingService.updateZoneSub(zoneSubIdx, req);
        return ResponseEntity.ok(BaseResponse.success(null));
    }

    // STAFFING_006 — 해당 ZoneSub 배치 작업자 조회
    @GetMapping("/zones/{zoneSubIdx}/workers")
    public ResponseEntity<BaseResponse<List<StaffingDto.AssignedWorkerRes>>> getAssignedWorkers(
            @PathVariable Long zoneSubIdx,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate rosterDate
    ) {
        List<StaffingDto.AssignedWorkerRes> dto =
                staffingService.loadAssignedWorkersForZoneSub(zoneSubIdx, rosterDate);
        return ResponseEntity.ok(BaseResponse.success(dto));
    }

    // STAFFING_006 — 해당 ZoneSub 에서 작업자 미투입
    @DeleteMapping("/zones/{zoneSubIdx}/workers/{workerIdx}")
    public ResponseEntity<BaseResponse<Void>> unassignWorker(
            @PathVariable Long zoneSubIdx,
            @PathVariable Long workerIdx,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate rosterDate
    ) {
        staffingService.unassignWorkerFromZoneSub(zoneSubIdx, workerIdx, rosterDate);
        return ResponseEntity.ok(BaseResponse.success(null));
    }

    // STAFFING_007 — 상세 구역에 작업자 수동 배치
    @PostMapping("/zones/{zoneSubIdx}/assign")
    public ResponseEntity<BaseResponse<Void>> assignWorkers(
            @PathVariable Long zoneSubIdx,
            @RequestBody StaffingDto.AssignReq req,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate rosterDate
    ) {
        req.setSubZoneIdx(zoneSubIdx);
        staffingService.assignWorkers(zoneSubIdx, req, rosterDate);
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(BaseResponse.success("배치 완료", null));
    }

    // STAFFING_008 — 작업자 현황 (명단 근태 기준 일자 + 미배치 필터 등)
    @GetMapping("/workers")
    public ResponseEntity<BaseResponse<StaffingDto.WorkerPoolRes>> getWorkerPool(
            @RequestParam(required = false) String siteCode,
            @RequestParam(required = false) AffiliationKind affiliationKind,
            @RequestParam(required = false) String keyword,
            @RequestParam(required = false, defaultValue = "false") boolean unassignedOnly,
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
    public ResponseEntity<BaseResponse<StaffingDto.SaveSummaryRes>> autoRecommend(
            @RequestParam(required = false) String siteCode,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate rosterDate
    ) {
        StaffingDto.SaveSummaryRes dto = staffingService.autoRecommend(rosterDate, siteCode);
        return ResponseEntity.ok(BaseResponse.success(dto));
    }

    /**
     * 최종배치(확정): {@code staffing_assignment} → 당일 {@code attendance_record} 구역 반영.
     * POST /staffing/save
     */
    @PostMapping("/save")
    public ResponseEntity<BaseResponse<StaffingDto.SaveSummaryRes>> savePlacements(
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate rosterDate
    ) {
        StaffingDto.SaveSummaryRes dto = staffingService.finalizePlacementsToAttendance(rosterDate);
        return ResponseEntity.ok(BaseResponse.success("배치 저장 완료", dto));
    }

    // STAFFING_002 — 투입 인원 초기화.
    @PostMapping("/reset")
    public ResponseEntity<BaseResponse<Void>> reset(
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate rosterDate
    ) {
        staffingService.resetBoard(rosterDate);
        return ResponseEntity.ok(BaseResponse.success(null));
    }
}
