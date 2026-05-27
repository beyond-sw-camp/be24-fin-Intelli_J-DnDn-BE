package org.example.dndncore.mobile.controller;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.ArraySchema;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
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
@Tag(name = "Mobile Worker", description = "모바일 작업자 API")
public class MobileWorkerController {

    private final MobileWorkerService mobileWorkerService;

    // ── 인증 불필요 ──────────────────────────────────────────────────────────────

    @PostMapping("/mobile/auth/login")
    @Operation(summary = "모바일 로그인", description = "이름과 전화번호로 모바일 작업자 로그인을 수행합니다.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "로그인 성공",
                    content = @Content(schema = @Schema(implementation = MobileAuthDto.LoginRes.class))),
            @ApiResponse(responseCode = "400", description = "잘못된 요청"),
            @ApiResponse(responseCode = "401", description = "인증 실패")
    })
    public BaseResponse<MobileAuthDto.LoginRes> login(@RequestBody MobileAuthDto.LoginReq req) {
        return BaseResponse.success(mobileWorkerService.login(req.getName(), req.getPhone()));
    }

    // feat : 모바일 프로필 조회 API

    @GetMapping("/mobile/worker/profile")
    @Operation(summary = "내 프로필 조회", description = "현재 인증된 작업자의 프로필을 조회합니다.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "조회 성공",
                    content = @Content(schema = @Schema(implementation = MobileWorkerDto.ProfileRes.class))),
            @ApiResponse(responseCode = "401", description = "인증 필요")
    })
    public BaseResponse<MobileWorkerDto.ProfileRes> getProfile() {
        return BaseResponse.success(mobileWorkerService.getProfile(currentWorkerIdx()));
    }

    @GetMapping("/mobile/worker/today")
    @Operation(summary = "오늘 근태 및 배치 조회", description = "오늘의 근태 상태와 배치 정보를 조회합니다.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "조회 성공",
                    content = @Content(schema = @Schema(implementation = MobileWorkerDto.TodayRes.class))),
            @ApiResponse(responseCode = "401", description = "인증 필요")
    })
    public BaseResponse<MobileWorkerDto.TodayRes> getToday() {
        return BaseResponse.success(mobileWorkerService.getToday(currentWorkerIdx()));
    }

    @PostMapping("/mobile/worker/attendance")
    @Operation(summary = "출퇴근 기록", description = "출근(CHECK_IN) 또는 퇴근(CHECK_OUT)을 기록합니다.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "기록 성공",
                    content = @Content(schema = @Schema(implementation = MobileWorkerDto.TodayRes.class))),
            @ApiResponse(responseCode = "400", description = "잘못된 요청"),
            @ApiResponse(responseCode = "401", description = "인증 필요")
    })
    public BaseResponse<MobileWorkerDto.TodayRes> recordAttendance(
            @RequestBody MobileWorkerDto.AttendanceReq req) {
        return BaseResponse.success(
                mobileWorkerService.recordAttendance(
                        currentWorkerIdx(), req.getAction(), req.getRecognizedAt()));
    }

    @GetMapping("/mobile/worker/attendance-history")
    @Operation(summary = "근태 이력 조회", description = "최근 N일간의 근태 이력을 조회합니다.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "조회 성공",
                    content = @Content(array = @ArraySchema(schema = @Schema(implementation = MobileWorkerDto.AttendanceHistoryItemRes.class)))),
            @ApiResponse(responseCode = "401", description = "인증 필요")
    })
    public BaseResponse<List<MobileWorkerDto.AttendanceHistoryItemRes>> getAttendanceHistory(
            @Parameter(description = "조회 일수", example = "30")
            @RequestParam(defaultValue = "30") int days) {
        return BaseResponse.success(
                mobileWorkerService.getAttendanceHistory(currentWorkerIdx(), days));
    }

    @GetMapping("/mobile/worker/accidents")
    @Operation(summary = "안전사고 이력 조회", description = "작업자의 안전사고 이력을 조회합니다.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "조회 성공",
                    content = @Content(array = @ArraySchema(schema = @Schema(implementation = MobileWorkerDto.AccidentRes.class)))),
            @ApiResponse(responseCode = "401", description = "인증 필요")
    })
    public BaseResponse<List<MobileWorkerDto.AccidentRes>> getAccidents() {
        return BaseResponse.success(mobileWorkerService.getAccidents(currentWorkerIdx()));
    }

    @GetMapping("/mobile/worker/docs")
    @Operation(summary = "보유 서류 조회", description = "작업자의 보유 서류 목록을 조회합니다.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "조회 성공",
                    content = @Content(array = @ArraySchema(schema = @Schema(implementation = MobileWorkerDto.DocRes.class)))),
            @ApiResponse(responseCode = "401", description = "인증 필요")
    })
    public BaseResponse<List<MobileWorkerDto.DocRes>> getDocs() {
        return BaseResponse.success(mobileWorkerService.getDocs(currentWorkerIdx()));
    }

    @GetMapping("/mobile/worker/deployments")
    @Operation(summary = "배치 이력 조회", description = "작업자의 배치 이력을 조회합니다.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "조회 성공",
                    content = @Content(array = @ArraySchema(schema = @Schema(implementation = MobileWorkerDto.DeploymentRes.class)))),
            @ApiResponse(responseCode = "401", description = "인증 필요")
    })
    public BaseResponse<List<MobileWorkerDto.DeploymentRes>> getDeployments() {
        return BaseResponse.success(mobileWorkerService.getDeployments(currentWorkerIdx()));
    }

    // feat : 인증 컨텍스트에서 현재 작업자 식별자 추출

    private Long currentWorkerIdx() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        return (Long) auth.getPrincipal();
    }
}
