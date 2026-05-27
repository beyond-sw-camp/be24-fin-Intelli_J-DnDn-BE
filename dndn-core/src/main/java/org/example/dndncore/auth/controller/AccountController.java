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
import org.example.dndncore.auth.model.dto.AccountDto;
import org.example.dndncore.auth.service.AccountService;
import org.example.dndncore.common.model.BaseResponse;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * 계정 관리 — ADMIN 전용 (/admin/** 은 SecurityConfig 에서 ROLE_ADMIN 만 접근 허용).
 */
@Tag(name = "Account", description = "계정 관리 API")
@RestController
@RequestMapping("/admin/accounts")
@RequiredArgsConstructor
public class AccountController {

    private final AccountService accountService;

    /** 전체 계정 목록 조회. */
    @GetMapping
    @Operation(summary = "전체 계정 목록 조회", description = "관리자용 전체 계정 목록을 조회합니다.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "조회 성공",
                    content = @Content(array = @ArraySchema(schema = @Schema(implementation = AccountDto.Res.class)))),
            @ApiResponse(responseCode = "500", description = "서버 오류")
    })
    public ResponseEntity<BaseResponse<List<AccountDto.Res>>> getAll() {
        return ResponseEntity.ok(BaseResponse.success(accountService.getAll()));
    }

    /** 단일 계정 조회. */
    @GetMapping("/{idx}")
    @Operation(summary = "계정 단건 조회", description = "관리자용 계정 단건 정보를 조회합니다.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "조회 성공",
                    content = @Content(schema = @Schema(implementation = AccountDto.Res.class))),
            @ApiResponse(responseCode = "404", description = "대상을 찾을 수 없음"),
            @ApiResponse(responseCode = "500", description = "서버 오류")
    })
    public ResponseEntity<BaseResponse<AccountDto.Res>> getOne(@PathVariable Long idx) {
        return ResponseEntity.ok(BaseResponse.success(accountService.getOne(idx)));
    }

    /** 계정 생성. */
    @PostMapping
    @Operation(summary = "계정 생성", description = "관리자용 계정을 생성합니다.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "생성 성공",
                    content = @Content(schema = @Schema(implementation = AccountDto.Res.class))),
            @ApiResponse(responseCode = "400", description = "잘못된 요청"),
            @ApiResponse(responseCode = "500", description = "서버 오류")
    })
    public ResponseEntity<BaseResponse<AccountDto.Res>> create(@Valid @RequestBody AccountDto.CreateReq req) {
        return ResponseEntity.ok(BaseResponse.success(accountService.create(req)));
    }

    /** 계정 정보 수정 (이름/권한/현장/공종/활성화 여부). */
    @PutMapping("/{idx}")
    @Operation(summary = "계정 수정", description = "관리자용 계정 정보를 수정합니다.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "수정 성공",
                    content = @Content(schema = @Schema(implementation = AccountDto.Res.class))),
            @ApiResponse(responseCode = "400", description = "잘못된 요청"),
            @ApiResponse(responseCode = "404", description = "대상을 찾을 수 없음"),
            @ApiResponse(responseCode = "500", description = "서버 오류")
    })
    public ResponseEntity<BaseResponse<AccountDto.Res>> update(
            @Parameter(description = "계정 ID", required = true, example = "1")
            @PathVariable Long idx,
            @Valid @RequestBody AccountDto.UpdateReq req) {
        return ResponseEntity.ok(BaseResponse.success(accountService.update(idx, req)));
    }

    /** 이메일 중복 여부 확인 — true 면 사용 가능, false 면 이미 존재. */
    @GetMapping("/check-email")
    @Operation(summary = "이메일 중복 확인", description = "입력한 이메일의 사용 가능 여부를 확인합니다.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "확인 성공",
                    content = @Content(schema = @Schema(implementation = Boolean.class))),
            @ApiResponse(responseCode = "500", description = "서버 오류")
    })
    public ResponseEntity<BaseResponse<Boolean>> checkEmail(@RequestParam String email) {
        return ResponseEntity.ok(BaseResponse.success(accountService.isEmailAvailable(email)));
    }

    /** 계정 비활성화 (논리 삭제). */
    @DeleteMapping("/{idx}")
    @Operation(summary = "계정 삭제", description = "관리자용 계정을 비활성화합니다.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "삭제 성공"),
            @ApiResponse(responseCode = "404", description = "대상을 찾을 수 없음"),
            @ApiResponse(responseCode = "500", description = "서버 오류")
    })
    public ResponseEntity<BaseResponse<Void>> delete(@PathVariable Long idx) {
        accountService.delete(idx);
        return ResponseEntity.ok(BaseResponse.success(null));
    }
}
