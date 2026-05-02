package org.example.dndn.worker.model.dto;

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

    // MANAGEMENT_001 인력 데이터 요약
    @Getter
    @NoArgsConstructor
    @AllArgsConstructor
    @Builder
    public static class SyncRes {
        private int created;
        private int updated;
        private int total;
    }

    // 목록 상단 KPI (출근/지각/조퇴/결근 + 총 인원)
    @Getter
    @NoArgsConstructor
    @AllArgsConstructor
    @Builder
    public static class StateCountRes {
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
