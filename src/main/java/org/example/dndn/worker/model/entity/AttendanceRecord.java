package org.example.dndn.worker.model.entity;

import jakarta.persistence.*;
import lombok.*;
import org.example.dndn.common.model.BaseEntity;
import org.example.dndn.worker.model.enums.AttendanceStatus;
import org.example.dndn.worker.model.enums.EmploymentKind;

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
public class AttendanceRecord extends BaseEntity {

    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long idx;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "worker_idx", nullable = false)
    private Worker worker;

    @Column(name = "work_date", nullable = false)
    private LocalDate workDate;

    private LocalTime clockIn;
    private LocalTime clockOut;

    /** 산정 공수 (12:00 이전 퇴근 0.5 / 18:00 이전 1.0 / 그 이후 1.5) */
    @Column(precision = 3, scale = 1)
    private BigDecimal manDays;

    @Enumerated(EnumType.STRING)
    @Column(length = 15)
    private AttendanceStatus attendanceStatus;

    /** 당일 고용 구분 — 상용({@link EmploymentKind#REGULAR}) */
    @Enumerated(EnumType.STRING)
    @Column(name = "employment_kind", nullable = false, length = 16)
    private EmploymentKind employmentKind;

    /** 현장 코드 스냅샷 — worker.siteCode 기준, 현장별 출결 조회 지원 */
    @Column(name = "site_code", length = 30)
    private String siteCode;
}
