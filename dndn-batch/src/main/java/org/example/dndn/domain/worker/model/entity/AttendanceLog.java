package org.example.dndn.domain.worker.model.entity;

import jakarta.persistence.*;
import lombok.*;
import org.example.dndn.domain.worker.model.enums.AttendanceEventType;

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
public class AttendanceLog {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long idx;

    @Column(name = "worker_idx", nullable = false)
    private Long workerIdx;

    @Column(name = "site_code", length = 30)
    private String siteCode;

    @Column(name = "work_date", nullable = false)
    private LocalDate workDate;

    @Enumerated(EnumType.STRING)
    @Column(name = "event_type", nullable = false, length = 12)
    private AttendanceEventType eventType;

    @Column(name = "recognized_at", nullable = false)
    private LocalTime recognizedAt;

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @PrePersist
    protected void onCreate() {
        this.createdAt = LocalDateTime.now();
    }
}
