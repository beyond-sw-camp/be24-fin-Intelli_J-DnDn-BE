package org.example.dndncore.auth.model.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Builder;
import lombok.Getter;
import org.example.dndncore.auth.model.entity.SystemUser;
import org.example.dndncore.auth.model.enums.UserRole;

import java.time.LocalDateTime;

public class AccountDto {

    @Getter
    @Schema(description = "계정 생성 요청")
    public static class CreateReq {
        @Schema(description = "로그인 ID", example = "worker01")
        @NotBlank
        @Size(max = 50)
        private String loginId;

        @Schema(description = "비밀번호", example = "Password1234!")
        @NotBlank
        @Size(min = 8, max = 100)
        private String password;

        @Schema(description = "이름", example = "홍길동")
        @NotBlank
        @Size(max = 50)
        private String name;

        @Schema(description = "권한")
        @NotNull
        private UserRole role;

        @Schema(description = "현장 코드", example = "SITE-01")
        @Size(max = 50)
        private String siteCode;

        @Schema(description = "공종", example = "토목")
        @Size(max = 80)
        private String trade;

        @Schema(description = "전화번호", example = "010-1234-5678")
        @Size(max = 20)
        private String phone;

        @Schema(description = "이메일", example = "user@example.com")
        @Size(max = 100)
        private String email;
    }

    @Getter
    @Schema(description = "계정 수정 요청")
    public static class UpdateReq {
        @Schema(description = "이름", example = "홍길동")
        @NotBlank
        @Size(max = 50)
        private String name;

        @Schema(description = "권한")
        @NotNull
        private UserRole role;

        @Schema(description = "현장 코드", example = "SITE-01")
        @Size(max = 50)
        private String siteCode;

        @Schema(description = "공종", example = "토목")
        @Size(max = 80)
        private String trade;

        @Schema(description = "활성화 여부", example = "true")
        @NotNull
        private Boolean active;

        @Schema(description = "전화번호", example = "010-1234-5678")
        @Size(max = 20)
        private String phone;

        @Schema(description = "이메일", example = "user@example.com")
        @Size(max = 100)
        private String email;
    }

    @Getter
    @Builder
    @Schema(description = "계정 응답")
    public static class Res {
        @Schema(description = "계정 ID", example = "1")
        private Long idx;
        @Schema(description = "로그인 ID", example = "worker01")
        private String loginId;
        @Schema(description = "이름", example = "홍길동")
        private String name;
        @Schema(description = "권한")
        private UserRole role;
        @Schema(description = "현장 코드", example = "SITE-01")
        private String siteCode;
        @Schema(description = "공종", example = "토목")
        private String trade;
        @Schema(description = "전화번호", example = "010-1234-5678")
        private String phone;
        @Schema(description = "이메일", example = "user@example.com")
        private String email;
        @Schema(description = "활성화 여부", example = "true")
        private boolean active;
        @Schema(description = "생성 시각")
        private LocalDateTime createdAt;
        @Schema(description = "수정 시각")
        private LocalDateTime updatedAt;

        public static Res from(SystemUser u) {
            return Res.builder()
                    .idx(u.getIdx())
                    .loginId(u.getLoginId())
                    .name(u.getName())
                    .role(u.getRole())
                    .siteCode(u.getSiteCode())
                    .trade(u.getTrade())
                    .phone(u.getPhone())
                    .email(u.getEmail())
                    .active(u.isActive())
                    .createdAt(u.getCreatedAt())
                    .updatedAt(u.getUpdatedAt())
                    .build();
        }
    }
}
