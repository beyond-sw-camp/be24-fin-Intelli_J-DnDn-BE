package org.example.dndncore.esg.model;

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
public class EsgDailySnapshot extends BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long idx;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "project_idx", nullable = false)
    private Project project;

    @Column(name = "report_date", nullable = false)
    private LocalDate reportDate;

    @Column(name = "environment_score")
    private Double environmentScore;

    @Column(name = "social_score")
    private Double socialScore;

    @Column(name = "governance_score")
    private Double governanceScore;

    @Column(name = "total_score")
    private Double totalScore;

    @Column(name = "level_value")
    private Integer level;

    @Column(name = "carbon_kg")
    private Double carbonKg;

    @Column(name = "power_saving_kwh")
    private Double powerSavingKwh;

    @Column(name = "risk_count")
    private Integer riskCount;

    @Column(name = "mission_rate")
    private Integer missionRate;

    @Column(name = "safety_days")
    private Integer safetyDays;

    @Column(name = "zone_count")
    private Integer zoneCount;

    @Column(name = "snapshot_json", columnDefinition = "LONGTEXT")
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
