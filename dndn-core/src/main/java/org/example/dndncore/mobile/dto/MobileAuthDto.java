package org.example.dndncore.mobile.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Builder;
import lombok.Getter;

public class MobileAuthDto {

    // feat : 모바일 로그인 응답
    @Getter
    @Builder
    @Schema(description = "모바일 로그인 응답")
    public static class LoginRes {
        @Schema(description = "액세스 토큰")
        private String accessToken;
        @Schema(description = "작업자 ID", example = "1")
        private Long workerIdx;
        @Schema(description = "이름", example = "홍길동")
        private String name;
        @Schema(description = "현장명", example = "OO현장")
        private String siteName;
        @Schema(description = "현장 코드", example = "SITE-01")
        private String siteCode;
        @Schema(description = "직종", example = "철근")
        private String jobType;
        @Schema(description = "직급", example = "반장")
        private String jobRank;
        @Schema(description = "소속 구분", example = "PARTNER")
        private String affiliationKind;
        @Schema(description = "고용 구분", example = "CONTRACT")
        private String employmentKind;
        @Schema(description = "전화번호", example = "010-1234-5678")
        private String phoneNumber;
        @Schema(description = "비상연락처", example = "010-1111-2222")
        private String emergencyContact;
        @Schema(description = "비상연락 관계", example = "배우자")
        private String emergencyRelation;
        @Schema(description = "혈액형", example = "A")
        private String bloodType;
        @Schema(description = "프로필 이미지 URL")
        private String profileImageUrl;
        @Schema(description = "근태 상태", example = "WORKING")
        private String attendanceStatus;
    }

    // feat : 모바일 로그인 요청
    @Getter
    @Schema(description = "모바일 로그인 요청")
    public static class LoginReq {
        @Schema(description = "이름", example = "홍길동")
        private String name;
        @Schema(description = "전화번호", example = "010-1234-5678")
        private String phone;
    }
}
