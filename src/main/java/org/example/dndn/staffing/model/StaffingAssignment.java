package org.example.dndn.staffing.model;

import jakarta.persistence.*;
import lombok.*;
import org.example.dndn.common.model.BaseEntity;

import java.time.LocalDate;

@Entity
@Table(name = "staffing_assignment")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor(access = AccessLevel.PRIVATE)
@Builder
public class StaffingAssignment extends BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long idx;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "zone_sub_idx", nullable = false)
    private ZoneSub zoneSub;

    @Column(name = "worker_idx", nullable = false)
    private Long workerIdx;

    @Column(name = "work_date")
    private LocalDate workDate;

    /** 현장 코드 스냅샷 — worker.siteCode 기준, 현장별 배치 현황 조회·초기화·확정 지원 */
    @Column(name = "site_code", length = 30)
    private String siteCode;
}
