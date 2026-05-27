package org.example.dndncore.worker.model.dto;

import io.swagger.v3.oas.annotations.media.Schema;
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
    @Schema(description = "근무자 검색 요청 DTO")
    public static class SearchReq {
        @Schema(description = "현장 코드", example = "SITE01")
        private String siteCode;
        @Schema(description = "기준 일자", example = "2026-05-27")
        private LocalDate date;
        @Schema(description = "출결 상태", example = "PRESENT")
        private AttendanceStatus attendanceStatus;
        @Schema(description = "이름 검색어", example = "김")
        private String searchName;
    }

    // MANAGEMENT_010 게이트 출근 인식.
    @Getter
    @Setter
    @AllArgsConstructor
    @NoArgsConstructor
    @Builder
    @Schema(description = "게이트 출근 인식 요청 DTO")
    public static class GateClockInReq {
        @NotNull
        @Schema(description = "작업자 ID", example = "1")
        private Long workerIdx;
        @Schema(description = "근무 일자", example = "2026-05-27", nullable = true)
        private LocalDate workDate;  // null 이면 서버 로컬 기준 오늘
        @NotNull
        @Schema(description = "인식 시각", example = "08:00:00")
        private LocalTime recognizedAt;
        @Schema(description = "현장 코드", example = "SITE01")
        private String siteCode;     // 현장 구분 — 제공 시 worker.siteCode 불일치면 거부
    }

    // MANAGEMENT_011 게이트 퇴근 인식
    @Getter
    @Setter
    @AllArgsConstructor
    @NoArgsConstructor
    @Builder
    @Schema(description = "게이트 퇴근 인식 요청 DTO")
    public static class GateClockOutReq {
        @NotNull
        @Schema(description = "작업자 ID", example = "1")
        private Long workerIdx;
        @Schema(description = "근무 일자", example = "2026-05-27", nullable = true)
        private LocalDate workDate;  // null 이면 서버 로컬 기준 오늘
        @NotNull
        @Schema(description = "인식 시각", example = "18:00:00")
        private LocalTime recognizedAt;
        @Schema(description = "현장 코드", example = "SITE01")
        private String siteCode;     // 현장 구분 — 제공 시 worker.siteCode 불일치면 거부
    }

    // MANAGEMENT_010, MANAGEMENT_011 게이트 처리 직후 해당 일 근태 스냅샷.
    @Getter
    @NoArgsConstructor
    @AllArgsConstructor
    @Builder
    @Schema(description = "게이트 출퇴근 응답 DTO")
    public static class GateAttendanceRes {
        @Schema(description = "작업자 ID", example = "1")
        private Long workerIdx;
        @Schema(description = "근무 일자", example = "2026-05-27")
        private LocalDate workDate;
        @Schema(description = "출근 시각", example = "08:00:00", nullable = true)
        private LocalTime clockIn;
        @Schema(description = "퇴근 시각", example = "18:00:00", nullable = true)
        private LocalTime clockOut;
        @Schema(description = "출결 상태", example = "PRESENT")
        private AttendanceStatus attendanceStatus;
    }

    // 목록 상단 KPI (총 인원에 대한 출근/지각/퇴근/조퇴/결근)
    @Getter
    @NoArgsConstructor
    @AllArgsConstructor
    @Builder
    @Schema(description = "출결 현황 집계 DTO")
    public static class StateCountRes {
        @Schema(description = "미출근 인원", example = "2")
        private int pending;
        @Schema(description = "출근 인원", example = "15")
        private int present;
        @Schema(description = "지각 인원", example = "1")
        private int late;
        @Schema(description = "정상 퇴근 인원", example = "14")
        private int leave;
        @Schema(description = "조퇴 인원", example = "0")
        private int earlyLeave;
        @Schema(description = "결근 인원", example = "0")
        private int absent;
        @Schema(description = "총 인원", example = "18")
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
    @Schema(description = "작업자 행 응답 DTO")
    public static class WorkerRes {
        @Schema(description = "작업자 ID", example = "1")
        private Long idx;
        @Schema(description = "작업자 이름", example = "홍길동")
        private String name;
        @Schema(description = "연락처", example = "010-1234-5678")
        private String phone;
        @Schema(description = "직급", example = "TEAM_LEAD")
        private JobRank jobRank;
        @Schema(description = "소속 구분", example = "DIRECT")
        private AffiliationKind affiliationKind;
        @Schema(description = "공종", example = "목공")
        private String trade;
        @Schema(description = "현장", example = "강남구 재건축 A공구")
        private String site;
        @Schema(description = "고용 구분", example = "REGULAR")
        private EmploymentKind employmentKind;
        @Schema(description = "출근 시각", example = "08:00:00", nullable = true)
        private LocalTime clockIn;
        @Schema(description = "퇴근 시각", example = "18:00:00", nullable = true)
        private LocalTime clockOut;
        @Schema(description = "출결 상태", example = "PRESENT")
        private AttendanceStatus attendanceStatus;
        @Schema(description = "안전교육 이수 여부", example = "true")
        private boolean safetyEducationCompleted;

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
    @Schema(description = "작업자 목록 응답 DTO")
    public static class ListRes {
        @Schema(description = "전체 근무자 출결 집계")
        private StateCountRes globalKpi;
        @Schema(description = "필터 적용 후 출결 집계")
        private StateCountRes listKpi;
        @Schema(description = "현재 페이지 작업자 목록")
        private List<WorkerRes> rows;
        @Schema(description = "총 근무자 수", example = "120")
        private long totalElements;
        @Schema(description = "총 페이지 수", example = "6")
        private int totalPages;
        @Schema(description = "현재 페이지", example = "0")
        private int page;
        @Schema(description = "페이지 크기", example = "20")
        private int size;
        @Schema(description = "사용 가능한 공종 목록")
        private List<String> availableTrades;
    }

}
