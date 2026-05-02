package org.example.dndn.worker.model.entity;

import jakarta.persistence.*;
import lombok.*;
import org.example.dndn.common.model.BaseEntity;

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

    /** 사고 유형 (예: 추락, 절단, 자상 등) */
    @Column(length = 50)
    private String accidentType;

    /** 발생 구역 (예: B구역 (업무동) 3층) */
    @Column(length = 100)
    private String zone;

    /** 조치 결과 / 후속 조치 */
    @Column(length = 500)
    private String resolution;

    /** 중대 사고 여부 */
    @Column(nullable = false)
    private boolean severe;
}
