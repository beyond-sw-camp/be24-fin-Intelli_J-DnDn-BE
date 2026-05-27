package org.example.dndncore.mobile.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Builder;
import lombok.Getter;

public class MobileWorkerDto {

    // feat : 작업자 프로필 응답
    @Getter
    @Builder
    @Schema(description = "작업자 프로필 응답")
    public static class ProfileRes {
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

    // feat : 오늘 근태 및 배치 응답
    @Getter
    @Builder
    @Schema(description = "오늘 근태 및 배치 응답")
    public static class TodayRes {
        @Schema(description = "근무일", example = "2026-05-27")
        private String workDate;
        @Schema(description = "근태 스냅샷")
        private AttendanceSnapshotRes attendance;
        @Schema(description = "배치 정보")
        private PlacementRes placement;
        @Schema(description = "출근 가능 여부", example = "true")
        private boolean canClockIn;
        @Schema(description = "퇴근 가능 여부", example = "false")
        private boolean canClockOut;
        @Schema(description = "배치 여부", example = "true")
        private boolean rostered;
    }

    // feat : 출퇴근 스냅샷
    @Getter
    @Builder
    @Schema(description = "출퇴근 스냅샷")
    public static class AttendanceSnapshotRes {
        @Schema(description = "근태 상태", example = "CHECKED_IN")
        private String attendanceStatus;
        @Schema(description = "출근 시각", example = "08:55")
        private String clockIn;
        @Schema(description = "퇴근 시각", example = "18:01")
        private String clockOut;
    }

    // feat : 배치 구역 정보
    @Getter
    @Builder
    @Schema(description = "배치 구역 정보")
    public static class PlacementRes {
        @Schema(description = "메인 구역", example = "지상 2층")
        private String zoneMain;
        @Schema(description = "서브 구역", example = "복도")
        private String zoneSub;
        @Schema(description = "구역 표시명", example = "지상 2층 · 복도")
        private String zoneDisplay;
        @Schema(description = "위치", example = "A동")
        private String location;
        @Schema(description = "배정 공종", example = "철근")
        private String assignedTrade;
        @Schema(description = "작업 시간", example = "09:00~18:00")
        private String workTime;
        @Schema(description = "배치 확정 여부", example = "true")
        private boolean assignmentConfirmed;
    }

    // feat : 근태 이력 항목 응답
    @Getter
    @Builder
    @Schema(description = "근태 이력 항목 응답")
    public static class AttendanceHistoryItemRes {
        @Schema(description = "이력 ID", example = "att-1")
        private String id;
        @Schema(description = "일자", example = "2026-05-27")
        private String date;
        @Schema(description = "출근 시각", example = "08:55")
        private String clockIn;
        @Schema(description = "퇴근 시각", example = "18:01")
        private String clockOut;
        @Schema(description = "근태 상태", example = "CHECKED_OUT")
        private String attendanceStatus;
        @Schema(description = "메인 구역", example = "지상 2층")
        private String zoneMain;
        @Schema(description = "서브 구역", example = "복도")
        private String zoneSub;
        @Schema(description = "구역 표시명", example = "지상 2층 · 복도")
        private String zoneDisplay;
        @Schema(description = "배정 공종", example = "철근")
        private String assignedTrade;
    }

    // feat : 안전사고 이력 항목 응답
    @Getter
    @Builder
    @Schema(description = "안전사고 이력 항목 응답")
    public static class AccidentRes {
        @Schema(description = "사고 ID", example = "1")
        private Long idx;
        @Schema(description = "발생 시각", example = "2026-05-27T10:20:00")
        private String occurredAt;
        @Schema(description = "사고 유형", example = "낙하")
        private String accidentType;
        @Schema(description = "메인 구역", example = "지상 2층")
        private String zoneMain;
        @Schema(description = "서브 구역", example = "복도")
        private String zoneSub;
        @Schema(description = "구역 표시명", example = "지상 2층 · 복도")
        private String zoneDisplay;
        @Schema(description = "조치 내용", example = "응급처치 및 병원 이송")
        private String resolution;
    }

    // feat : 보유 서류 항목 응답
    @Getter
    @Builder
    @Schema(description = "보유 서류 항목 응답")
    public static class DocRes {
        @Schema(description = "서류 ID", example = "1")
        private Long idx;
        @Schema(description = "서류 제목", example = "안전교육 이수증")
        private String title;
        @Schema(description = "파일 URL")
        private String fileUrl;
        @Schema(description = "저장 파일명", example = "doc-1.pdf")
        private String storedFileName;
    }

    // feat : 배치 이력 항목 응답
    @Getter
    @Builder
    @Schema(description = "배치 이력 항목 응답")
    public static class DeploymentRes {
        @Schema(description = "배치 ID", example = "1")
        private Long idx;
        @Schema(description = "배치 시각", example = "2026-05-27T08:30:00")
        private String assignedAt;
        @Schema(description = "확정 시각", example = "2026-05-27T08:40:00")
        private String confirmedAt;
        @Schema(description = "메인 구역", example = "지상 2층")
        private String zoneMain;
        @Schema(description = "서브 구역", example = "복도")
        private String zoneSub;
        @Schema(description = "구역 표시명", example = "지상 2층 · 복도")
        private String zoneDisplay;
        @Schema(description = "공종명", example = "철근")
        private String tradeName;
        @Schema(description = "현장 코드", example = "SITE-01")
        private String siteCode;
    }

    // feat : 출퇴근 기록 요청
    @Getter
    @Schema(description = "출퇴근 기록 요청")
    public static class AttendanceReq {
        // feat : 출퇴근 액션
        @Schema(description = "출퇴근 액션", example = "CHECK_IN")
        private String action;
        // feat : 인식 시각
        @Schema(description = "인식 시각(HH:mm 또는 HH:mm:ss)", example = "08:55")
        private String recognizedAt;
    }
}
