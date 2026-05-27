package org.example.dndncore.staffing.model;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.persistence.*;
import lombok.*;
import org.example.dndncore.common.model.BaseEntity;

@Entity
@Table(
        name = "trade_need",
        uniqueConstraints = @UniqueConstraint(columnNames = {"zone_sub_idx", "trade"})
)
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor(access = AccessLevel.PRIVATE)
@Builder
@Schema(description = "상세 구역 직종별 필요 인원 엔티티")
public class TradeNeed extends BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Schema(description = "직종 필요 인원 ID", example = "1")
    private Long idx;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "zone_sub_idx", nullable = false)
    @Schema(description = "상세 구역")
    private ZoneSub zoneSub;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 10)
    @Schema(description = "직종", example = "CARPENTER")
    private Trade trade;

    @Column(nullable = false)
    @Schema(description = "필요 인원 수", example = "3")
    private int need;
}
