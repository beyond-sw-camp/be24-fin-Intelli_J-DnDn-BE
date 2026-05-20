package org.example.dndndocumentupload.document.model.entity;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.example.dndncore.common.model.BaseEntity;
import org.example.dndncore.project.model.entity.MasterSchedule;

@AllArgsConstructor
@NoArgsConstructor
@Getter
@Builder
@Entity
@Table(name = "schedule_ai_analysis")
public class ScheduleAiAnalysis extends BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long idx;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "master_schedule_id", nullable = false)
    private MasterSchedule masterSchedule;

    @Lob
    @Column(nullable = false, columnDefinition = "LONGTEXT")
    private String rawJson;

    @Column(nullable = false)
    private String status; // SUCCESS, FAILED

    @Lob
    @Column(columnDefinition = "TEXT")
    private String errorMessage;

    public void success(String rawJson) {
        this.rawJson = rawJson;
        this.status = "SUCCESS";
        this.errorMessage = null;
    }

    public void fail(String errorMessage) {
        this.status = "FAILED";
        this.errorMessage = errorMessage;
    }
}