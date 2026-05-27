package org.example.dndncore.worker.model.entity;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.persistence.*;
import lombok.*;
import org.example.dndncore.common.model.BaseEntity;

@Entity
@Table(name = "worker_document")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor(access = AccessLevel.PRIVATE)
@Builder
@Schema(description = "작업자 서류 엔티티")
public class WorkerDocument extends BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Schema(description = "서류 ID", example = "1")
    private Long idx;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "worker_idx", nullable = false)
    @Schema(description = "작업자")
    private Worker worker;

    @Column(name = "worker_idx", insertable = false, updatable = false)
    @Schema(description = "작업자 ID", example = "1")
    private Long workerIdx;

    @Column(nullable = false, length = 100)
    @Schema(description = "서류 제목", example = "기초안전보건교육 이수증")
    private String title;

    @Column(length = 500)
    @Schema(description = "다운로드 URL", example = "https://example.com/docs/1.pdf")
    private String fileUrl;

    @Column(length = 200)
    @Schema(description = "저장 파일명", example = "doc_20240115_abc123.pdf")
    private String storedFileName;
}