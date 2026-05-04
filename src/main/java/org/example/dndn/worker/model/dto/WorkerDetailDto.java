package org.example.dndn.worker.model.dto;

import lombok.*;
import org.example.dndn.worker.model.entity.*;
import org.example.dndn.worker.model.enums.AffiliationKind;
import org.example.dndn.worker.model.enums.AttendanceStatus;
import org.example.dndn.worker.model.enums.EmploymentKind;
import org.example.dndn.worker.model.enums.JobRank;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalTime;

public class WorkerDetailDto {

    /** 기본구역·상세구역 한 줄 표기 — 프론트 `formatWorkerZoneDisplay` 와 동일 규칙. */
    public static String formatZoneLine(String zoneMain, String zoneSub) {
        boolean hm = zoneMain != null && !zoneMain.isBlank();
        boolean hs = zoneSub != null && !zoneSub.isBlank();
        if (hm && hs) return zoneMain.trim() + " · " + zoneSub.trim();
        if (hm) return zoneMain.trim();
        if (hs) return zoneSub.trim();
        return null;
    }

    // MANAGEMENT_004 작업자 상세 프로필 조회 응답.
    @Getter
    @NoArgsConstructor
    @AllArgsConstructor
    @Builder
    public static class ProfileRes {
        private Long idx;
        private String name;
        private AffiliationKind affiliationKind;
        private String partnerCompany;
        private JobRank jobRank;
        private String site;
        private String phone;
        private String emergencyPhone;
        private String emergencyRelation;
        private String bloodType;
        private LocalDate registeredAt;
        private String profileImageUrl;
        private BigDecimal monthTotalMan;
        /** 서버 로컬 오늘({@code LocalDate.now}) {@code work_date} 근태 행의 고용 구분. 행이 없으면 null. */
        private EmploymentKind employmentKind;

        public static ProfileRes from(Worker w, EmploymentKind rosterEmploymentKind) {
            return ProfileRes.builder()
                    .idx(w.getIdx())
                    .name(w.getName())
                    .affiliationKind(w.getAffiliationKind())
                    .partnerCompany(w.getPartnerCompany())
                    .jobRank(w.getJobRank())
                    .site(w.getSite())
                    .phone(w.getPhone())
                    .emergencyPhone(w.getEmergencyPhone())
                    .emergencyRelation(w.getEmergencyRelation())
                    .bloodType(w.getBloodType())
                    .registeredAt(w.getRegisteredAt())
                    .profileImageUrl(w.getProfileImageUrl())
                    .monthTotalMan(w.getMonthTotalMan())
                    .employmentKind(rosterEmploymentKind)
                    .build();
        }
    }

    // MANAGEMENT_005 안전 및 서류 현황
    @Getter
    @NoArgsConstructor
    @AllArgsConstructor
    @Builder
    public static class DocRes {
        private Long idx;
        private String title;
        private String fileUrl;
        private String storedFileName;

        public static DocRes from(WorkerDocument d) {
            return DocRes.builder()
                    .idx(d.getIdx())
                    .title(d.getTitle())
                    .fileUrl(d.getFileUrl())
                    .storedFileName(d.getStoredFileName())
                    .build();
        }
    }

    // MANAGEMENT_006 출결 캘린더 1셀
    @Getter
    @NoArgsConstructor
    @AllArgsConstructor
    @Builder
    public static class AttendanceRes {
        private LocalDate date;
        private LocalTime clockIn;
        private LocalTime clockOut;
        private String zoneMain;
        private String zoneSub;
        /** 표시용 한 줄 — {@link WorkerDetailDto#formatZoneLine} */
        private String zoneDisplay;
        private String assignedTrade;
        /** 당일 고용 구분 (상용/일용) — {@link AttendanceRecord#getEmploymentKind()} */
        private EmploymentKind employmentKind;
        private AttendanceStatus attendanceStatus;
        private BigDecimal manDays;

        public static AttendanceRes from(AttendanceRecord a) {
            String zm = a.getZoneMain();
            String zs = a.getZoneSub();
            return AttendanceRes.builder()
                    .date(a.getWorkDate())
                    .clockIn(a.getClockIn())
                    .clockOut(a.getClockOut())
                    .zoneMain(zm)
                    .zoneSub(zs)
                    .zoneDisplay(formatZoneLine(zm, zs))
                    .assignedTrade(a.getAssignedTrade())
                    .employmentKind(a.getEmploymentKind())
                    .attendanceStatus(a.getAttendanceStatus())
                    .manDays(a.getManDays())
                    .build();
        }
    }

    /** MANAGEMENT_007 구역·공종 배치 요약 1건 — `worker_zone_history` 없이 {@link AttendanceRecord} 스냅샷 */
    @Getter
    @NoArgsConstructor
    @AllArgsConstructor
    @Builder
    public static class DeploymentRes {
        private Long idx;
        private LocalDate assignedAt;
        private String zoneMain;
        private String zoneSub;
        private String zoneDisplay;
        /** 공종 — {@link AttendanceRecord#getAssignedTrade()} */
        private String assignedTrade;
        /** 당일 고용 구분 — {@link AttendanceRecord#getEmploymentKind()} */
        private EmploymentKind employmentKind;

        public static DeploymentRes from(AttendanceRecord a) {
            String zm = a.getZoneMain();
            String zs = a.getZoneSub();
            return DeploymentRes.builder()
                    .idx(a.getIdx())
                    .assignedAt(a.getWorkDate())
                    .zoneMain(zm)
                    .zoneSub(zs)
                    .zoneDisplay(formatZoneLine(zm, zs))
                    .assignedTrade(a.getAssignedTrade())
                    .employmentKind(a.getEmploymentKind())
                    .build();
        }
    }

    // MANAGEMENT_008 제재 / 주의 이력
    @Getter
    @NoArgsConstructor
    @AllArgsConstructor
    @Builder
    public static class SanctionRes {
        private Long idx;
        private LocalDate occurredAt;
        private String type;
        private String reason;
        private String action;
        private boolean active;

        public static SanctionRes from(WorkerSanction s) {
            return SanctionRes.builder()
                    .idx(s.getIdx())
                    .occurredAt(s.getOccurredAt())
                    .type(s.getType())
                    .reason(s.getReason())
                    .action(s.getAction())
                    .active(s.isActive())
                    .build();
        }
    }

    // MANAGEMENT_009 안전 사고 이력 1건
    @Getter
    @NoArgsConstructor
    @AllArgsConstructor
    @Builder
    public static class AccidentRes {
        private Long idx;
        private LocalDate occurredAt;
        private String accidentType;
        private String zoneMain;
        private String zoneSub;
        private String zoneDisplay;
        private String resolution;

        public static AccidentRes from(SafetyAccident a) {
            String zm = a.getZoneMain();
            String zs = a.getZoneSub();
            return AccidentRes.builder()
                    .idx(a.getIdx())
                    .occurredAt(a.getOccurredAt())
                    .accidentType(a.getAccidentType())
                    .zoneMain(zm)
                    .zoneSub(zs)
                    .zoneDisplay(formatZoneLine(zm, zs))
                    .resolution(a.getResolution())
                    .build();
        }
    }
}
