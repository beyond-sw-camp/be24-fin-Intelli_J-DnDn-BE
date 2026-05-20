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
        name = "esg_metric_input",
        uniqueConstraints = {
                @UniqueConstraint(
                        name = "uk_esg_metric_input_project_date_zone",
                        columnNames = {"project_idx", "report_date", "zone_name"}
                )
        }
)
public class EsgMetricInput extends BaseEntity {

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

    @Column(name = "carbon_kg")
    private Double carbonKg;

    @Column(name = "power_usage_kwh")
    private Double powerUsageKwh;

    @Column(name = "power_saving_kwh")
    private Double powerSavingKwh;

    @Column(name = "wash_water_liters")
    private Double washWaterLiters;

    @Column(name = "wastewater_liters")
    private Double wastewaterLiters;

    @Column(name = "wastewater_recovery_rate")
    private Double wastewaterRecoveryRate;

    @Column(name = "fine_dust_value")
    private Double fineDustValue;

    @Column(name = "noise_db")
    private Double noiseDb;

    @Column(name = "complaint_count")
    private Integer complaintCount;

    @Column(name = "complaint_resolved_count")
    private Integer complaintResolvedCount;

    @Column(name = "safety_education_rate")
    private Double safetyEducationRate;

    @Column(name = "staffing_rate")
    private Double staffingRate;

    @Column(name = "report_rate")
    private Double reportRate;

    @Column(name = "action_tracking_rate")
    private Double actionTrackingRate;

    @Column(name = "data_link_rate")
    private Double dataLinkRate;

    @Column(name = "memo", length = 500)
    private String memo;
}
