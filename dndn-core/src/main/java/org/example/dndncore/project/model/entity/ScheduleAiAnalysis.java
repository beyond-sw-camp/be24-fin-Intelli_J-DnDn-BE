package org.example.dndncore.project.model.entity;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.persistence.*;
import lombok.*;
import org.example.dndncore.common.model.BaseEntity;

@AllArgsConstructor
@NoArgsConstructor
@Getter
@Builder
@Entity
@Table(name = "schedule_ai_analysis")
@Schema(description = "공정표 AI 분석 엔티티")
public class ScheduleAiAnalysis extends BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Schema(description = "AI 분석 ID", example = "1")
    private Long idx;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "master_schedule_id", nullable = false)
    @Schema(description = "마스터 공정표")
    private MasterSchedule masterSchedule;

    @Lob
    @Column(nullable = false, columnDefinition = "LONGTEXT")
    @Schema(description = "AI 분석 원본 JSON")
    private String rawJson;

    @Column(nullable = false)
    @Schema(description = "분석 상태", example = "SUCCESS")
    private String status;

    @Lob
    @Column(columnDefinition = "TEXT")
    @Schema(description = "오류 메시지")
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