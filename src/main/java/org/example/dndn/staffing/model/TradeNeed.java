package org.example.dndn.staffing.model;

import jakarta.persistence.*;
import lombok.*;
import org.example.dndn.common.model.BaseEntity;

@Entity
@Table(
        name = "trade_need",
        uniqueConstraints = @UniqueConstraint(columnNames = {"zone_sub_idx", "trade"})
)
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor(access = AccessLevel.PRIVATE)
@Builder
public class TradeNeed extends BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long idx;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "zone_sub_idx", nullable = false)
    private ZoneSub zoneSub;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 10)
    private Trade trade;

    @Column(nullable = false)
    private int need;
}
