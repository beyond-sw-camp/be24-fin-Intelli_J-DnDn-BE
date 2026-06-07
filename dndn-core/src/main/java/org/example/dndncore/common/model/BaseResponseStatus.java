package org.example.dndncore.common.model;

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
    LOGIN_INVALID_USERINFO(false, 3010, "아이디나 비밀번호를 확인하세요."),
    LOGIN_ROLE_NOT_ALLOWED_FOR_SITE(false, 3011, "현장 로그인은 현장 권한 계정만 사용할 수 있습니다."),
    LOGIN_ROLE_NOT_ALLOWED_FOR_ADMIN(false, 3012, "관리자 로그인은 본사 또는 시스템 관리자 계정만 사용할 수 있습니다."),
    AUTH_NOT_AUTHENTICATED(false, 3013, "인증된 사용자 정보를 찾을 수 없습니다."),
    LOGIN_ACCOUNT_DEACTIVATED(false, 3014, "비활성화된 계정입니다."),

    // 3100번대 회원가입/계정 오류
    SIGNUP_DUPLICATE_EMAIL(false, 3101, "중복된 이메일입니다."),
    SIGNUP_INVALID_PASSWORD(false, 3102, "비밀번호는 대문자, 소문자, 숫자, 특수문자가 포함되어야 합니다."),
    SIGNUP_INVALID_UUID(false, 3103, "유효하지 않은 인증값입니다. 이메일 인증을 다시 시도해주세요."),
    ACCOUNT_DUPLICATE_LOGIN_ID(false, 3104, "이미 사용 중인 로그인 아이디입니다."),
    ACCOUNT_NOT_FOUND(false, 3105, "존재하지 않는 계정입니다."),
    ACCOUNT_REQUEST_NOT_FOUND(false, 3106, "존재하지 않는 계정 신청입니다."),
    ACCOUNT_REQUEST_ALREADY_PROCESSED(false, 3107, "이미 처리된 계정 신청입니다."),

    // 3150번대 모바일 작업자 오류
    MOBILE_WORKER_INVALID_CREDENTIALS(false, 3151, "이름 또는 전화번호가 일치하지 않습니다."),
    MOBILE_WORKER_NOT_ROSTERED(false, 3152, "오늘 근무 명단에 등록되지 않은 작업자입니다."),

    // 3200번대 작업자(Worker) 오류
    WORKER_SYNC_MISSING_SITE_CODE(false, 3201, "현장 코드가 누락되었습니다."),
    WORKER_SYNC_MISSING_DATE(false, 3202, "날짜 정보가 누락되었습니다."),
    WORKER_NOT_FOUND(false, 3203, "존재하지 않는 작업자입니다."),
    WORKER_ATTENDANCE_NOT_FOUND(false, 3204, "해당 일자 근태 기록을 찾을 수 없습니다."),
    WORKER_CLOCK_IN_REQUIRED(false, 3205, "출근 기록 없이 퇴근할 수 없습니다."),
    WORKER_SITE_MISMATCH(false, 3206, "요청 현장 코드와 작업자 소속 현장이 일치하지 않습니다."),

    // 3300번대 인력 배치(STAFFING) 오류
    STAFFING_ZONE_NOT_FOUND(false, 3301, "존재하지 않는 배치 구역입니다."),
    STAFFING_INVALID_REQUEST(false, 3302, "잘못된 배치 요청입니다."),
    STAFFING_INVALID_TITLE(false, 3303, "구역 이름을 입력해주세요."),
    STAFFING_WORKER_NOT_FOUND(false, 3304, "존재하지 않는 작업자입니다."),
    STAFFING_ALREADY_ASSIGNED(false, 3305, "이미 다른 구역에 배치된 작업자입니다."),
    STAFFING_INVALID_JOB_RANK(false, 3306, "작업자(WORKER) 등급만 배치할 수 있습니다."),
    ASSIGN_OVERFLOW(false, 3350, "투입 가능 인원을 초과했습니다."),
    STAFFING_WORKER_NOT_PERMITTED(false, 3351, "현재 계정 권한으로 배치할 수 없는 작업자입니다."),

    // 3400번대 문서 관리 오류
    DOCUMENT_DUPLICATE_MASTER(false, 3401, "마스터 공정표는 이미 등록되어 있습니다. 기존 문서를 먼저 삭제해주세요."),
    DOCUMENT_DUPLICATE_MILESTONE(false, 3402, "마일스톤 공정표는 이미 등록되어 있습니다. 기존 문서를 먼저 삭제해주세요."),
    DOCUMENT_DUPLICATE_WEIGHT(false, 3403, "보할 공정표는 이미 등록되어 있습니다. 기존 문서를 먼저 삭제해주세요."),
    DOCUMENT_PROJECT_NOT_FOUND(false, 3404, "존재하지 않는 프로젝트입니다."),
    DOCUMENT_FILE_EMPTY(false, 3405, "업로드 파일이 비어있습니다."),
    DOCUMENT_NOT_FOUND(false, 3406, "존재하지 않는 문서입니다."),
    DOCUMENT_FILE_READ_FAIL(false, 3407, "파일을 읽을 수 없습니다."),
    PROJECT_INVALID_SITE_CODE(false, 3450, "현장 코드는 XX-X 형식의 영문 대문자만 사용할 수 있습니다."),
    PROJECT_DUPLICATE_SITE_CODE(false, 3451, "이미 사용 중인 현장 코드입니다."),

    // 5000번대 서버 오류
    FAIL(false, 5000, "요청이 실패했습니다."),
    AWS_UPLOAD_FAIL(false, 5001, "파일 업로드에 실패했습니다.");

    private final boolean success;
    private final int code;
    private final String message;
}
