package org.example.dndn.worker.model.dto;

import lombok.*;
import org.example.dndn.staffing.model.StaffingLog;
import org.example.dndn.worker.model.entity.AttendanceRecord;
import org.example.dndn.worker.model.entity.SafetyAccident;
import org.example.dndn.worker.model.entity.Worker;
import org.example.dndn.worker.model.entity.WorkerDocument;
import org.example.dndn.worker.model.enums.AffiliationKind;
import org.example.dndn.worker.model.enums.AttendanceStatus;
import org.example.dndn.worker.model.enums.EmploymentKind;
import org.example.dndn.worker.model.enums.JobRank;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;

public class WorkerDetailDto {

    /**
     * MANAGEMENT_004 피로도 산출 상세 — 항목별 점수 및 설명 문자열.
     * {@link org.example.dndn.worker.service.FatigueCalculationService#recalculateAndPersist(Long, LocalDate)} 가 채운다.
     */
    @Getter
    @NoArgsConstructor
    @AllArgsConstructor
    @Builder
    public static class FatigueSummaryRes {
        private LocalDate referenceDate;
        private LocalDateTime calculatedAt;
        /** 100점 상한 적용 후 총점 */
        private int totalScore;
        /** 상한 적용 전 항목 합 */
        private int rawScoreSum;
        /** 상한 초과분(100 초과분, 없으면 0) */
        private int cappedRemainderLost;
        /** {@link #totalScore} ≥ 고위험 기준 여부 */
        private boolean highRiskWorker;
        private int accidentScore;
        private boolean accidentOccurredLast30Days;
        private String accidentDetail;
        private int streakScore;
        private int onsiteStreakDays;
        private String streakDetail;
        private int overnightScore;
        private String overnightDetail;
        private int tradeRiskScore;
        private String tradeCategoryKey;
        private String tradeDetail;
        private int scoreCap;
        private int highRiskThreshold;
    }

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
        private JobRank jobRank;
        private String site;
        private String phone;
        private String emergencyPhone;
        private String emergencyRelation;
        private String bloodType;
        private LocalDate registeredAt;
        private String profileImageUrl;
        private EmploymentKind employmentKind;
        private BigDecimal monthTotalMan;
        /** 피로도·항목별 점수 요약 */
        private FatigueSummaryRes fatigue;

        public static ProfileRes from(Worker w, EmploymentKind rosterEmploymentKind, FatigueSummaryRes fatigue) {
            return ProfileRes.builder()
                    .idx(w.getIdx())
                    .name(w.getName())
                    .affiliationKind(w.getAffiliationKind())
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
                    .fatigue(fatigue)
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
        /** 당일 고용 구분 (상용/일용) */
        private EmploymentKind employmentKind;
        private AttendanceStatus attendanceStatus;
        private BigDecimal manDays;

        public static AttendanceRes from(AttendanceRecord a) {
            return AttendanceRes.builder()
                    .date(a.getWorkDate())
                    .clockIn(a.getClockIn())
                    .clockOut(a.getClockOut())
                    .employmentKind(a.getEmploymentKind())
                    .attendanceStatus(a.getAttendanceStatus())
                    .manDays(a.getManDays())
                    .build();
        }
    }

    /** MANAGEMENT_007 구역 배치 확정 이력 1건 — {@code staffing_log} 기반 */
    @Getter
    @NoArgsConstructor
    @AllArgsConstructor
    @Builder
    public static class DeploymentRes {
        private Long idx;
        private LocalDate assignedAt;
        /** staffing_log.created_at — 가장 최근 확정 시각 (프론트 일자/시간 표시용) */
        private LocalDateTime confirmedAt;
        private String zoneMain;
        private String zoneSub;
        private String zoneDisplay;
        private String tradeName;
        private String siteCode;

        public static DeploymentRes from(StaffingLog log) {
            String zm = log.getZoneMainTitle();
            String zs = log.getZoneSubTitle();
            return DeploymentRes.builder()
                    .idx(log.getIdx())
                    .assignedAt(log.getWorkDate())
                    .confirmedAt(log.getCreatedAt())
                    .zoneMain(zm)
                    .zoneSub(zs)
                    .zoneDisplay(formatZoneLine(zm, zs))
                    .tradeName(log.getTradeName())
                    .siteCode(log.getSiteCode())
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
