package org.example.dndncore.staffing.model;

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
public class StaffingLog {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long idx;

    @Column(name = "worker_idx", nullable = false)
    private Long workerIdx;

    @Column(name = "work_date", nullable = false)
    private LocalDate workDate;

    /** 배치 구역 PK 스냅샷 */
    @Column(name = "zone_sub_idx")
    private Long zoneSubIdx;

    /** 기록 시점 zone_main.title 스냅샷 */
    @Column(name = "zone_main_title", length = 100)
    private String zoneMainTitle;

    /** 기록 시점 zone_sub.title 스냅샷 */
    @Column(name = "zone_sub_title", length = 150)
    private String zoneSubTitle;

    /** 기록 시점 zone_sub.tradeName 스냅샷 */
    @Column(name = "trade_name", length = 50)
    private String tradeName;

    /** 현장 코드 스냅샷 — worker.siteCode 기준, 현장별 배치 이력 조회 지원 */
    @Column(name = "site_code", length = 30)
    private String siteCode;

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @PrePersist
    protected void onCreate() {
        this.createdAt = LocalDateTime.now();
    }
}
