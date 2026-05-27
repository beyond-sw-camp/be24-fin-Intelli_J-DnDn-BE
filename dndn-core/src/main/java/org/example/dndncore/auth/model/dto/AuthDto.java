package org.example.dndncore.auth.model.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.Builder;
import lombok.NoArgsConstructor;
import org.example.dndncore.auth.model.enums.LoginMode;
import org.example.dndncore.auth.model.enums.UserRole;

public class AuthDto {

    @Getter
    @Schema(description = "로그인 요청")
    public static class LoginReq {
        @Schema(description = "로그인 ID", example = "admin")
        @NotBlank
        private String loginId;
        @Schema(description = "비밀번호", example = "Password1234!")
        @NotBlank
        private String password;
        // 프론트 로그인 화면에서 선택한 탭(현장 / 관리자) 정보.
        @Schema(description = "로그인 모드", example = "ADMIN")
        private LoginMode loginMode;
    }

    @Getter
    @NoArgsConstructor
    @Schema(description = "비밀번호 변경 요청")
    public static class ChangePasswordReq {
        @Schema(description = "현재 비밀번호", example = "Password1234!")
        @NotBlank
        private String currentPassword;

        @Schema(description = "새 비밀번호", example = "Password12345!")
        @NotBlank
        @Size(min = 8, max = 100)
        private String newPassword;
    }

    @Getter
    @Builder
    @Schema(description = "로그인 응답")
    public static class LoginRes {
        @Schema(description = "액세스 토큰")
        private String accessToken;
        @Schema(description = "사용자 ID", example = "1")
        private Long userIdx;
        @Schema(description = "프로젝트 ID", example = "1")
        private Long projectId;
        @Schema(description = "사용자 이름", example = "홍길동")
        private String name;
        @Schema(description = "권한")
        private UserRole role;
        @Schema(description = "현장 코드", example = "SITE-01")
        private String siteCode;
        @Schema(description = "공종", example = "토목")
        private String trade;
        @Schema(description = "초기 업로드 필요 여부", example = "false")
        private Boolean needsInitialUpload;
    }
}
