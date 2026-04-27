package org.example.dndn.partner.model;

import jakarta.persistence.*;
import lombok.*;
import org.example.dndn.common.model.BaseEntity;

import java.time.LocalDate;

@AllArgsConstructor
@NoArgsConstructor
@Getter
@Builder
@Entity
@Table(name = "partner_evaluation")
public class PartnerEvaluation extends BaseEntity {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long idx;

    private Integer qualityScore;   // 품질
    private Integer safetyScore;    // 안전
    private Integer scheduleScore;  // 일정
    private Integer commScore;      // 소통

    private Integer totalScore;     // 종합 점수
    private String grade;           // S/A/B+/B/C

    @Column(length = 1000)
    private String summary;

    private LocalDate lastEvaluatedAt;

    @OneToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "partner_idx")
    private Partner partner;

    /**
     * 점수 갱신 - 평균과 등급은 자동 계산
     */
    public void update(Integer qualityScore, Integer safetyScore, Integer scheduleScore,
                       Integer commScore, String summary, LocalDate evaluatedAt) {
        this.qualityScore = clamp(qualityScore);
        this.safetyScore = clamp(safetyScore);
        this.scheduleScore = clamp(scheduleScore);
        this.commScore = clamp(commScore);
        this.summary = (summary == null || summary.isBlank()) ? "평가 의견이 입력되지 않았습니다." : summary;
        this.lastEvaluatedAt = evaluatedAt;
        this.totalScore = calcTotal();
        this.grade = calcGrade(this.totalScore);
    }

    void bindPartner(Partner partner) {
        this.partner = partner;
    }

    private int clamp(Integer score) {
        if (score == null) return 0;
        return Math.max(0, Math.min(100, score));
    }

    private int calcTotal() {
        return Math.round((qualityScore + safetyScore + scheduleScore + commScore) / 4.0f);
    }

    private String calcGrade(int score) {
        if (score >= 95) return "A+";
        if (score >= 90) return "A";
        if (score >= 80) return "B";
        if (score >= 70) return "C";
        return "C";
    }
}