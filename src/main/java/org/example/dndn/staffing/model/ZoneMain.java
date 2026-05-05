package org.example.dndn.staffing.model;

import jakarta.persistence.*;
import lombok.*;
import org.example.dndn.common.model.BaseEntity;

import java.util.ArrayList;
import java.util.List;

@Entity
@Table(name = "zone_main")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor(access = AccessLevel.PRIVATE)
@Builder
public class ZoneMain extends BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long idx;

    @Column(nullable = false, length = 30)
    private String title;

    private int displayOrder;

    @OneToMany(mappedBy = "zoneMain", cascade = CascadeType.ALL, orphanRemoval = true)
    @Builder.Default
    @OrderBy("displayOrder ASC")
    private List<ZoneSub> zoneSubs = new ArrayList<>();
}
