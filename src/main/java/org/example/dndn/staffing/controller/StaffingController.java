package org.example.dndn.staffing.controller;

import lombok.RequiredArgsConstructor;
import org.example.dndn.common.model.BaseResponse;
import org.example.dndn.staffing.model.StaffingDto;
import org.example.dndn.staffing.service.StaffingService;
import org.springframework.format.annotation.DateTimeFormat;
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
    public ResponseEntity<BaseResponse<List<StaffingDto.ZoneMainRes>>> getZones() {
        List<StaffingDto.ZoneMainRes> dto = staffingService.loadZoneMainTree();
        return ResponseEntity.ok(BaseResponse.success(dto));
    }

    // STAFFING_004 — 상세 구역(ZoneSub) 정보 조회
    @GetMapping("/zones/{zoneSubIdx}")
    public ResponseEntity<BaseResponse<StaffingDto.ZoneSubRes>> getZoneSub(
            @PathVariable Long zoneSubIdx
    ) {
        StaffingDto.ZoneSubRes dto = staffingService.loadZoneSubDetail(zoneSubIdx);
        return ResponseEntity.ok(BaseResponse.success(dto));
    }

    /** STAFFING_005 — 상세 구역(ZoneSub) 제목·직종별 필요 인원 수정 */
    @PatchMapping("/zones/{zoneSubIdx}")
    public ResponseEntity<BaseResponse<Void>> patchZoneSub(
            @PathVariable Long zoneSubIdx,
            @RequestBody StaffingDto.ZoneUpdateReq req
    ) {
        staffingService.updateZoneSub(zoneSubIdx, req);
        return ResponseEntity.ok(BaseResponse.success(null));
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
