package org.example.dndn.staffing.model;

import jakarta.persistence.*;
import lombok.*;
import org.example.dndn.common.model.BaseEntity;

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

    @Column(nullable = false)
    private boolean confirmed;

    /** 최종배치(`/staffing/save`) 반영 여부. 초안 배치에서는 {@code false}. */
    public void markConfirmed(boolean confirmed) {
        this.confirmed = confirmed;
    }
}
