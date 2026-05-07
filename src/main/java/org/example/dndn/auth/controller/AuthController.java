package org.example.dndn.auth.controller;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.example.dndn.auth.model.dto.AuthDto;
import org.example.dndn.auth.service.AuthService;
import org.example.dndn.common.model.BaseResponse;
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
}
