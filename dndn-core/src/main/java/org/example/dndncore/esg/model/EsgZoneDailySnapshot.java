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
        name = "esg_zone_daily_snapshot",
        uniqueConstraints = {
                @UniqueConstraint(
                        name = "uk_esg_zone_daily_snapshot_project_date_zone",
                        columnNames = {"project_idx", "report_date", "zone_name"}
                )
        }
)
public class EsgZoneDailySnapshot extends BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long idx;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "project_idx", nullable = false)
    private Project project;

    @Column(name = "report_date", nullable = false)
    private LocalDate reportDate;

    @Column(name = "zone_name", nullable = false, length = 100)
    private String zoneName;

    @Column(name = "zone_type", length = 30)
    private String zoneType;

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

    @Column(name = "equipment_count")
    private Integer equipmentCount;

    @Column(name = "high_risk_equipment_count")
    private Integer highRiskEquipmentCount;

    @Column(name = "contribution_weight")
    private Double contributionWeight;

    @Column(name = "contribution_score")
    private Double contributionScore;

    @Column(name = "snapshot_json", columnDefinition = "LONGTEXT")
    private String snapshotJson;

    public void update(
            String zoneName,
            String zoneType,
            Double environmentScore,
            Double socialScore,
            Double governanceScore,
            Double totalScore,
            Integer level,
            Double carbonKg,
            Double powerSavingKwh,
            Integer riskCount,
            Integer missionRate,
            Integer equipmentCount,
            Integer highRiskEquipmentCount,
            Double contributionWeight,
            Double contributionScore,
            String snapshotJson
    ) {
        this.zoneName = zoneName;
        this.zoneType = zoneType;
        this.environmentScore = environmentScore;
        this.socialScore = socialScore;
        this.governanceScore = governanceScore;
        this.totalScore = totalScore;
        this.level = level;
        this.carbonKg = carbonKg;
        this.powerSavingKwh = powerSavingKwh;
        this.riskCount = riskCount;
        this.missionRate = missionRate;
        this.equipmentCount = equipmentCount;
        this.highRiskEquipmentCount = highRiskEquipmentCount;
        this.contributionWeight = contributionWeight;
        this.contributionScore = contributionScore;
        this.snapshotJson = snapshotJson;
    }
}
