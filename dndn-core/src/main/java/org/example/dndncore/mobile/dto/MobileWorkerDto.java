package org.example.dndncore.mobile.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.util.List;

public class MobileWorkerDto {

    // ─────────────────────────────────────────────────
    // GET /mobile/worker/profile
    // ─────────────────────────────────────────────────

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

    // ─────────────────────────────────────────────────
    // GET /mobile/worker/today
    // ─────────────────────────────────────────────────

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

    @Getter
    @Builder
    public static class AttendanceSnapshotRes {
        private String attendanceStatus;
        private String clockIn;   // "HH:mm" 또는 null
        private String clockOut;  // "HH:mm" 또는 null
    }

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

    // ─────────────────────────────────────────────────
    // POST /mobile/worker/attendance
    // ─────────────────────────────────────────────────

    @Getter
    @NoArgsConstructor
    public static class AttendanceReq {
        /** CHECK_IN | CHECK_OUT */
        @NotBlank
        private String action;
        /** null 이면 서버 현재 시각 사용 */
        private String recognizedAt;
    }

    // ─────────────────────────────────────────────────
    // GET /mobile/worker/attendance-history
    // ─────────────────────────────────────────────────

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
        private String location;
        private String assignedTrade;
        private String workTime;
    }

    @Getter
    @Builder
    public static class AttendanceHistoryRes {
        private List<AttendanceHistoryItemRes> rows;
    }

    // ─────────────────────────────────────────────────
    // GET /mobile/worker/accidents
    // ─────────────────────────────────────────────────

    @Getter
    @Builder
    public static class AccidentRes {
        private Long idx;
        private String occurredAt;   // "YYYY-MM-DD"
        private String accidentType;
        private String zoneMain;
        private String zoneSub;
        private String zoneDisplay;
        private String resolution;
    }

    // ─────────────────────────────────────────────────
    // GET /mobile/worker/docs
    // ─────────────────────────────────────────────────

    @Getter
    @Builder
    public static class DocRes {
        private Long idx;
        private String title;
        private String fileUrl;
        private String storedFileName;
    }

    // ─────────────────────────────────────────────────
    // GET /mobile/worker/deployments
    // ─────────────────────────────────────────────────

    @Getter
    @Builder
    public static class DeploymentRes {
        private Long idx;
        private String assignedAt;   // "YYYY-MM-DD"
        private String confirmedAt;  // "YYYY-MM-DD HH:mm"
        private String zoneMain;
        private String zoneSub;
        private String zoneDisplay;
        private String tradeName;
        private String siteCode;
    }
}
