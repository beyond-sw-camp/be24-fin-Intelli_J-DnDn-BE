package org.example.dndn.worker.model.dto;

import jakarta.validation.constraints.NotNull;
import lombok.*;
import org.example.dndn.worker.model.entity.AttendanceRecord;
import org.example.dndn.worker.model.entity.Worker;
import org.example.dndn.worker.model.enums.AffiliationKind;
import org.example.dndn.worker.model.enums.AttendanceStatus;
import org.example.dndn.worker.model.enums.EmploymentKind;
import org.example.dndn.worker.model.enums.JobRank;

import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;

public class WorkerDto {

    // MANAGEMENT_002 근무자 검색
    @Getter
    @Setter
    @AllArgsConstructor
    @NoArgsConstructor
    @Builder
    public static class SearchReq {
        private String siteCode;
        private LocalDate date;
        private AttendanceStatus attendanceStatus;
        private String partnerCompany;
        private String searchName;
    }

    // MANAGEMENT_010 게이트 출근 인식.
    @Getter
    @Setter
    @AllArgsConstructor
    @NoArgsConstructor
    @Builder
    public static class GateClockInReq {
        @NotNull
        private Long workerIdx;
        private LocalDate workDate;  // null 이면 서버 로컬 기준 오늘
        @NotNull
        private LocalTime recognizedAt;
        private String siteCode;     // 현장 구분 — 제공 시 worker.siteCode 불일치면 거부
    }

    // MANAGEMENT_011 게이트 퇴근 인식
    @Getter
    @Setter
    @AllArgsConstructor
    @NoArgsConstructor
    @Builder
    public static class GateClockOutReq {
        @NotNull
        private Long workerIdx;
        private LocalDate workDate;  // null 이면 서버 로컬 기준 오늘
        @NotNull
        private LocalTime recognizedAt;
        private String siteCode;     // 현장 구분 — 제공 시 worker.siteCode 불일치면 거부
    }

    // MANAGEMENT_001 인력 데이터 요약
    @Getter
    @NoArgsConstructor
    @AllArgsConstructor
    @Builder
    public static class SyncRes {
        private int created;
        private int updated;
        private int total;
        private int documentsSynced;
        private int sanctionsSynced;
        private int accidentsSynced;
        private int attendanceRecordsSynced;
    }

    // MANAGEMENT_010, MANAGEMENT_011 게이트 처리 직후 해당 일 근태 스냅샷.
    @Getter
    @NoArgsConstructor
    @AllArgsConstructor
    @Builder
    public static class GateAttendanceRes {
        private Long workerIdx;
        private LocalDate workDate;
        private LocalTime clockIn;
        private LocalTime clockOut;
        private AttendanceStatus attendanceStatus;
    }

    // 목록 상단 KPI (총 인원에 대한 출근/지각/퇴근/조퇴/결근)
    @Getter
    @NoArgsConstructor
    @AllArgsConstructor
    @Builder
    public static class StateCountRes {
        private int pending;
        private int present;
        private int late;
        /** 규정 퇴근 시각 이후 정상 퇴근 처리된 인원 */
        private int leave;
        private int earlyLeave;
        private int absent;
        private int total;
    }

    /**
     * MANAGEMENT_002/003 Worker 1행 조회.
     * 「상용/일용」은 조회일 {@link AttendanceRecord#getEmploymentKind()} → {@link #employmentKind}.
     * {@link #subLabel} 은 마스터 테이블에 저장되는 공종 라벨이다.
     */
    @Getter
    @NoArgsConstructor
    @AllArgsConstructor
    @Builder
    public static class WorkerRes {
        private Long idx;
        private String name;
        private String phone;
        private JobRank jobRank;
        private AffiliationKind affiliationKind;
        /** 중간 전문 건설사명 (예: 구산토건, 삼보이앤씨). 본사는 null. */
        private String partnerCompany;
        /** 공종별 협력업체명 (예: 태양목공, 대한철근). PARTNER 일 때만 사용. */
        private String partnerCompanyDetail;
        private String subLabel;
        private String site;
        private EmploymentKind employmentKind;
        private LocalTime clockIn;
        private LocalTime clockOut;
        private AttendanceStatus attendanceStatus;
        private boolean safetyEducationCompleted;

        public static WorkerRes from(Worker w, AttendanceRecord a) {
            return from(w, a, false);
        }

        public static WorkerRes from(Worker w, AttendanceRecord a, boolean safetyEducationCompleted) {
            return WorkerRes.builder()
                    .idx(w.getIdx())
                    .name(w.getName())
                    .phone(w.getPhone())
                    .jobRank(w.getJobRank())
                    .affiliationKind(w.getAffiliationKind())
                    .partnerCompany(w.getPartnerCompany())
                    .partnerCompanyDetail(w.getPartnerCompanyDetail())
                    .subLabel(w.getSubLabel())
                    .site(w.getSite())
                    .employmentKind(a == null ? null : a.getEmploymentKind())
                    .clockIn(a == null ? null : a.getClockIn())
                    .clockOut(a == null ? null : a.getClockOut())
                    .attendanceStatus(a == null ? AttendanceStatus.ABSENT : a.getAttendanceStatus())
                    .safetyEducationCompleted(safetyEducationCompleted)
                    .build();
        }
    }

    // MANAGEMENT_002/003 목록 응답 (KPI + rows)
    @Getter
    @NoArgsConstructor
    @AllArgsConstructor
    @Builder
    public static class ListRes {
        private StateCountRes globalKpi;
        private StateCountRes listKpi;
        private List<WorkerRes> rows;
    }
}
