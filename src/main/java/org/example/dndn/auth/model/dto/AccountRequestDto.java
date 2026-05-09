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

    /** 승인 시 초기 비밀번호를 관리자가 지정. 미입력 시 임시 비밀번호 자동 생성. */
    @Getter
    @NoArgsConstructor
    public static class ApproveReq {
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
        /** 프론트 호환 — requesterName 과 동일 값. */
        private String name;
        private String requestedName;
        private String requestedLoginId;
        private UserRole requestedRole;
        /** 프론트 호환 — 요청자(requester) 의 권한. */
        private UserRole role;
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
                    .name(r.getRequester().getName())
                    .requestedName(r.getRequestedName())
                    .requestedLoginId(r.getRequestedLoginId())
                    .requestedRole(r.getRequestedRole())
                    .role(r.getRequester().getRole())
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
