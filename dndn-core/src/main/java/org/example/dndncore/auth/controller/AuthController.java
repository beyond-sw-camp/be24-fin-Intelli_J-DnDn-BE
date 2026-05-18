package org.example.dndncore.auth.controller;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.example.dndncore.auth.model.dto.AuthDto;
import org.example.dndncore.auth.service.AuthService;
import org.example.dndncore.common.model.BaseResponse;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/auth")
@RequiredArgsConstructor
public class AuthController {

    private final AuthService authService;

    @PostMapping("/login")
    public ResponseEntity<BaseResponse<AuthDto.LoginRes>> login(@Valid @RequestBody AuthDto.LoginReq req) {
        return ResponseEntity.ok(BaseResponse.success(authService.login(req)));
    }

    @PutMapping("/password")
    public ResponseEntity<?> changePassword(@Valid @RequestBody AuthDto.ChangePasswordReq req) {
        authService.changePassword(req);
        return ResponseEntity.ok(BaseResponse.success("비밀번호가 변경되었습니다."));
    }
}
