package org.example.dndncore.mobile.dto;

import lombok.Builder;
import lombok.Getter;

public class MobileWorkerDto {

    /** GET /mobile/worker/profile */
    @Getter
    @Builder
    public static class ProfileRes {
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

    /** GET /mobile/worker/today */
    @Getter
    @Builder
    public static class TodayRes {
        private String workDate;
        private AttendanceSnapshotRes attendance;
        private PlacementRes placement;
        private boolean canClockIn;
        private boolean canClockOut;
        private boolean rostered;
    }

    /** 출퇴근 스냅샷 (TodayRes 내부) */
    @Getter
    @Builder
    public static class AttendanceSnapshotRes {
        private String attendanceStatus;
        private String clockIn;
        private String clockOut;
    }

    /** 배치 구역 정보 (TodayRes 내부) */
    @Getter
    @Builder
    public static class PlacementRes {
        private String zoneMain;
        private String zoneSub;
        private String zoneDisplay;
        private String location;
        private String assignedTrade;
        private String workTime;
        private boolean assignmentConfirmed;
    }

    /** GET /mobile/worker/attendance-history 항목 */
    @Getter
    @Builder
    public static class AttendanceHistoryItemRes {
        private String id;
        private String date;
        private String clockIn;
        private String clockOut;
        private String attendanceStatus;
        private String zoneMain;
        private String zoneSub;
        private String zoneDisplay;
        private String assignedTrade;
    }

    /** GET /mobile/worker/accidents 항목 */
    @Getter
    @Builder
    public static class AccidentRes {
        private Long idx;
        private String occurredAt;
        private String accidentType;
        private String zoneMain;
        private String zoneSub;
        private String zoneDisplay;
        private String resolution;
    }

    /** GET /mobile/worker/docs 항목 */
    @Getter
    @Builder
    public static class DocRes {
        private Long idx;
        private String title;
        private String fileUrl;
        private String storedFileName;
    }

    /** GET /mobile/worker/deployments 항목 */
    @Getter
    @Builder
    public static class DeploymentRes {
        private Long idx;
        private String assignedAt;
        private String confirmedAt;
        private String zoneMain;
        private String zoneSub;
        private String zoneDisplay;
        private String tradeName;
        private String siteCode;
    }

    /** POST /mobile/worker/attendance 요청 */
    @Getter
    public static class AttendanceReq {
        private String action;       // CHECK_IN | CHECK_OUT
        private String recognizedAt; // HH:mm 또는 HH:mm:ss (없으면 서버 현재시각)
    }
}
