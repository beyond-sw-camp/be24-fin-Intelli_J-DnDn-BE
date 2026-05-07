package org.example.dndn.auth.model.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.Getter;
import lombok.Builder;
import org.example.dndn.auth.model.enums.UserRole;

public class AuthDto {

    @Getter
    public static class LoginReq {
        @NotBlank
        private String loginId;
        @NotBlank
        private String password;
    }

    @Getter
    @Builder
    public static class LoginRes {
        private String accessToken;
        private Long userIdx;
        private String name;
        private UserRole role;
        private String siteCode;
        private String trade;
    }
}
