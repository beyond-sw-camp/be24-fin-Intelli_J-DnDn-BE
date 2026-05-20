package org.example.dndndocumentupload.document.model.entity;

import jakarta.persistence.*;
import lombok.*;
import org.example.dndndocumentupload.common.model.BaseEntity;
import org.example.dndndocumentupload.document.model.DocType;

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

    // 현장 ID
    private Long projectIdx;

    private String fileName;    // 원본 파일명 (표시용)

    @Column(nullable = false)
    private String fileUrl;     // 업로드된 파일 경로

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private DocType docType;    // 마스터/마일스톤/보할/공종별/작업지시/공사일보

    // 협력사 여부
    public Boolean isPartner;

    // 소속 명칭 (본사 or 협력사 이름)
    public String affiliationName;

    // 업로드 한 사람의 ID
    public Long uploaderIdx;

    // 업로드 한 사람의 이름
    public String uploaderName;
    
    // 삭제 여부
    public boolean isDelete;
    
    // 공종 이름
    public String tradeName;
    
    // 작업지시/공사일보의 ID 값
    public Long refId;

    public void update(DocType docType, String fileUrl, String fileName) {
        this.docType = docType;
        this.fileUrl = fileUrl;
        this.fileName = fileName;
    }
}