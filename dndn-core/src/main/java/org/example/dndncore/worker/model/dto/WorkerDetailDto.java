package org.example.dndncore.worker.model.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.*;
import org.example.dndncore.staffing.model.StaffingLog;
import org.example.dndncore.worker.model.entity.AttendanceRecord;
import org.example.dndncore.worker.model.entity.SafetyAccident;
import org.example.dndncore.worker.model.entity.Worker;
import org.example.dndncore.worker.model.entity.WorkerDocument;
import org.example.dndncore.worker.model.enums.AffiliationKind;
import org.example.dndncore.worker.model.enums.AttendanceStatus;
import org.example.dndncore.worker.model.enums.EmploymentKind;
import org.example.dndncore.worker.model.enums.JobRank;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;

public class WorkerDetailDto {

    /**
     * MANAGEMENT_004 피로도 산출 상세 — 항목별 점수 및 설명 문자열.
     * {@link org.example.dndncore.worker.service.FatigueCalculationService#recalculateAndPersist(Long, LocalDate)} 가 채운다.
     */
    @Getter
    @NoArgsConstructor
    @AllArgsConstructor
    @Builder
    @Schema(description = "피로도 요약 DTO")
    public static class FatigueSummaryRes {
        @Schema(description = "기준 일자", example = "2026-05-27")
        private LocalDate referenceDate;
        @Schema(description = "산정 시각", example = "2026-05-27T14:30:00")
        private LocalDateTime calculatedAt;
        @Schema(description = "총점(100점 상한)", example = "65")
        private int totalScore;
        @Schema(description = "상한 적용 전 항목 합", example = "85")
        private int rawScoreSum;
        @Schema(description = "상한 초과분", example = "20")
        private int cappedRemainderLost;
        @Schema(description = "고위험 근로자 여부", example = "false")
        private boolean highRiskWorker;
        @Schema(description = "안전 사고 점수", example = "10")
        private int accidentScore;
        @Schema(description = "최근 30일 사고 여부", example = "false")
        private boolean accidentOccurredLast30Days;
        @Schema(description = "사고 상세", example = "경미한 찰과상")
        private String accidentDetail;
        @Schema(description = "연속 근무 점수", example = "15")
        private int streakScore;
        @Schema(description = "현장 연속 근무일 수", example = "12")
        private int onsiteStreakDays;
        @Schema(description = "연속 근무 상세", example = "12일 연속 근무")
        private String streakDetail;
        @Schema(description = "야간 근무 점수", example = "5")
        private int overnightScore;
        @Schema(description = "야간 근무 상세", example = "야간 근무 없음")
        private String overnightDetail;
        @Schema(description = "공종 위험도 점수", example = "20")
        private int tradeRiskScore;
        @Schema(description = "공종 카테고리", example = "HIGH_RISK")
        private String tradeCategoryKey;
        @Schema(description = "공종 위험도 상세", example = "고위험 공종")
        private String tradeDetail;
        @Schema(description = "점수 상한값", example = "100")
        private int scoreCap;
        @Schema(description = "고위험 기준값", example = "80")
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
    @Schema(description = "작업자 프로필 응답 DTO")
    public static class ProfileRes {
        @Schema(description = "작업자 ID", example = "1")
        private Long idx;
        @Schema(description = "작업자 이름", example = "홍길동")
        private String name;
        @Schema(description = "소속 구분", example = "DIRECT")
        private AffiliationKind affiliationKind;
        @Schema(description = "공종", example = "목공")
        private String trade;
        @Schema(description = "직급", example = "TEAM_LEAD")
        private JobRank jobRank;
        @Schema(description = "현장", example = "강남구 재건축 A공구")
        private String site;
        @Schema(description = "연락처", example = "010-1234-5678")
        private String phone;
        @Schema(description = "비상 연락처", example = "010-9999-8888")
        private String emergencyPhone;
        @Schema(description = "비상 연락 관계", example = "배우자")
        private String emergencyRelation;
        @Schema(description = "혈액형", example = "A형")
        private String bloodType;
        @Schema(description = "등록 일자", example = "2024-01-15")
        private LocalDate registeredAt;
        @Schema(description = "프로필 이미지 URL", example = "https://example.com/profile/1.jpg")
        private String profileImageUrl;
        @Schema(description = "고용 구분", example = "REGULAR")
        private EmploymentKind employmentKind;
        @Schema(description = "당월 누적 공수", example = "15.5")
        private BigDecimal monthTotalMan;
        @Schema(description = "피로도 요약")
        private FatigueSummaryRes fatigue;

