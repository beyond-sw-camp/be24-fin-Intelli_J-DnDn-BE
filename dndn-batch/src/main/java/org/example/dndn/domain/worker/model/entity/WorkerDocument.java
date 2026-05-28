package org.example.dndn.domain.worker.model.entity;

import jakarta.persistence.*;
import lombok.*;
import org.example.dndn.domain.common.model.BaseEntity;

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

    @Column(nullable = false, length = 100)
    private String title;

    @Column(length = 500)
    private String fileUrl;

    @Column(length = 200)
    private String storedFileName;
}
