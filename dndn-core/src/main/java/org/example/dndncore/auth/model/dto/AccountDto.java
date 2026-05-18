package org.example.dndncore.auth.model.dto;

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

        @Size(max = 20)
        private String phone;

        @Size(max = 100)
        private String email;
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

        @Size(max = 20)
        private String phone;

        @Size(max = 100)
        private String email;
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
        private String phone;
        private String email;
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
                    .phone(u.getPhone())
                    .email(u.getEmail())
                    .active(u.isActive())
                    .createdAt(u.getCreatedAt())
                    .updatedAt(u.getUpdatedAt())
                    .build();
        }
    }
}
