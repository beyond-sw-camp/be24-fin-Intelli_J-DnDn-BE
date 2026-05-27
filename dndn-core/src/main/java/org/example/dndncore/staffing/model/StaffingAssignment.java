package org.example.dndncore.staffing.model;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.persistence.*;
import lombok.*;
import org.example.dndncore.common.model.BaseEntity;

import java.time.LocalDate;

@Entity
@Table(name = "staffing_assignment")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor(access = AccessLevel.PRIVATE)
@Builder
@Schema(description = "인력 배치 초안/확정 상태 엔티티")
public class StaffingAssignment extends BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Schema(description = "배치 ID", example = "1")
    private Long idx;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "zone_sub_idx", nullable = false)
    @Schema(description = "배치된 상세 구역")
    private ZoneSub zoneSub;

    @Column(name = "worker_idx", nullable = false)
    @Schema(description = "작업자 ID", example = "101")
    private Long workerIdx;

    @Column(name = "work_date")
    @Schema(description = "근무 일자", example = "2026-05-27")
    private LocalDate workDate;

    /** 현장 코드 스냅샷 — worker.siteCode 기준, 현장별 배치 현황 조회·초기화·확정 지원 */
    @Column(name = "site_code", length = 30)
    @Schema(description = "현장 코드", example = "SITE-001")
    private String siteCode;

    /** 배치 확정 여부 — true: 확정됨, false(기본값): 초안 */
    @Builder.Default
    @Column(name = "confirmed", nullable = false)
    @Schema(description = "배치 확정 여부", example = "false")
    private boolean confirmed = false;
}
