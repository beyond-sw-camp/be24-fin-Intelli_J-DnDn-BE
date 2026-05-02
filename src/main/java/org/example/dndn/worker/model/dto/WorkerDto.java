package org.example.dndn.worker.model.dto;

import jakarta.validation.constraints.NotNull;
import lombok.*;
import org.example.dndn.worker.model.entity.AttendanceRecord;
import org.example.dndn.worker.model.entity.Worker;
import org.example.dndn.worker.model.enums.AffiliationKind;
import org.example.dndn.worker.model.enums.AttendanceStatus;
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
        private LocalDate workDate; // null 이면 서버 로컬 기준 오늘
        @NotNull
        private LocalTime recognizedAt;
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
        private LocalDate workDate; // null 이면 서버 로컬 기준 오늘
        @NotNull
        private LocalTime recognizedAt;
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
        private int zoneHistoriesSynced;
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

    // 목록 상단 KPI (총 인원에 대한 출근/지각/조퇴/결근)
    @Getter
    @NoArgsConstructor
    @AllArgsConstructor
    @Builder
    public static class StateCountRes {
        private int pending;
        private int present;
        private int late;
        private int earlyLeave;
        private int absent;
        private int total;
    }

    // MANAGEMENT_002/003 Worker 1행 조회.
    // 컬럼: 이름/연락처, 비상연락망/관계, 소속, 직급, 출/퇴근 시간, 상태, 상세보기 ID
    @Getter
    @NoArgsConstructor
    @AllArgsConstructor
    @Builder
    public static class WorkerRes {
        private Long idx;
        private String name;
        private String phone;
        private String emergencyPhone;
        private String emergencyRelation;
        private JobRank jobRank;
        private AffiliationKind affiliationKind;
        private String partnerCompany;
        private String subLabel;
        private String site;
        private LocalTime clockIn;
        private LocalTime clockOut;
        private AttendanceStatus attendanceStatus;

        public static WorkerRes from(Worker w, AttendanceRecord a) {
            return WorkerRes.builder()
                    .idx(w.getIdx())
                    .name(w.getName())
                    .phone(w.getPhone())
                    .emergencyPhone(w.getEmergencyPhone())
                    .emergencyRelation(w.getEmergencyRelation())
                    .jobRank(w.getJobRank())
                    .affiliationKind(w.getAffiliationKind())
                    .partnerCompany(w.getPartnerCompany())
                    .subLabel(w.getSubLabel())
                    .site(w.getSite())
                    .clockIn(a == null ? null : a.getClockIn())
                    .clockOut(a == null ? null : a.getClockOut())
                    .attendanceStatus(a == null ? AttendanceStatus.ABSENT : a.getAttendanceStatus())
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
