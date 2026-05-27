package org.example.dndncore.worker.model.entity;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.persistence.*;
import lombok.*;
import org.example.dndncore.worker.model.enums.AttendanceEventType;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;

@Entity
@Table(
        name = "attendance_log",
        indexes = @Index(name = "idx_attendance_log_worker_date", columnList = "worker_idx, work_date")
)
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor(access = AccessLevel.PRIVATE)
@Builder
@Schema(description = "출입 이벤트 로그 엔티티")
public class AttendanceLog {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Schema(description = "로그 ID", example = "1")
    private Long idx;

    @Column(name = "worker_idx", nullable = false)
    @Schema(description = "작업자 ID", example = "1")
    private Long workerIdx;

    @Column(name = "site_code", length = 30)
    @Schema(description = "현장 코드", example = "SITE01")
    private String siteCode;

    @Column(name = "work_date", nullable = false)
    @Schema(description = "근무 일자", example = "2026-05-27")
    private LocalDate workDate;

    @Enumerated(EnumType.STRING)
    @Column(name = "event_type", nullable = false, length = 12)
    @Schema(description = "이벤트 유형", example = "CLOCK_IN")
    private AttendanceEventType eventType;

    @Column(name = "recognized_at", nullable = false)
    @Schema(description = "인식 시각", example = "08:00:00")
    private LocalTime recognizedAt;

    @Column(name = "created_at", nullable = false, updatable = false)
    @Schema(description = "기록 시각", example = "2026-05-27T08:00:05")
    private LocalDateTime createdAt;

    @PrePersist
    protected void onCreate() {
        this.createdAt = LocalDateTime.now();
    }
}
