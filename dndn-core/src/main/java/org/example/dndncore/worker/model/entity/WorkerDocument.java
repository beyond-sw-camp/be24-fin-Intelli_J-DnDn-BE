package org.example.dndncore.worker.model.entity;

import jakarta.persistence.*;
import lombok.*;
import org.example.dndncore.common.model.BaseEntity;

@Entity
@Table(name = "worker_document")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor(access = AccessLevel.PRIVATE)
@Builder
public class WorkerDocument extends BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long idx;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "worker_idx", nullable = false)
    private Worker worker;

    @Column(name = "worker_idx", insertable = false, updatable = false)
    private Long workerIdx;

    /** 서류명 (예: 기초안전보건교육 이수증) */
    @Column(nullable = false, length = 100)
    private String title;

    /** 다운로드 URL */
    @Column(length = 500)
    private String fileUrl;

    /** 저장 파일명 */
    @Column(length = 200)
    private String storedFileName;
}