package org.example.dndn.staffing.model;

import jakarta.persistence.*;
import lombok.*;
import org.example.dndn.common.model.BaseEntity;

import java.util.ArrayList;
import java.util.List;

@Entity
@Table(name = "zone_sub")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor(access = AccessLevel.PRIVATE)
@Builder
public class ZoneSub extends BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long idx;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "zone_main_idx", nullable = false)
    private ZoneMain zoneMain;

    @Column(nullable = false, length = 50)
    private String title;

    private int required;

    private int displayOrder;

    @OneToMany(mappedBy = "zoneSub", cascade = CascadeType.ALL, orphanRemoval = true)
    @Builder.Default
    private List<StaffingAssignment> assignments = new ArrayList<>();
}
