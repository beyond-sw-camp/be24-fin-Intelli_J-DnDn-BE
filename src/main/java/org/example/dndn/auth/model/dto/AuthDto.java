package org.example.dndn.auth.model.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.Builder;
import lombok.NoArgsConstructor;
import org.example.dndn.auth.model.enums.LoginMode;
import org.example.dndn.auth.model.enums.UserRole;

public class AuthDto {

    @Getter
    public static class LoginReq {
        @NotBlank
        private String loginId;
        @NotBlank
        private String password;
        // 프론트 로그인 화면에서 선택한 탭(현장 / 관리자) 정보.
        private LoginMode loginMode;
    }

    @Getter
    @NoArgsConstructor
    public static class ChangePasswordReq {
        @NotBlank
        private String currentPassword;

        @NotBlank
        @Size(min = 8, max = 100)
        private String newPassword;
    }

    @Getter
    @Builder
    public static class LoginRes {
        private String accessToken;
        private Long userIdx;
        private Long projectId;
        private String name;
        private UserRole role;
        private String siteCode;
        private String trade;
    }
}
