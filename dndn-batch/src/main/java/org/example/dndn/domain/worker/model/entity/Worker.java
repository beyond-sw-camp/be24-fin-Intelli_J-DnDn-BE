package org.example.dndn.domain.worker.model.entity;

import jakarta.persistence.*;
import lombok.*;
import org.example.dndn.domain.common.model.BaseEntity;
import org.example.dndn.domain.worker.model.enums.AffiliationKind;
import org.example.dndn.domain.worker.model.enums.EmploymentKind;
import org.example.dndn.domain.worker.model.enums.JobRank;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

@Entity
@Table(name = "worker")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor(access = AccessLevel.PRIVATE)
@Builder
public class Worker extends BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long idx;

    @Column(unique = true, length = 50)
    private String externalCode;

    @Column(nullable = false, length = 30)
    private String name;

    @Column(length = 20)
    private String phone;

    @Column(length = 20)
    private String emergencyPhone;

    @Column(length = 20)
    private String emergencyRelation;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 10)
    private JobRank jobRank;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 10)
    private AffiliationKind affiliationKind;

    @Column(name = "trade", length = 30)
    private String trade;

    @Enumerated(EnumType.STRING)
    @Column(name = "employment_kind", nullable = false, length = 16)
    private EmploymentKind employmentKind;

    @Column(length = 100)
    private String site;

    @Column(length = 30)
    private String siteCode;

    @Column(length = 5)
    private String bloodType;

    private LocalDate registeredAt;

    @Column(length = 500)
    private String profileImageUrl;

    @Column(precision = 5, scale = 1)
    private BigDecimal monthTotalMan;

    @Column(name = "fatigue_score_total", nullable = false)
    @Builder.Default
    private int fatigueScoreTotal = 0;

    @Column(name = "fatigue_high_risk", nullable = false)
    @Builder.Default
    private boolean fatigueHighRisk = false;

    @Column(name = "fatigue_pt_accident", nullable = false)
    @Builder.Default
    private int fatiguePtAccident = 0;

    @Column(name = "fatigue_pt_streak", nullable = false)
    @Builder.Default
    private int fatiguePtStreak = 0;

    @Column(name = "fatigue_pt_overnight", nullable = false)
    @Builder.Default
    private int fatiguePtOvernight = 0;

    @Column(name = "fatigue_pt_trade_risk", nullable = false)
    @Builder.Default
    private int fatiguePtTradeRisk = 0;

    @Column(name = "fatigue_calculated_at")
    private LocalDateTime fatigueCalculatedAt;

    public void replaceFatigueSnapshot(
            int totalCapped, boolean highRisk,
            int ptAccident, int ptStreak, int ptOvernight, int ptTrade,
            LocalDateTime calculatedAt) {
        this.fatigueScoreTotal = totalCapped;
        this.fatigueHighRisk = highRisk;
        this.fatiguePtAccident = ptAccident;
        this.fatiguePtStreak = ptStreak;
        this.fatiguePtOvernight = ptOvernight;
        this.fatiguePtTradeRisk = ptTrade;
        this.fatigueCalculatedAt = calculatedAt;
    }

    public void updateFromSync(Worker incoming) {
        this.name = incoming.name;
        this.phone = incoming.phone;
        this.emergencyPhone = incoming.emergencyPhone;
        this.emergencyRelation = incoming.emergencyRelation;
        this.jobRank = incoming.jobRank;
        this.affiliationKind = incoming.affiliationKind;
        this.trade = incoming.trade;
        this.employmentKind = incoming.employmentKind;
        this.site = incoming.site;
        this.siteCode = incoming.siteCode;
        this.bloodType = incoming.bloodType;
        this.profileImageUrl = incoming.profileImageUrl;
    }
}
