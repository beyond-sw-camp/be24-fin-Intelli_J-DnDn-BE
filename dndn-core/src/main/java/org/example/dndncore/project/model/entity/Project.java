package org.example.dndncore.project.model.entity;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.persistence.*;
import lombok.*;
import org.example.dndncore.common.model.BaseEntity;

import java.time.LocalDate;

@AllArgsConstructor
@NoArgsConstructor
@Getter
@Builder
@Entity
@Table(name = "project")
@Schema(description = "프로젝트 엔티티")
public class Project extends BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Schema(description = "프로젝트 ID", example = "1")
    private Long idx;

    @Column(nullable = false)
    @Schema(description = "프로젝트명", example = "OO아파트 신축공사")
    private String name;

    @Schema(description = "현장 위치", example = "서울시 강남구")
    private String location;
    @Schema(description = "공사 시작일", example = "2026-01-01")
    private LocalDate startDate;
    @Schema(description = "공사 종료일", example = "2026-12-31")
    private LocalDate endDate;

    @Column(nullable = false)
    @Builder.Default
    @Schema(description = "활성 상태", example = "true")
    private boolean active = true;

    public void update(String name, String location, LocalDate startDate, LocalDate endDate) {
        this.name = name;
        this.location = location;
        this.startDate = startDate;
        this.endDate = endDate;
    }

    public void deactivate() {
        this.active = false;
    }

    public void activate() {
        this.active = true;
    }
}