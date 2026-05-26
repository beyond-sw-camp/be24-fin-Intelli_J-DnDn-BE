package org.example.dndncore.mobile.controller;

import lombok.RequiredArgsConstructor;
import org.example.dndncore.common.model.BaseResponse;
import org.example.dndncore.mobile.dto.MobileAuthDto;
import org.example.dndncore.mobile.dto.MobileWorkerDto;
import org.example.dndncore.mobile.service.MobileWorkerService;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * 모바일 작업자 전용 REST API.
 *
 * <pre>
 *   POST  /mobile/auth/login              — 이름+전화번호 로그인 (인증 불필요)
 *   GET   /mobile/worker/profile          — 내 프로필
 *   GET   /mobile/worker/today            — 오늘 근태 + 배치 현황
 *   POST  /mobile/worker/attendance       — 출근(CHECK_IN) / 퇴근(CHECK_OUT)
 *   GET   /mobile/worker/attendance-history — 최근 N일 근태 이력
 *   GET   /mobile/worker/accidents        — 안전사고 이력
 *   GET   /mobile/worker/docs             — 보유 서류 목록
 *   GET   /mobile/worker/deployments      — 배치 이력
 * </pre>
 */
@RestController
@RequiredArgsConstructor
public class MobileWorkerController {

    private final MobileWorkerService mobileWorkerService;

    // ── 인증 불필요 ──────────────────────────────────────────────────────────────

    @PostMapping("/mobile/auth/login")
    public BaseResponse<MobileAuthDto.LoginRes> login(@RequestBody MobileAuthDto.LoginReq req) {
        return BaseResponse.success(mobileWorkerService.login(req.getName(), req.getPhone()));
    }

    // ── ROLE_WORKER 필요 ─────────────────────────────────────────────────────────

    @GetMapping("/mobile/worker/profile")
    public BaseResponse<MobileWorkerDto.ProfileRes> getProfile() {
        return BaseResponse.success(mobileWorkerService.getProfile(currentWorkerIdx()));
    }

    @GetMapping("/mobile/worker/today")
    public BaseResponse<MobileWorkerDto.TodayRes> getToday() {
        return BaseResponse.success(mobileWorkerService.getToday(currentWorkerIdx()));
    }

    @PostMapping("/mobile/worker/attendance")
    public BaseResponse<MobileWorkerDto.TodayRes> recordAttendance(
            @RequestBody MobileWorkerDto.AttendanceReq req) {
        return BaseResponse.success(
                mobileWorkerService.recordAttendance(
                        currentWorkerIdx(), req.getAction(), req.getRecognizedAt()));
    }

    @GetMapping("/mobile/worker/attendance-history")
    public BaseResponse<List<MobileWorkerDto.AttendanceHistoryItemRes>> getAttendanceHistory(
            @RequestParam(defaultValue = "30") int days) {
        return BaseResponse.success(
                mobileWorkerService.getAttendanceHistory(currentWorkerIdx(), days));
    }

    @GetMapping("/mobile/worker/accidents")
    public BaseResponse<List<MobileWorkerDto.AccidentRes>> getAccidents() {
        return BaseResponse.success(mobileWorkerService.getAccidents(currentWorkerIdx()));
    }

    @GetMapping("/mobile/worker/docs")
    public BaseResponse<List<MobileWorkerDto.DocRes>> getDocs() {
        return BaseResponse.success(mobileWorkerService.getDocs(currentWorkerIdx()));
    }

    @GetMapping("/mobile/worker/deployments")
    public BaseResponse<List<MobileWorkerDto.DeploymentRes>> getDeployments() {
        return BaseResponse.success(mobileWorkerService.getDeployments(currentWorkerIdx()));
    }

    // ─────────────────────────────────────────────────────────────────────────────

    private Long currentWorkerIdx() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        return (Long) auth.getPrincipal();
    }
}
