package org.example.dndncore.worker.model.entity;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.persistence.*;
import lombok.*;
import org.example.dndncore.common.model.BaseEntity;
import org.example.dndncore.worker.model.enums.AffiliationKind;
import org.example.dndncore.worker.model.enums.EmploymentKind;
import org.example.dndncore.worker.model.enums.JobRank;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

@Entity
@Table(name = "worker")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor(access = AccessLevel.PRIVATE)
@Builder
@Schema(description = "작업자 정보 엔티티")
public class Worker extends BaseEntity {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Schema(description = "작업자 ID", example = "1")
    private Long idx;

    @Column(unique = true, length = 50)
    @Schema(description = "외부 식별자/사번", example = "EMP20240115001")
    private String externalCode;

    @Column(nullable = false, length = 30)
    @Schema(description = "작업자 이름", example = "홍길동")
    private String name;

    @Column(length = 20)
    @Schema(description = "연락처", example = "010-1234-5678")
    private String phone;

    @Column(length = 20)
    @Schema(description = "비상 연락처", example = "010-9999-8888")
    private String emergencyPhone;

    @Column(length = 20)
    @Schema(description = "비상 연락 관계", example = "배우자")
    private String emergencyRelation;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 10)
    @Schema(description = "직급", example = "TEAM_LEAD")
    private JobRank jobRank;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 10)
    @Schema(description = "소속 구분", example = "DIRECT")
    private AffiliationKind affiliationKind;

    @Column(name = "trade", length = 30)
    @Schema(description = "공종", example = "목공")
    private String trade;

    @Enumerated(EnumType.STRING)
    @Column(name = "employment_kind", nullable = false, length = 16)
    @Schema(description = "고용 구분", example = "REGULAR")
    private EmploymentKind employmentKind;

    @Column(length = 100)
    @Schema(description = "현장", example = "강남구 재건축 A공구")
    private String site;

    @Column(length = 30)
    @Schema(description = "현장 코드", example = "SITE01")
    private String siteCode;

    @Column(length = 5)
    @Schema(description = "혈액형", example = "A형")
    private String bloodType;

    @Schema(description = "등록 일자", example = "2024-01-15")
    private LocalDate registeredAt;

    @Column(length = 500)
    @Schema(description = "프로필 이미지 URL", example = "https://example.com/profile/1.jpg")
    private String profileImageUrl;

    @Column(precision = 5, scale = 1)
    @Schema(description = "당월 누적 공수", example = "15.5")
    private BigDecimal monthTotalMan;

    @Column(name = "fatigue_score_total", nullable = false)
    @Builder.Default
    @Schema(description = "피로도 점수(0-100)", example = "42")
    private int fatigueScoreTotal = 0;

    @Column(name = "fatigue_high_risk", nullable = false)
    @Builder.Default
    @Schema(description = "고위험 근로자 여부", example = "false")
    private boolean fatigueHighRisk = false;

    @Column(name = "fatigue_pt_accident", nullable = false)
    @Builder.Default
    @Schema(description = "사고 피로도 점수", example = "10")
    private int fatiguePtAccident = 0;

    @Column(name = "fatigue_pt_streak", nullable = false)
    @Builder.Default
    @Schema(description = "연속 근무 피로도 점수", example = "15")
    private int fatiguePtStreak = 0;

    @Column(name = "fatigue_pt_overnight", nullable = false)
    @Builder.Default
    @Schema(description = "야간 근무 피로도 점수", example = "5")
    private int fatiguePtOvernight = 0;

    @Column(name = "fatigue_pt_trade_risk", nullable = false)
    @Builder.Default
    @Schema(description = "공종 위험도 점수", example = "20")
    private int fatiguePtTradeRisk = 0;

    @Column(name = "fatigue_calculated_at")
    @Schema(description = "피로도 산정 시각", example = "2026-05-27T14:30:00")
    private LocalDateTime fatigueCalculatedAt;

    public void replaceFatigueSnapshot(
            int totalCapped,
            boolean highRisk,
            int ptAccident,
            int ptStreak,
            int ptOvernight,
            int ptTrade,
            LocalDateTime calculatedAt
    ) {
        this.fatigueScoreTotal = totalCapped;
        this.fatigueHighRisk = highRisk;
        this.fatiguePtAccident = ptAccident;
        this.fatiguePtStreak = ptStreak;
        this.fatiguePtOvernight = ptOvernight;
        this.fatiguePtTradeRisk = ptTrade;
        this.fatigueCalculatedAt = calculatedAt;
    }

}
