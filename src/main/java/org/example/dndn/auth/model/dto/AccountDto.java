package org.example.dndn.auth.model.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Builder;
import lombok.Getter;
import org.example.dndn.auth.model.entity.SystemUser;
import org.example.dndn.auth.model.enums.UserRole;

import java.time.LocalDateTime;

public class AccountDto {

    @Getter
    public static class CreateReq {
        @NotBlank
        @Size(max = 50)
        private String loginId;

        @NotBlank
        @Size(min = 8, max = 100)
        private String password;

        @NotBlank
        @Size(max = 50)
        private String name;

        @NotNull
        private UserRole role;

        @Size(max = 50)
        private String siteCode;

        @Size(max = 80)
        private String trade;
    }

    @Getter
    public static class UpdateReq {
        @NotBlank
        @Size(max = 50)
        private String name;

        @NotNull
        private UserRole role;

        @Size(max = 50)
        private String siteCode;

        @Size(max = 80)
        private String trade;

        @NotNull
        private Boolean active;
    }

    @Getter
    public static class PasswordReq {
        @NotBlank
        @Size(min = 8, max = 100)
        private String newPassword;
    }

    @Getter
    @Builder
    public static class Res {
        private Long idx;
        private String loginId;
        private String name;
        private UserRole role;
        private String siteCode;
        private String trade;
        private boolean active;
        private LocalDateTime createdAt;
        private LocalDateTime updatedAt;

        public static Res from(SystemUser u) {
            return Res.builder()
                    .idx(u.getIdx())
                    .loginId(u.getLoginId())
                    .name(u.getName())
                    .role(u.getRole())
                    .siteCode(u.getSiteCode())
                    .trade(u.getTrade())
                    .active(u.isActive())
                    .createdAt(u.getCreatedAt())
                    .updatedAt(u.getUpdatedAt())
                    .build();
        }
    }
}
