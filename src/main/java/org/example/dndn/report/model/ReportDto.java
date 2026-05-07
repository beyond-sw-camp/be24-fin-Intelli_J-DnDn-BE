package org.example.dndn.report.model;

import jakarta.validation.constraints.*;
import lombok.*;
import java.time.LocalDate;
import java.util.List;

// feat : 공사일보 데이터 전송 및 반환용 DTO 클래스
public class ReportDto {

    // [REPORT_007] 7단계 : 명일 스케줄 투입 장비 연동 기능
    // feat : 프론트에서 명일 투입 장비 배열을 받기 위한 DTO 클래스
    @Getter @Setter @NoArgsConstructor @AllArgsConstructor
    public static class TomorrowEqDto {
        private String type;    // feat : 장비 종류 (예: 굴삭기, 덤프트럭 등)
        private Integer count;  // feat : 투입 장비 대수
    }

    // [REPORT_001] 1단계 : 공사일보 기본 엔티티 및 DTO 설계
    // feat : 프론트에서 백엔드로 공사일보 데이터를 생성/요청하기 위한 DTO 클래스 (Req)
    @Getter @Builder @NoArgsConstructor @AllArgsConstructor
    public static class Req {
        @NotNull(message = "workPlanId is required")
        private Long workPlanId;            // feat : 연관된 작업 계획(WorkPlan) 고유 식별자 FK

        @NotNull(message = "actualProgress is required")
        private Double actualProgress;      // feat : 계산 완료된 전체 누적 진척률 (예: 12.5%)

        private Double todayProgress;       // feat : 금일 입력된 진척률 (예: 사용자가 입력한 90%)

        @NotNull(message = "actualWorkerCount is required")
        private Integer actualWorkerCount;  // feat : 금일 실제 투입 인원 수

        @NotBlank(message = "issue is required")
        private String issue;               // feat : 특이사항 및 이슈

        @NotNull(message = "reportDate is required")
        private LocalDate reportDate;       // feat : 공사일보 작성 일자

        private String todayWork;           // feat : 금일 작업 완료 내용
        private String tomorrowPlan;        // feat : 명일 작업 예정 내용

        // [REPORT_006] 6단계 : 명일 스케줄 투입 인원 연동 기능
        // feat : 옵션 B 추가 데이터 (내일 일정 자동 생성용)
        private Integer tomorrowWorkerCount; // feat : 명일 투입 예정 인원 수

        // [REPORT_007] 7단계 : 명일 스케줄 투입 장비 연동 기능
        private List<TomorrowEqDto> tomorrowEquipments; // feat : 명일 투입 예정 장비 목록 배열
    }

    // [REPORT_001] 1단계 : 공사일보 기본 엔티티 및 DTO 설계
    // feat : 프론트로 데이터를 반환하기 위한 응답용 DTO 클래스 (Res)
    @Getter @Builder @NoArgsConstructor @AllArgsConstructor
    public static class Res {
        private Long idx;                   // feat : 공사일보 고유 식별자 PK
        private Long workPlanId;            // feat : 연관된 작업 계획 고유 식별자 FK
        private String process;             // feat : 공정명 (예: 전기 공정)
        private Double actualProgress;      // feat : 전체 누적 진척률
        private Double todayProgress;       // feat : 금일 입력된 진척률
        private Integer actualWorkerCount;  // feat : 금일 실제 투입 인원 수
        private String issue;               // feat : 특이사항 및 이슈
        private LocalDate reportDate;       // feat : 공사일보 작성 일자
        private String todayWork;           // feat : 금일 작업 완료 내용
        private String tomorrowPlan;        // feat : 명일 작업 예정 내용
    }
}