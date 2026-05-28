package org.example.dndn.domain.project.model.entity;

import jakarta.persistence.*;
import lombok.*;
import org.example.dndn.domain.common.model.BaseEntity;

import java.time.LocalDate;

@AllArgsConstructor
@NoArgsConstructor
@Getter
@Builder
@Entity
@Table(name = "project")
public class Project extends BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long idx;

    @Column(nullable = false)
    private String name;

    private String location;
    private LocalDate startDate;
    private LocalDate endDate;

    @Column(nullable = false)
    @Builder.Default
    private boolean active = true;
}
