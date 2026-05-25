package org.example.dndncore.mobile.controller;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.example.dndncore.common.model.BaseResponse;
import org.example.dndncore.mobile.dto.MobileWorkerDto;
import org.example.dndncore.mobile.service.MobileWorkerService;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * 모바일 작업자 데이터 API.
 * 모든 엔드포인트는 Worker JWT(ROLE_WORKER) 인증이 필요하다.
 * workerIdx 는 JWT claim 에서 추출하며, 경로 파라미터로 받지 않는다(타인 정보 접근 차단).
 */
@RestController
@RequiredArgsConstructor
@RequestMapping("/mobile/worker")
public class MobileWorkerController {

    private final MobileWorkerService mobileWorkerService;

    /** MOBILE_WORKER_001 내 프로필 조회 */
    @GetMapping("/profile")
    public ResponseEntity<BaseResponse<MobileWorkerDto.ProfileRes>> profile() {
        Long workerIdx = currentWorkerIdx();
        return ResponseEntity.ok(BaseResponse.success(mobileWorkerService.getProfile(workerIdx)));
    }

    /** MOBILE_WORKER_002 오늘 현황 조회 (배정 구역 + 출퇴근 상태 + canClockIn/canClockOut) */
    @GetMapping("/today")
    public ResponseEntity<BaseResponse<MobileWorkerDto.TodayRes>> today() {
        Long workerIdx = currentWorkerIdx();
        return ResponseEntity.ok(BaseResponse.success(mobileWorkerService.getToday(workerIdx)));
    }

    /**
     * MOBILE_WORKER_003 출퇴근 처리.
     * action = CHECK_IN | CHECK_OUT
     * recognizedAt = "HH:mm" 또는 "HH:mm:ss" (null 이면 서버 현재 시각)
     */
    @PostMapping("/attendance")
    public ResponseEntity<BaseResponse<MobileWorkerDto.TodayRes>> attendance(
            @Valid @RequestBody MobileWorkerDto.AttendanceReq req
    ) {
        Long workerIdx = currentWorkerIdx();
        MobileWorkerDto.TodayRes res = mobileWorkerService.recordAttendance(
                workerIdx, req.getAction(), req.getRecognizedAt());
        return ResponseEntity.ok(BaseResponse.success(res));
    }

    /**
     * MOBILE_WORKER_004 출결 이력 조회.
     * days = 최근 N일 (기본 30, 최대 90)
     */
    @GetMapping("/attendance-history")
    public ResponseEntity<BaseResponse<List<MobileWorkerDto.AttendanceHistoryItemRes>>> attendanceHistory(
            @RequestParam(defaultValue = "30") int days
    ) {
        Long workerIdx = currentWorkerIdx();
        return ResponseEntity.ok(BaseResponse.success(
                mobileWorkerService.getAttendanceHistory(workerIdx, days)));
    }

    /** MOBILE_WORKER_005 안전 사고 이력 조회 */
    @GetMapping("/accidents")
    public ResponseEntity<BaseResponse<List<MobileWorkerDto.AccidentRes>>> accidents() {
        Long workerIdx = currentWorkerIdx();
        return ResponseEntity.ok(BaseResponse.success(mobileWorkerService.getAccidents(workerIdx)));
    }

    /** MOBILE_WORKER_006 서류 및 안전 현황 조회 */
    @GetMapping("/docs")
    public ResponseEntity<BaseResponse<List<MobileWorkerDto.DocRes>>> docs() {
        Long workerIdx = currentWorkerIdx();
        return ResponseEntity.ok(BaseResponse.success(mobileWorkerService.getDocs(workerIdx)));
    }

    /** MOBILE_WORKER_007 구역 배치이력 조회 */
    @GetMapping("/deployments")
    public ResponseEntity<BaseResponse<List<MobileWorkerDto.DeploymentRes>>> deployments() {
        Long workerIdx = currentWorkerIdx();
        return ResponseEntity.ok(BaseResponse.success(mobileWorkerService.getDeployments(workerIdx)));
    }

    // ─────────────────────────────────────────────────
    // 내부 헬퍼
    // ─────────────────────────────────────────────────

    /**
     * JwtFilter 가 WORKER 토큰을 파싱해 SecurityContext 에 설정한 workerIdx 추출.
     * principal = workerIdx (Long).
     */
    private Long currentWorkerIdx() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        return (Long) auth.getPrincipal();
    }
}
