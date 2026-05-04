package org.example.dndn.project.model.entity;

import jakarta.persistence.*;
import lombok.*;
import org.example.dndn.common.model.BaseEntity;

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
    private String name;        // 프로젝트명 (예: "OO아파트 신축공사")

    private String location;    // 현장 위치
    private LocalDate startDate; // 공사 시작일
    private LocalDate endDate;   // 공사 종료일

    public void update(String name, String location, LocalDate startDate, LocalDate endDate) {
        this.name = name;
        this.location = location;
        this.startDate = startDate;
        this.endDate = endDate;
    }
}