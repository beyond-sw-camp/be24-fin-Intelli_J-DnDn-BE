package org.example.dndn.worker.model.entity;

import jakarta.persistence.*;
import lombok.*;
import org.example.dndn.common.model.BaseEntity;

import java.time.LocalDate;

@Entity
@Table(name = "worker_sanction")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor(access = AccessLevel.PRIVATE)
@Builder
public class WorkerSanction extends BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long idx;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "worker_idx", nullable = false)
    private Worker worker;

    @Column(nullable = false)
    private LocalDate occurredAt;

    /** 유형 (예: 주의, 제재) */
    @Column(length = 20)
    private String type;

    @Column(length = 200)
    private String reason;

    @Column(length = 300)
    private String action;

    /** 현재 유효 여부 (true 시 상세 프로필 상단 우선 표시) */
    @Column(nullable = false)
    private boolean active;
}
