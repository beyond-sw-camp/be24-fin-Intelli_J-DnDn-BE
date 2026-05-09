package org.example.dndn.auth.controller;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.example.dndn.auth.model.dto.AccountRequestDto;
import org.example.dndn.auth.model.enums.RequestStatus;
import org.example.dndn.auth.service.AccountRequestService;
import org.example.dndn.common.model.BaseResponse;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequiredArgsConstructor
public class AccountRequestController {

    private final AccountRequestService requestService;

    /** 계정 생성 요청 제출 — 인증된 사용자 누구나 가능. */
    @PostMapping("/account-requests")
    public ResponseEntity<BaseResponse<AccountRequestDto.Res>> create(
            @AuthenticationPrincipal Long requesterIdx,
            @Valid @RequestBody AccountRequestDto.CreateReq req) {
        return ResponseEntity.ok(BaseResponse.success(requestService.create(requesterIdx, req)));
    }

    /** [ADMIN] 요청 목록 조회. status 파라미터로 필터링 가능 (PENDING/APPROVED/REJECTED). */
    @GetMapping("/admin/account-requests")
    public ResponseEntity<BaseResponse<List<AccountRequestDto.Res>>> getAll(
            @RequestParam(required = false) RequestStatus status) {
        return ResponseEntity.ok(BaseResponse.success(requestService.getAll(status)));
    }

    /** [ADMIN] 요청 단건 조회. */
    @GetMapping("/admin/account-requests/{idx}")
    public ResponseEntity<BaseResponse<AccountRequestDto.Res>> getOne(@PathVariable Long idx) {
        return ResponseEntity.ok(BaseResponse.success(requestService.getOne(idx)));
    }

    /** [ADMIN] 요청 승인 — 초기 비밀번호와 함께 계정 자동 생성. */
    @PutMapping("/admin/account-requests/{idx}/approve")
    public ResponseEntity<BaseResponse<AccountRequestDto.Res>> approve(
            @PathVariable Long idx,
            @Valid @RequestBody AccountRequestDto.ApproveReq req) {
        return ResponseEntity.ok(BaseResponse.success(requestService.approve(idx, req)));
    }

    /** [ADMIN] 요청 거절. */
    @PutMapping("/admin/account-requests/{idx}/reject")
    public ResponseEntity<BaseResponse<AccountRequestDto.Res>> reject(
            @PathVariable Long idx,
            @RequestBody(required = false) AccountRequestDto.RejectReq req) {
        return ResponseEntity.ok(BaseResponse.success(
                requestService.reject(idx, req != null ? req : new AccountRequestDto.RejectReq())));
    }
}
