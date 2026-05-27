package org.example.dndncore.project.model.entity;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.persistence.*;
import lombok.*;
import org.example.dndncore.common.model.BaseEntity;
import org.example.dndncore.project.model.enums.DocType;

@AllArgsConstructor
@NoArgsConstructor
@Getter
@Builder
@Entity
@Table(name = "master_schedule")
@Schema(description = "마스터 공정표 엔티티")
public class MasterSchedule extends BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Schema(description = "공정표 ID", example = "1")
    private Long idx;

    @Setter
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "project_id", nullable = false)
    @Schema(description = "프로젝트")
    private Project project;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    @Schema(description = "문서 타입", example = "MASTER")
    private DocType docType;

    @Column(nullable = false)
    @Schema(description = "파일 URL")
    private String fileUrl;

    @Schema(description = "원본 파일명", example = "master-schedule.xlsx")
    private String fileName;

    // feat : 협력사 여부
    @Schema(description = "협력사 문서 여부", example = "false")
    public Boolean isPartner;

    // feat : 소속 명칭
    @Schema(description = "소속 명칭", example = "본사")
    public String affiliationName;

    // feat : 작성자 이름
    @Schema(description = "작성자 이름", example = "홍길동")
    public String name;

    public void update(DocType docType, String fileUrl, String fileName) {
        this.docType = docType;
        this.fileUrl = fileUrl;
        this.fileName = fileName;
    }
}