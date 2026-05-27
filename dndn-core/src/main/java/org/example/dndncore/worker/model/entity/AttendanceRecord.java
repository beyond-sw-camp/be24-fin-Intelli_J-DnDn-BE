package org.example.dndncore.worker.model.entity;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.persistence.*;
import lombok.*;
import org.example.dndncore.common.model.BaseEntity;
import org.example.dndncore.worker.model.enums.AttendanceStatus;
import org.example.dndncore.worker.model.enums.EmploymentKind;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalTime;

/**
 * 근무자 일자별 근태 기록.
 * 구역·공종 배치 이력은 {@code staffing_log} 에서 관리한다.
 */
@Entity
@Table(
        name = "attendance_record",
        uniqueConstraints = @UniqueConstraint(columnNames = {"worker_idx", "work_date"})
)
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor(access = AccessLevel.PRIVATE)
@Builder
@Schema(description = "근태 기록 엔티티")
public class AttendanceRecord extends BaseEntity {

    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Schema(description = "근태 기록 ID", example = "1")
    private Long idx;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "worker_idx", nullable = false)
    @Schema(description = "작업자")
    private Worker worker;

    @Column(name = "work_date", nullable = false)
    @Schema(description = "근무 일자", example = "2026-05-27")
    private LocalDate workDate;

    @Schema(description = "출근 시각", example = "08:00:00", nullable = true)
    private LocalTime clockIn;

    @Schema(description = "퇴근 시각", example = "18:00:00", nullable = true)
    private LocalTime clockOut;

    @Column(precision = 3, scale = 1)
    @Schema(description = "공수", example = "1.0")
    private BigDecimal manDays;

    @Enumerated(EnumType.STRING)
    @Column(length = 15)
    @Schema(description = "출결 상태", example = "PRESENT")
    private AttendanceStatus attendanceStatus;

    @Enumerated(EnumType.STRING)
    @Column(name = "employment_kind", nullable = false, length = 16)
    @Schema(description = "고용 구분", example = "REGULAR")
    private EmploymentKind employmentKind;

    @Column(name = "site_code", length = 30)
    @Schema(description = "현장 코드", example = "SITE01")
    private String siteCode;
}
