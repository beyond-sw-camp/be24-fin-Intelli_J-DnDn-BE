package org.example.dndn.project.model.entity;

import jakarta.persistence.*;
import lombok.*;
import org.example.dndn.common.model.BaseEntity;
import org.example.dndn.project.model.enums.DocType;

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
    private DocType docType;    // 마스터/마일스톤/보할/공종별

    @Column(nullable = false)
    private String fileUrl;     // 업로드된 파일 경로

    private String fileName;    // 원본 파일명 (표시용)

    public void update(DocType docType, String fileUrl, String fileName) {
        this.docType = docType;
        this.fileUrl = fileUrl;
        this.fileName = fileName;
    }
}