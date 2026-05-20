package org.example.dndncore.worker.model.dto;

import jakarta.validation.constraints.NotNull;
import lombok.*;
import org.example.dndncore.worker.model.entity.AttendanceRecord;
import org.example.dndncore.worker.model.entity.Worker;
import org.example.dndncore.worker.model.enums.AffiliationKind;
import org.example.dndncore.worker.model.enums.AttendanceStatus;
import org.example.dndncore.worker.model.enums.EmploymentKind;
import org.example.dndncore.worker.model.enums.JobRank;

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
     * {@link #trade} 는 근무자가 지원한 공종 카테고리이다 (예: 목공, 전기, 토목, 마감).
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
        private String trade;
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
                    .trade(w.getTrade())
                    .site(w.getSite())
                    .employmentKind(a == null ? null : a.getEmploymentKind())
                    .clockIn(a == null ? null : a.getClockIn())
                    .clockOut(a == null ? null : a.getClockOut())
                    .attendanceStatus(a == null ? AttendanceStatus.ABSENT : a.getAttendanceStatus())
                    .safetyEducationCompleted(safetyEducationCompleted)
                    .build();
        }
    }

    // MANAGEMENT_002/003 목록 응답 (KPI + 페이지 rows)
    @Getter
    @NoArgsConstructor
    @AllArgsConstructor
    @Builder
    public static class ListRes {
        /** 현장+날짜 전체 근무자 집계 (필터 무관) */
        private StateCountRes globalKpi;
        /** 공종·이름 필터 적용 후 전체 집계 (페이징 전) */
        private StateCountRes listKpi;
        /** 현재 페이지 데이터 (size 개) */
        private List<WorkerRes> rows;
        /** listKpi 기준 총 인원 */
        private long totalElements;
        private int totalPages;
        private int page;
        private int size;
        /** 현장+날짜 기준 전체 공종명 목록 (드롭다운 옵션용) */
        private List<String> availableTrades;
    }

}
