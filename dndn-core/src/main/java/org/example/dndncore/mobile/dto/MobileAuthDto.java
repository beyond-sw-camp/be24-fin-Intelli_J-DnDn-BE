package org.example.dndncore.mobile.dto;

import lombok.Builder;
import lombok.Getter;

public class MobileAuthDto {

    /**
     * POST /mobile/auth/login 응답
     */
    @Getter
    @Builder
    public static class LoginRes {
        private String accessToken;
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
        private String attendanceStatus;
    }

    /**
     * POST /mobile/auth/login 요청
     */
    @Getter
    public static class LoginReq {
        private String name;
        private String phone;
    }
}