        public static ProfileRes from(Worker w, EmploymentKind rosterEmploymentKind, FatigueSummaryRes fatigue) {
            return ProfileRes.builder()
                    .idx(w.getIdx())
                    .name(w.getName())
                    .affiliationKind(w.getAffiliationKind())
                    .trade(w.getTrade())
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
    @Schema(description = "서류 응답 DTO")
    public static class DocRes {
        @Schema(description = "서류 ID", example = "1")
        private Long idx;
        @Schema(description = "서류 제목", example = "기초안전보건교육 이수증")
        private String title;
        @Schema(description = "파일 다운로드 URL", example = "https://example.com/docs/1.pdf")
        private String fileUrl;
        @Schema(description = "저장 파일명", example = "doc_20240115_abc123.pdf")
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
    @Schema(description = "출결 기록 응답 DTO")
    public static class AttendanceRes {
        @Schema(description = "일자", example = "2026-05-27")
        private LocalDate date;
        @Schema(description = "출근 시각", example = "08:00:00", nullable = true)
        private LocalTime clockIn;
        @Schema(description = "퇴근 시각", example = "18:00:00", nullable = true)
        private LocalTime clockOut;
        @Schema(description = "고용 구분", example = "REGULAR")
        private EmploymentKind employmentKind;
        @Schema(description = "출결 상태", example = "PRESENT")
        private AttendanceStatus attendanceStatus;
        @Schema(description = "공수", example = "1.0")
        private BigDecimal manDays;
        @Schema(description = "기본 구역명", example = "골조 공정", nullable = true)
        private String zoneMain;
        @Schema(description = "상세 구역명", example = "A동 3층", nullable = true)
        private String zoneSub;
        @Schema(description = "구역 표시", example = "골조 공정 · A동 3층", nullable = true)
        private String zoneDisplay;

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

        public static AttendanceRes from(AttendanceRecord a, String zoneMainTitle, String zoneSubTitle) {
            return AttendanceRes.builder()
                    .date(a.getWorkDate())
                    .clockIn(a.getClockIn())
                    .clockOut(a.getClockOut())
                    .employmentKind(a.getEmploymentKind())
                    .attendanceStatus(a.getAttendanceStatus())
                    .manDays(a.getManDays())
                    .zoneMain(zoneMainTitle)
                    .zoneSub(zoneSubTitle)
                    .zoneDisplay(formatZoneLine(zoneMainTitle, zoneSubTitle))
                    .build();
        }
    }

    /** MANAGEMENT_007 구역 배치 확정 이력 1건 — {@code staffing_log} 기반 */
    @Getter
    @NoArgsConstructor
    @AllArgsConstructor
    @Builder
    @Schema(description = "배치 이력 응답 DTO")
    public static class DeploymentRes {
        @Schema(description = "이력 ID", example = "1")
        private Long idx;
        @Schema(description = "배치 일자", example = "2026-05-27")
        private LocalDate assignedAt;
        @Schema(description = "확정 시각", example = "2026-05-27T09:30:00")
        private LocalDateTime confirmedAt;
        @Schema(description = "기본 구역명", example = "골조 공정")
        private String zoneMain;
        @Schema(description = "상세 구역명", example = "A동 3층")
        private String zoneSub;
        @Schema(description = "구역 표시", example = "골조 공정 · A동 3층")
        private String zoneDisplay;
        @Schema(description = "공종명", example = "철근")
        private String tradeName;
        @Schema(description = "현장 코드", example = "SITE01")
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
    @Schema(description = "안전 사고 응답 DTO")
    public static class AccidentRes {
        @Schema(description = "사고 ID", example = "1")
        private Long idx;
        @Schema(description = "발생 일자", example = "2026-05-15")
        private LocalDate occurredAt;
        @Schema(description = "사고 유형", example = "추락")
        private String accidentType;
        @Schema(description = "기본 구역명", example = "골조 공정")
        private String zoneMain;
        @Schema(description = "상세 위치", example = "A동 3층")
        private String zoneSub;
        @Schema(description = "구역 표시", example = "골조 공정 · A동 3층")
        private String zoneDisplay;
        @Schema(description = "조치 결과", example = "병원 치료 후 휴무")
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
