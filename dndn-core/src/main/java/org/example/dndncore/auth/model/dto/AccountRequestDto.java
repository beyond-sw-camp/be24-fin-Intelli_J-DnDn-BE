package org.example.dndncore.auth.model.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.example.dndncore.auth.model.entity.AccountRequest;
import org.example.dndncore.auth.model.enums.RequestStatus;
import org.example.dndncore.auth.model.enums.UserRole;

import java.time.LocalDateTime;

public class AccountRequestDto {

    @Getter
    @Schema(description = "계정 생성 요청 입력")
    public static class CreateReq {
        @Schema(description = "요청 이름", example = "홍길동")
        @NotBlank
        @Size(max = 50)
        private String requestedName;

        @Schema(description = "요청 로그인 ID", example = "worker01")
        @NotBlank
        @Size(max = 50)
        private String requestedLoginId;

        @Schema(description = "요청 권한")
        @NotNull
        private UserRole requestedRole;

        @Schema(description = "현장 코드", example = "SITE-01")
        @Size(max = 50)
        private String siteCode;

        @Schema(description = "공종", example = "토목")
        @Size(max = 80)
        private String trade;
    }

    /** 승인 시 초기 비밀번호를 관리자가 지정. 미입력 시 임시 비밀번호 자동 생성. */
    @Getter
    @NoArgsConstructor
    @Schema(description = "계정 요청 승인 입력")
    public static class ApproveReq {
        @Schema(description = "초기 비밀번호", example = "Password1234!")
        @Size(min = 8, max = 100)
        private String initialPassword;
    }

    @Getter
    @NoArgsConstructor
    @Schema(description = "계정 요청 반려 입력")
    public static class RejectReq {
        @Schema(description = "거절 메모", example = "요청 정보가 누락되었습니다.")
        @Size(max = 300)
        private String note;
    }

    @Getter
    @Builder
    @Schema(description = "계정 요청 응답")
    public static class Res {
        @Schema(description = "요청 ID", example = "1")
        private Long idx;
        @Schema(description = "요청자 ID", example = "10")
        private Long requesterIdx;
        @Schema(description = "요청자 이름", example = "김철수")
        private String requesterName;
        /** 프론트 호환 — requesterName 과 동일 값. */
        @Schema(description = "요청자 이름(호환)", example = "김철수")
        private String name;
        @Schema(description = "요청 이름", example = "홍길동")
        private String requestedName;
        @Schema(description = "요청 로그인 ID", example = "worker01")
        private String requestedLoginId;
        @Schema(description = "요청 권한")
        private UserRole requestedRole;
        /** 프론트 호환 — 요청자(requester) 의 권한. */
        @Schema(description = "요청자 권한")
        private UserRole role;
        @Schema(description = "현장 코드", example = "SITE-01")
        private String siteCode;
        @Schema(description = "공종", example = "토목")
        private String trade;
        @Schema(description = "요청 상태")
        private RequestStatus status;
        @Schema(description = "메모", example = "요청 정보가 누락되었습니다.")
        private String note;
        @Schema(description = "생성 시각")
        private LocalDateTime createdAt;
        @Schema(description = "수정 시각")
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
