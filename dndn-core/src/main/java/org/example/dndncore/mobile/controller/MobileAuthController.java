package org.example.dndncore.mobile.controller;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.example.dndncore.common.model.BaseResponse;
import org.example.dndncore.mobile.dto.MobileAuthDto;
import org.example.dndncore.mobile.service.MobileWorkerService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 모바일 작업자 인증 API.
 * SystemUser(관리자) 인증({@code /auth/**})과 완전히 분리된 엔드포인트.
 */
@RestController
@RequiredArgsConstructor
@RequestMapping("/mobile/auth")
public class MobileAuthController {

    private final MobileWorkerService mobileWorkerService;

    /**
     * MOBILE_AUTH_001 작업자 로그인.
     * POST /mobile/auth/login
     * - 이름 + 전화번호 일치 여부 검증
     * - 오늘 근무 명단(attendance_record) 존재 여부 검증 (요구사항 1)
     * - Worker 전용 JWT 발급
     */
    @PostMapping("/login")
    public ResponseEntity<BaseResponse<MobileAuthDto.LoginRes>> login(
            @Valid @RequestBody MobileAuthDto.LoginReq req
    ) {
        MobileAuthDto.LoginRes res = mobileWorkerService.login(req.getName(), req.getPhone());
        return ResponseEntity.ok(BaseResponse.success(res));
    }
}
