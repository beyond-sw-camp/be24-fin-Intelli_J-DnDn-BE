package org.example.dndncore.auth.controller;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.ArraySchema;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.example.dndncore.auth.model.dto.AccountRequestDto;
import org.example.dndncore.auth.model.enums.RequestStatus;
import org.example.dndncore.auth.service.AccountRequestService;
import org.example.dndncore.common.model.BaseResponse;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@Tag(name = "Account Request", description = "계정 생성 요청 API")
@RestController
@RequiredArgsConstructor
public class AccountRequestController {

    private final AccountRequestService requestService;

    /** 계정 생성 요청 제출 — 인증된 사용자 누구나 가능. */
    @PostMapping("/account-requests")
    @Operation(summary = "계정 생성 요청 제출", description = "인증된 사용자가 계정 생성 요청을 제출합니다.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "제출 성공",
                    content = @Content(schema = @Schema(implementation = AccountRequestDto.Res.class))),
            @ApiResponse(responseCode = "400", description = "잘못된 요청"),
            @ApiResponse(responseCode = "500", description = "서버 오류")
    })
    public ResponseEntity<BaseResponse<AccountRequestDto.Res>> create(
            @AuthenticationPrincipal Long requesterIdx,
            @Valid @RequestBody AccountRequestDto.CreateReq req) {
        return ResponseEntity.ok(BaseResponse.success(requestService.create(requesterIdx, req)));
    }

    /** [ADMIN] 요청 목록 조회. status 파라미터로 필터링 가능 (PENDING/APPROVED/REJECTED). */
    @GetMapping("/admin/account-requests")
    @Operation(summary = "계정 요청 목록 조회", description = "관리자가 계정 생성 요청 목록을 조회합니다.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "조회 성공",
                    content = @Content(array = @ArraySchema(schema = @Schema(implementation = AccountRequestDto.Res.class)))),
            @ApiResponse(responseCode = "500", description = "서버 오류")
    })
    public ResponseEntity<BaseResponse<List<AccountRequestDto.Res>>> getAll(
            @Parameter(description = "요청 상태 필터", example = "PENDING")
            @RequestParam(required = false) RequestStatus status) {
        return ResponseEntity.ok(BaseResponse.success(requestService.getAll(status)));
    }

    /** [ADMIN] 요청 단건 조회. */
    @GetMapping("/admin/account-requests/{idx}")
    @Operation(summary = "계정 요청 단건 조회", description = "관리자가 계정 생성 요청 상세를 조회합니다.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "조회 성공",
                    content = @Content(schema = @Schema(implementation = AccountRequestDto.Res.class))),
            @ApiResponse(responseCode = "404", description = "대상을 찾을 수 없음"),
            @ApiResponse(responseCode = "500", description = "서버 오류")
    })
    public ResponseEntity<BaseResponse<AccountRequestDto.Res>> getOne(@PathVariable Long idx) {
        return ResponseEntity.ok(BaseResponse.success(requestService.getOne(idx)));
    }

    /** [ADMIN] 요청 승인 — 초기 비밀번호와 함께 계정 자동 생성. */
    @PutMapping("/admin/account-requests/{idx}/approve")
    @Operation(summary = "계정 요청 승인", description = "관리자가 계정 생성 요청을 승인합니다.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "승인 성공",
                    content = @Content(schema = @Schema(implementation = AccountRequestDto.Res.class))),
            @ApiResponse(responseCode = "400", description = "잘못된 요청"),
            @ApiResponse(responseCode = "404", description = "대상을 찾을 수 없음"),
            @ApiResponse(responseCode = "500", description = "서버 오류")
    })
    public ResponseEntity<BaseResponse<AccountRequestDto.Res>> approve(
            @Parameter(description = "요청 ID", required = true, example = "1")
            @PathVariable Long idx,
            @Valid @RequestBody AccountRequestDto.ApproveReq req) {
        return ResponseEntity.ok(BaseResponse.success(requestService.approve(idx, req)));
    }

    /** [ADMIN] 요청 거절. */
    @PutMapping("/admin/account-requests/{idx}/reject")
    @Operation(summary = "계정 요청 거절", description = "관리자가 계정 생성 요청을 거절합니다.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "거절 성공",
                    content = @Content(schema = @Schema(implementation = AccountRequestDto.Res.class))),
            @ApiResponse(responseCode = "404", description = "대상을 찾을 수 없음"),
            @ApiResponse(responseCode = "500", description = "서버 오류")
    })
    public ResponseEntity<BaseResponse<AccountRequestDto.Res>> reject(
            @Parameter(description = "요청 ID", required = true, example = "1")
            @PathVariable Long idx,
            @RequestBody(required = false) AccountRequestDto.RejectReq req) {
        return ResponseEntity.ok(BaseResponse.success(
                requestService.reject(idx, req != null ? req : new AccountRequestDto.RejectReq())));
    }
}
