package org.example.dndncore.esg.model;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.example.dndncore.common.model.BaseEntity;
import org.example.dndncore.project.model.entity.Project;

import java.time.LocalDate;

@Getter
@Builder
@AllArgsConstructor
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@Entity
@Table(
        name = "esg_daily_snapshot",
        uniqueConstraints = {
                @UniqueConstraint(
                        name = "uk_esg_daily_snapshot_project_date",
                        columnNames = {"project_idx", "report_date"}
                )
        }
)
@Schema(description = "ESG 일별 스냅샷 엔티티")
public class EsgDailySnapshot extends BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Schema(description = "스냅샷 ID", example = "1")
    private Long idx;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "project_idx", nullable = false)
    @Schema(description = "프로젝트")
    private Project project;

    @Column(name = "report_date", nullable = false)
    @Schema(description = "보고일", example = "2026-05-27")
    private LocalDate reportDate;

    @Column(name = "environment_score")
    @Schema(description = "환경 점수", example = "82.5")
    private Double environmentScore;

    @Column(name = "social_score")
    @Schema(description = "사회 점수", example = "78.2")
    private Double socialScore;

    @Column(name = "governance_score")
    @Schema(description = "지배구조 점수", example = "80.1")
    private Double governanceScore;

    @Column(name = "total_score")
    @Schema(description = "총점", example = "80.3")
    private Double totalScore;

    @Column(name = "level_value")
    @Schema(description = "등급 레벨", example = "3")
    private Integer level;

    @Column(name = "carbon_kg")
    @Schema(description = "탄소 배출량(kg)", example = "123.4")
    private Double carbonKg;

    @Column(name = "power_saving_kwh")
    @Schema(description = "절감 전력량(kWh)", example = "45.6")
    private Double powerSavingKwh;

    @Column(name = "risk_count")
    @Schema(description = "리스크 건수", example = "2")
    private Integer riskCount;

    @Column(name = "mission_rate")
    @Schema(description = "미션 달성률", example = "85")
    private Integer missionRate;

    @Column(name = "safety_days")
    @Schema(description = "무재해 일수", example = "120")
    private Integer safetyDays;

    @Column(name = "zone_count")
    @Schema(description = "구역 수", example = "6")
    private Integer zoneCount;

    @Column(name = "snapshot_json", columnDefinition = "LONGTEXT")
    @Schema(description = "스냅샷 원본 JSON")
    private String snapshotJson;

    public void update(
            Double environmentScore,
            Double socialScore,
            Double governanceScore,
            Double totalScore,
            Integer level,
            Double carbonKg,
            Double powerSavingKwh,
            Integer riskCount,
            Integer missionRate,
            Integer safetyDays,
            Integer zoneCount,
            String snapshotJson
    ) {
        this.environmentScore = environmentScore;
        this.socialScore = socialScore;
        this.governanceScore = governanceScore;
        this.totalScore = totalScore;
        this.level = level;
        this.carbonKg = carbonKg;
        this.powerSavingKwh = powerSavingKwh;
        this.riskCount = riskCount;
        this.missionRate = missionRate;
        this.safetyDays = safetyDays;
        this.zoneCount = zoneCount;
        this.snapshotJson = snapshotJson;
    }
}
