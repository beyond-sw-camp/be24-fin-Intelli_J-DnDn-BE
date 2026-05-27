package org.example.dndncore.worker.model.entity;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.persistence.*;
import lombok.*;
import org.example.dndncore.common.model.BaseEntity;

import java.time.LocalDate;

@Entity
@Table(name = "safety_accident")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor(access = AccessLevel.PRIVATE)
@Builder
@Schema(description = "안전 사고 엔티티")
public class SafetyAccident extends BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Schema(description = "사고 ID", example = "1")
    private Long idx;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "worker_idx", nullable = false)
    @Schema(description = "작업자")
    private Worker worker;

    @Column(nullable = false)
    @Schema(description = "발생 일자", example = "2026-05-15")
    private LocalDate occurredAt;

    @Column(length = 50)
    @Schema(description = "사고 유형", example = "추락")
    private String accidentType;

    @Column(name = "zone_main", length = 50)
    @Schema(description = "기본 구역명", example = "골조 공정")
    private String zoneMain;

    @Column(name = "zone_sub", length = 100)
    @Schema(description = "상세 위치", example = "A동 3층")
    private String zoneSub;

    @Column(length = 500)
    @Schema(description = "조치 결과", example = "병원 치료 후 휴무")
    private String resolution;
}
