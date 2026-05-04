package org.example.dndn.report.model;

import jakarta.persistence.*;
import lombok.*;
import org.example.dndn.common.model.BaseEntity;
import org.example.dndn.workplan.model.entity.WorkPlan;
import java.time.LocalDate;

// [REPORT_001] 1단계 : 공사일보 기본 엔티티 및 DTO 설계
// feat : 공사일보 엔티티 클래스
@Entity
@Table(name = "daily_report")
@Getter @Builder @NoArgsConstructor @AllArgsConstructor
public class DailyReport extends BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long idx; // feat : 공사일보 고유 식별자 PK

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "work_plan_idx")
    private WorkPlan workPlan; // feat : 연관된 주간 작업 계획 FK

    private Double actualProgress; // feat : 전체 누적 진척률

    private Double todayProgress; // feat : 금일 입력 진척률 (새로 추가된 컬럼)

    private Integer actualWorkerCount; // feat : 금일 실제 투입 인원 수

    private String issue; // feat : 특이사항 및 이슈

    private LocalDate reportDate; // feat : 공사일보 작성 일자

    @Column(columnDefinition = "TEXT")
    private String todayWork; // feat : 금일 작업 완료 내용

    @Column(columnDefinition = "TEXT")
    private String tomorrowPlan; // feat : 명일 작업 예정 내용

    // [REPORT_003] 3단계 : 공사일보 제출(Upsert) 기본 로직 구현
    // feat : 공사일보 정보 업데이트 메서드 (금일 진척률 반영)
    public void updateReport(Double actualProgress, Double todayProgress, Integer actualWorkerCount, String issue, String todayWork, String tomorrowPlan) {
        this.actualProgress = actualProgress;
        this.todayProgress = todayProgress;
        this.actualWorkerCount = actualWorkerCount;
        this.issue = issue;
        this.todayWork = todayWork;
        this.tomorrowPlan = tomorrowPlan;
    }
}