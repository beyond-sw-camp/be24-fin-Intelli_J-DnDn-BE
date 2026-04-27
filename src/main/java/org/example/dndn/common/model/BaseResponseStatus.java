package org.example.dndn.common.model;

import lombok.AllArgsConstructor;
import lombok.Getter;

@Getter
@AllArgsConstructor
public enum BaseResponseStatus {

    // 2000번대 성공
    SUCCESS(true, 2000, "요청이 성공했습니다"),

    // 3000번대 인증/인가 오류
    JWT_EXPIRED(false, 3001, "JWT 토큰이 만료되었습니다."),
    JWT_INVALID(false, 3002, "JWT 토큰이 유효하지 않습니다."),

    // 3100번대 회원가입 오류
    SIGNUP_DUPLICATE_EMAIL(false, 3101, "중복된 이메일입니다."),
    SIGNUP_INVALID_PASSWORD(false, 3102, "비밀번호는 대문자, 소문자, 숫자, 특수문자가 포함되어야 합니다."),
    SIGNUP_INVALID_UUID(false, 3103, "유효하지 않은 인증값입니다. 이메일 인증을 다시 시도해주세요."),

    // 3200번대 로그인 오류
    LOGIN_INVALID_USERINFO(false, 3201, "이메일이나 비밀번호를 확인해주세요."),

    // 5000번대 서버 오류
    FAIL(false, 5000, "요청이 실패했습니다."),
    AWS_UPLOAD_FAIL(false, 5001, "파일 업로드에 실패했습니다.");

    private final boolean success;
    private final int code;
    private final String message;
}