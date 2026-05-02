package org.example.dndn.worker.model.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.example.dndn.worker.model.entity.AttendanceRecord;
import org.example.dndn.worker.model.entity.Worker;
import org.example.dndn.worker.model.enums.AffiliationKind;
import org.example.dndn.worker.model.enums.AttendanceStatus;
import org.example.dndn.worker.model.enums.JobRank;

import java.time.LocalTime;
import java.util.List;

public class WorkerDto {

    /**
     * MANAGEMENT_001 sync 결과 요약
     */
    @Getter
    @NoArgsConstructor
    @AllArgsConstructor
    @Builder
    public static class SyncRes {
        private int created;
        private int updated;
        private int total;
    }

    /**
     * 목록 상단 KPI (출근/지각/조퇴/결근 + 총 인원)
     */
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

        // 해당일 근태
        private LocalTime clockIn;
        private LocalTime clockOut;
        private AttendanceStatus attendanceStatus;

        /**
         * Worker + 해당일 AttendanceRecord(없을 수 있음) → WorkerRes.
         * 해당일 근태 행이 없으면 상태는 결근(ABSENT)으로 둔다 — 근태는 항상 {@link AttendanceRecord} 가 단일 출처.
         */
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

    /**
     * MANAGEMENT_002/003 목록 응답 (KPI + rows)
     */
    @Getter
    @NoArgsConstructor
    @AllArgsConstructor
    @Builder
    public static class ListRes {
        private StateCountRes kpi;
        private List<WorkerRes> rows;
    }
}
