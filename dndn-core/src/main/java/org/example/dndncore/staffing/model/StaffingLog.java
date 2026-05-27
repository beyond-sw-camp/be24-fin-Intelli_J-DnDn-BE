package org.example.dndncore.staffing.model;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDate;
import java.time.LocalDateTime;

/** 인력 배치 확정 이력. /staffing/save 호출 시 1인 1행씩 append-only 로 기록된다. */
@Entity
@Table(
        name = "staffing_log",
        indexes = @Index(name = "idx_staffing_log_worker_date", columnList = "worker_idx, work_date")
)
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor(access = AccessLevel.PRIVATE)
@Builder
@Schema(description = "인력 배치 확정 이력 엔티티")
public class StaffingLog {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Schema(description = "확정 이력 ID", example = "1")
    private Long idx;

    @Column(name = "worker_idx", nullable = false)
    @Schema(description = "작업자 ID", example = "101")
    private Long workerIdx;

    @Column(name = "work_date", nullable = false)
    @Schema(description = "근무 일자", example = "2026-05-27")
    private LocalDate workDate;

    /** 배치 구역 PK 스냅샷 */
    @Column(name = "zone_sub_idx")
    @Schema(description = "상세 구역 ID", example = "10")
    private Long zoneSubIdx;

    /** 기록 시점 zone_main.title 스냅샷 */
    @Column(name = "zone_main_title", length = 100)
    @Schema(description = "기록 시점 기본 구역명", example = "골조 공정")
    private String zoneMainTitle;

    /** 기록 시점 zone_sub.title 스냅샷 */
    @Column(name = "zone_sub_title", length = 150)
    @Schema(description = "기록 시점 상세 구역명", example = "A동 3층 거푸집")
    private String zoneSubTitle;

    /** 기록 시점 zone_sub.tradeName 스냅샷 */
    @Column(name = "trade_name", length = 50)
    @Schema(description = "기록 시점 공종명", example = "철근")
    private String tradeName;

    /** 현장 코드 스냅샷 — worker.siteCode 기준, 현장별 배치 이력 조회 지원 */
    @Column(name = "site_code", length = 30)
    @Schema(description = "현장 코드", example = "SITE-001")
    private String siteCode;

    @Column(name = "created_at", nullable = false, updatable = false)
    @Schema(description = "확정 기록 시각", example = "2026-05-27T09:30:00")
    private LocalDateTime createdAt;

    @PrePersist
    protected void onCreate() {
        this.createdAt = LocalDateTime.now();
    }
}
