package org.example.dndncore.mobile.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

public class MobileAuthDto {

    /** POST /mobile/auth/login 요청 */
    @Getter
    @NoArgsConstructor
    public static class LoginReq {
        @NotBlank(message = "이름을 입력해 주세요.")
        private String name;

        @NotBlank(message = "전화번호를 입력해 주세요.")
        private String phone;
    }

    /** POST /mobile/auth/login 응답 */
    @Getter
    @Builder
    public static class LoginRes {
        private String accessToken;
        /** 이후 QR 페이로드·출퇴근 요청에 사용 */
        private Long workerIdx;
        private String name;
        private String siteName;
        private String siteCode;
        private String jobType;
        private String jobRank;
        private String affiliationKind;
        private String employmentKind;
        private String phoneNumber;
        private String emergencyContact;
        private String emergencyRelation;
        private String bloodType;
        private String profileImageUrl;
        /** 오늘 출결 상태 (PENDING / PRESENT / LATE / LEAVE / EARLY_LEAVE / ABSENT) */
        private String attendanceStatus;
    }
}
