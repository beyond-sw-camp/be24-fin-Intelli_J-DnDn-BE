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
        name = "esg_metric_input",
        uniqueConstraints = {
                @UniqueConstraint(
                        name = "uk_esg_metric_input_project_date_zone",
                        columnNames = {"project_idx", "report_date", "zone_name"}
                )
        }
)
@Schema(description = "ESG 지표 입력 엔티티")
public class EsgMetricInput extends BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Schema(description = "지표 입력 ID", example = "1")
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

    @Column(name = "carbon_kg")
    @Schema(description = "탄소 배출량(kg)", example = "123.4")
    private Double carbonKg;

    @Column(name = "power_usage_kwh")
    @Schema(description = "전력 사용량(kWh)", example = "456.7")
    private Double powerUsageKwh;

    @Column(name = "power_saving_kwh")
    @Schema(description = "절감 전력량(kWh)", example = "45.6")
    private Double powerSavingKwh;

    @Column(name = "wash_water_liters")
    @Schema(description = "세척수 사용량(L)", example = "1200")
    private Double washWaterLiters;

    @Column(name = "wastewater_liters")
    @Schema(description = "폐수량(L)", example = "900")
    private Double wastewaterLiters;

    @Column(name = "wastewater_recovery_rate")
    @Schema(description = "폐수 회수율", example = "75.0")
    private Double wastewaterRecoveryRate;

    @Column(name = "fine_dust_value")
    @Schema(description = "미세먼지 값", example = "36.5")
    private Double fineDustValue;

    @Column(name = "noise_db")
    @Schema(description = "소음(dB)", example = "58.2")
    private Double noiseDb;

    @Column(name = "complaint_count")
    @Schema(description = "민원 건수", example = "1")
    private Integer complaintCount;

    @Column(name = "complaint_resolved_count")
    @Schema(description = "민원 처리 건수", example = "1")
    private Integer complaintResolvedCount;

    @Column(name = "safety_education_rate")
    @Schema(description = "안전교육 이수율", example = "92.0")
    private Double safetyEducationRate;

    @Column(name = "staffing_rate")
    @Schema(description = "인력 충원율", example = "88.0")
    private Double staffingRate;

    @Column(name = "report_rate")
    @Schema(description = "보고율", example = "95.0")
    private Double reportRate;

    @Column(name = "action_tracking_rate")
    @Schema(description = "조치 추적율", example = "90.0")
    private Double actionTrackingRate;

    @Column(name = "data_link_rate")
    @Schema(description = "데이터 연계율", example = "85.0")
    private Double dataLinkRate;

    @Column(name = "memo", length = 500)
    @Schema(description = "메모")
    private String memo;
}
