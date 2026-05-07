package org.example.dndn.auth.controller;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.example.dndn.auth.model.dto.AccountDto;
import org.example.dndn.auth.service.AccountService;
import org.example.dndn.common.model.BaseResponse;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * 계정 관리 — ADMIN 전용 (/admin/** 은 SecurityConfig 에서 ROLE_ADMIN 만 접근 허용).
 */
@RestController
@RequestMapping("/admin/accounts")
@RequiredArgsConstructor
public class AccountController {

    private final AccountService accountService;

    /** 전체 계정 목록 조회. */
    @GetMapping
    public ResponseEntity<BaseResponse<List<AccountDto.Res>>> getAll() {
        return ResponseEntity.ok(BaseResponse.success(accountService.getAll()));
    }

    /** 단일 계정 조회. */
    @GetMapping("/{idx}")
    public ResponseEntity<BaseResponse<AccountDto.Res>> getOne(@PathVariable Long idx) {
        return ResponseEntity.ok(BaseResponse.success(accountService.getOne(idx)));
    }

    /** 계정 생성. */
    @PostMapping
    public ResponseEntity<BaseResponse<AccountDto.Res>> create(@Valid @RequestBody AccountDto.CreateReq req) {
        return ResponseEntity.ok(BaseResponse.success(accountService.create(req)));
    }

    /** 계정 정보 수정 (이름/권한/현장/공종/활성화 여부). */
    @PutMapping("/{idx}")
    public ResponseEntity<BaseResponse<AccountDto.Res>> update(
            @PathVariable Long idx,
            @Valid @RequestBody AccountDto.UpdateReq req) {
        return ResponseEntity.ok(BaseResponse.success(accountService.update(idx, req)));
    }

    /** 비밀번호 변경. */
    @PutMapping("/{idx}/password")
    public ResponseEntity<BaseResponse<Void>> changePassword(
            @PathVariable Long idx,
            @Valid @RequestBody AccountDto.PasswordReq req) {
        accountService.changePassword(idx, req);
        return ResponseEntity.ok(BaseResponse.success(null));
    }

    /** 계정 비활성화 (논리 삭제). */
    @DeleteMapping("/{idx}")
    public ResponseEntity<BaseResponse<Void>> delete(@PathVariable Long idx) {
        accountService.delete(idx);
        return ResponseEntity.ok(BaseResponse.success(null));
    }
}
