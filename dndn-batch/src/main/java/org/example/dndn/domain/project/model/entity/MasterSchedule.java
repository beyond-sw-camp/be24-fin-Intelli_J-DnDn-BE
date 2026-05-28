package org.example.dndn.domain.project.model.entity;

import jakarta.persistence.*;
import lombok.*;
import org.example.dndn.domain.common.model.BaseEntity;
import org.example.dndn.domain.project.model.enums.DocType;

@AllArgsConstructor
@NoArgsConstructor
@Getter
@Builder
@Entity
@Table(name = "master_schedule")
public class MasterSchedule extends BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long idx;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "project_id", nullable = false)
    private Project project;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private DocType docType;

    @Column(nullable = false)
    private String fileUrl;

    private String fileName;

    public Boolean isPartner;
    public String affiliationName;
    public String name;
}
