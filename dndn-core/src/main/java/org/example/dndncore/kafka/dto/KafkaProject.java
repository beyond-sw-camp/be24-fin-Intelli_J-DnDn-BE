package org.example.dndncore.kafka.dto;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.example.dndncore.common.model.BaseEntity;

import java.time.LocalDate;

@AllArgsConstructor
@NoArgsConstructor
@Getter
@Builder
@Entity
@Table(name = "kafka_project")
public class KafkaProject extends BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long idx;

    @Column(nullable = false)
    private String name;        // 프로젝트명 (예: "OO아파트 신축공사")

    private String location;    // 현장 위치
    private LocalDate startDate; // 공사 시작일
    private LocalDate endDate;   // 공사 종료일

    @Column(nullable = false)
    @Builder.Default
    private boolean active = true; // false = 운영 종료

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