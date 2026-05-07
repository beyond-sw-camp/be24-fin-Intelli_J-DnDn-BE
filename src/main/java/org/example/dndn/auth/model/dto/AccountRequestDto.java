package org.example.dndn.auth.model.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.example.dndn.auth.model.entity.AccountRequest;
import org.example.dndn.auth.model.enums.RequestStatus;
import org.example.dndn.auth.model.enums.UserRole;

import java.time.LocalDateTime;

public class AccountRequestDto {

    @Getter
    public static class CreateReq {
        @NotBlank
        @Size(max = 50)
        private String requestedName;

        @NotBlank
        @Size(max = 50)
        private String requestedLoginId;

        @NotNull
        private UserRole requestedRole;

        @Size(max = 50)
        private String siteCode;

        @Size(max = 80)
        private String trade;
    }

    /** 승인 시 초기 비밀번호를 관리자가 지정. */
    @Getter
    public static class ApproveReq {
        @NotBlank
        @Size(min = 8, max = 100)
        private String initialPassword;
    }

    @Getter
    @NoArgsConstructor
    public static class RejectReq {
        @Size(max = 300)
        private String note;
    }

    @Getter
    @Builder
    public static class Res {
        private Long idx;
        private Long requesterIdx;
        private String requesterName;
        private String requestedName;
        private String requestedLoginId;
        private UserRole requestedRole;
        private String siteCode;
        private String trade;
        private RequestStatus status;
        private String note;
        private LocalDateTime createdAt;
        private LocalDateTime updatedAt;

        public static Res from(AccountRequest r) {
            return Res.builder()
                    .idx(r.getIdx())
                    .requesterIdx(r.getRequester().getIdx())
                    .requesterName(r.getRequester().getName())
                    .requestedName(r.getRequestedName())
                    .requestedLoginId(r.getRequestedLoginId())
                    .requestedRole(r.getRequestedRole())
                    .siteCode(r.getSiteCode())
                    .trade(r.getTrade())
                    .status(r.getStatus())
                    .note(r.getNote())
                    .createdAt(r.getCreatedAt())
                    .updatedAt(r.getUpdatedAt())
                    .build();
        }
    }
}
