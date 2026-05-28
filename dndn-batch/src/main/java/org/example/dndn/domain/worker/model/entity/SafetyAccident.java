package org.example.dndn.domain.worker.model.entity;

import jakarta.persistence.*;
import lombok.*;
import org.example.dndn.domain.common.model.BaseEntity;

import java.time.LocalDate;

@Entity
@Table(name = "safety_accident")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor(access = AccessLevel.PRIVATE)
@Builder
public class SafetyAccident extends BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long idx;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "worker_idx", nullable = false)
    private Worker worker;

    @Column(nullable = false)
    private LocalDate occurredAt;

    @Column(length = 50)
    private String accidentType;

    @Column(name = "zone_main", length = 50)
    private String zoneMain;

    @Column(name = "zone_sub", length = 100)
    private String zoneSub;

    @Column(length = 500)
    private String resolution;
}
