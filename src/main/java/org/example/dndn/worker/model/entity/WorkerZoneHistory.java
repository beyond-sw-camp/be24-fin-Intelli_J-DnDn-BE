package org.example.dndn.worker.model.entity;

import jakarta.persistence.*;
import lombok.*;
import org.example.dndn.common.model.BaseEntity;

import java.time.LocalDate;

@Entity
@Table(name = "worker_zone_history")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor(access = AccessLevel.PRIVATE)
@Builder
public class WorkerZoneHistory extends BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long idx;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "worker_idx", nullable = false)
    private Worker worker;

    @Column(nullable = false)
    private LocalDate assignedAt;

    /** 배치 구역 (예: A구역 (지하주차장)) */
    @Column(nullable = false, length = 100)
    private String zone;

    /** 작업 내용 (공종/세부작업) */
    @Column(length = 200)
    private String workType;

    /** 그 시점의 소속 협력사 snapshot (Worker.partnerCompany 가 변경될 수 있어 이력성으로 별도 보관) */
    @Column(length = 50)
    private String partnerCompanySnapshot;
}