package org.example.dndncore.staffing.model;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.persistence.*;
import lombok.*;
import org.example.dndncore.common.model.BaseEntity;
import org.example.dndncore.project.model.entity.Project;
import org.hibernate.annotations.BatchSize;

import java.util.ArrayList;
import java.util.List;

@Entity
@Table(name = "zone_main")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor(access = AccessLevel.PRIVATE)
@Builder
@Schema(description = "인력 배치 기본 구역 엔티티")
public class ZoneMain extends BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Schema(description = "기본 구역 ID", example = "1")
    private Long idx;

    @Column(nullable = false, length = 100)
    @Schema(description = "기본 구역명", example = "골조 공정")
    private String title;

    @Schema(description = "표시 순서", example = "1")
    private int displayOrder;

    @Column(name = "schedule_generated", nullable = false)
    @Builder.Default
    @Schema(description = "일정 기반 자동 생성 여부", example = "false")
    private boolean scheduleGenerated = false;

    @Column(name = "source_key", length = 80)
    @Schema(description = "원본 식별 키", example = "WP-202605-001")
    private String sourceKey;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "project_id")
    @Schema(description = "연결 프로젝트")
    private Project project;

    @BatchSize(size = 64)
    @OneToMany(mappedBy = "zoneMain", cascade = CascadeType.ALL, orphanRemoval = true)
    @Builder.Default
    @OrderBy("displayOrder ASC")
    @Schema(description = "하위 상세 구역 목록")
    private List<ZoneSub> zoneSubs = new ArrayList<>();

    public void updateScheduleGroup(String title, int displayOrder, String sourceKey, Project project) {
        this.title = title;
        this.displayOrder = displayOrder;
        this.scheduleGenerated = true;
        this.sourceKey = sourceKey;
        if (project != null) this.project = project;
    }
}
