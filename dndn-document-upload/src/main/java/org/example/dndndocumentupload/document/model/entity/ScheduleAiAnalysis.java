package org.example.dndndocumentupload.document.model.entity;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.example.dndndocumentupload.common.model.BaseEntity;

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

    private Long masterScheduleIdx;

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