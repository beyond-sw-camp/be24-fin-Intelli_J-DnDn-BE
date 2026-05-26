package org.example.dndncore.worker.model.entity;

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
public class Worker extends BaseEntity {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long idx;

    /** 시연 픽스처·향후 연동 시 공통으로 쓰는 외부 식별자/사번 등 (sync 시 dedup key) */
    @Column(unique = true, length = 50)
    private String externalCode;

    /** 이름 */
    @Column(nullable = false, length = 30)
    private String name;

    /** 본인 연락처 */
    @Column(length = 20)
    private String phone;

    /** 비상 연락처 (번호) */
    @Column(length = 20)
    private String emergencyPhone;

    /** 비상 연락 관계 (예: 배우자, 자녀, 부모) */
    @Column(length = 20)
    private String emergencyRelation;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 10)
    private JobRank jobRank;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 10)
    private AffiliationKind affiliationKind;

    /** 인력 데이터 수신 시 해당 근무자가 지원한 공종 카테고리 (예: 목공, 전기, 토목, 마감). */
    @Column(name = "trade", length = 30)
    private String trade;

    /** 인사·연동에서 받은 상용(REGULAR)·일용(DAILY) 마스터 구분. sync 시 갱신되며 명단일 {@link AttendanceRecord} 생성 시 기본값으로 쓰인다. */
    @Enumerated(EnumType.STRING)
    @Column(name = "employment_kind", nullable = false, length = 16)
    private EmploymentKind employmentKind;

    /** 투입 현장 (예: 강남구 재건축 A공구) */
    @Column(length = 100)
    private String site;

    /** 인사·현장 시스템과 맞추는 현장 코드(sync 필터 키). 미입력 시 {@link #site} 문자열로만 매칭. */
    @Column(length = 30)
    private String siteCode;

    /** 혈액형 */
    @Column(length = 5)
    private String bloodType;

    /** 등록일 */
    private LocalDate registeredAt;

    /** 프로필 사진 URL */
    @Column(length = 500)
    private String profileImageUrl;

    /** 당월 누적 공수 캐시값 (실시간 합계는 AttendanceRecord 로 산출) */
    @Column(precision = 5, scale = 1)
    private BigDecimal monthTotalMan;

    /** 피로도/위험 점수(0–100 상한 적용값). 프로필·인력 배치 등에서 참조한다. */
    @Column(name = "fatigue_score_total", nullable = false)
    @Builder.Default
    private int fatigueScoreTotal = 0;

    /** {@code fatigue_score_total ≥ 80} 일 때 고위험 근로자로 분류 */
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

    /** 마지막 피로도 산정 시각(재계산 직후 갱신) */
    @Column(name = "fatigue_calculated_at")
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
