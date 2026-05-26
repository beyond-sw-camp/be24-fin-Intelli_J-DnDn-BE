package org.example.dndncore.gate.model;

import jakarta.persistence.*;
import lombok.*;
import org.example.dndncore.common.model.BaseEntity;
import org.example.dndncore.project.model.entity.Project;

@AllArgsConstructor
@NoArgsConstructor
@Getter
@Builder
@Entity
@Table(
        name = "gate_blueprint",
        uniqueConstraints = @UniqueConstraint(
                name = "uk_gate_blueprint_project",
                columnNames = "project_idx"
        )
)
public class GateBlueprint extends BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long idx;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "project_idx", nullable = false)
    private Project project;

    @Lob
    @Column(name = "data_url", nullable = false, columnDefinition = "LONGTEXT")
    private String dataUrl;

    @Column(name = "original_file_name")
    private String originalFileName;

    public static GateBlueprint create(Project project, String dataUrl, String originalFileName) {
        return GateBlueprint.builder()
                .project(project)
                .dataUrl(dataUrl)
                .originalFileName(cleanFileName(originalFileName))
                .build();
    }

    public void updateDataUrl(String dataUrl, String originalFileName) {
        this.dataUrl = dataUrl;
        this.originalFileName = cleanFileName(originalFileName);
    }

    private static String cleanFileName(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        String trimmed = value.trim();
        return trimmed.length() > 255 ? trimmed.substring(0, 255) : trimmed;
    }
}
