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
        name = "esg_zone_daily_snapshot",
        uniqueConstraints = {
                @UniqueConstraint(
                        name = "uk_esg_zone_daily_snapshot_project_date_zone",
                        columnNames = {"project_idx", "report_date", "zone_name"}
                )
        }
)
@Schema(description = "ESG 구역 일별 스냅샷 엔티티")
public class EsgZoneDailySnapshot extends BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Schema(description = "구역 스냅샷 ID", example = "1")
    private Long idx;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "project_idx", nullable = false)
    @Schema(description = "프로젝트")
    private Project project;

    @Column(name = "report_date", nullable = false)
    @Schema(description = "보고일", example = "2026-05-27")
    private LocalDate reportDate;

    @Column(name = "zone_name", nullable = false, length = 100)
    @Schema(description = "구역명", example = "A구역")
    private String zoneName;

    @Column(name = "zone_type", length = 30)
    @Schema(description = "구역 유형", example = "OUTDOOR")
    private String zoneType;

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
    @Schema(description = "탄소 배출량(kg)", example = "30.2")
    private Double carbonKg;

    @Column(name = "power_saving_kwh")
    @Schema(description = "절감 전력량(kWh)", example = "11.4")
    private Double powerSavingKwh;

    @Column(name = "risk_count")
    @Schema(description = "리스크 건수", example = "1")
    private Integer riskCount;

    @Column(name = "mission_rate")
    @Schema(description = "미션 달성률", example = "88")
    private Integer missionRate;

    @Column(name = "equipment_count")
    @Schema(description = "장비 수", example = "15")
    private Integer equipmentCount;

    @Column(name = "high_risk_equipment_count")
    @Schema(description = "고위험 장비 수", example = "2")
    private Integer highRiskEquipmentCount;

    @Column(name = "contribution_weight")
    @Schema(description = "기여 가중치", example = "0.25")
    private Double contributionWeight;

    @Column(name = "contribution_score")
    @Schema(description = "기여 점수", example = "20.1")
    private Double contributionScore;

    @Column(name = "snapshot_json", columnDefinition = "LONGTEXT")
    @Schema(description = "스냅샷 원본 JSON")
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
