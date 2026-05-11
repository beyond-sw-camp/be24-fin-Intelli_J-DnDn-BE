package org.example.dndn.staffing.model;

import jakarta.persistence.*;
import lombok.*;
import org.example.dndn.common.model.BaseEntity;
import org.hibernate.annotations.BatchSize;

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

    @Column(nullable = false, length = 100)
    private String title;

    private int displayOrder;

    @Column(name = "schedule_generated", nullable = false)
    @Builder.Default
    private boolean scheduleGenerated = false;

    @Column(name = "source_key", length = 80)
    private String sourceKey;

    @BatchSize(size = 64)
    @OneToMany(mappedBy = "zoneMain", cascade = CascadeType.ALL, orphanRemoval = true)
    @Builder.Default
    @OrderBy("displayOrder ASC")
    private List<ZoneSub> zoneSubs = new ArrayList<>();

    public void updateScheduleGroup(String title, int displayOrder, String sourceKey) {
        this.title = title;
        this.displayOrder = displayOrder;
        this.scheduleGenerated = true;
        this.sourceKey = sourceKey;
    }
}
