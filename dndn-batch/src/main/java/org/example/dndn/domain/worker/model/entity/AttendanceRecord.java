package org.example.dndn.domain.worker.model.entity;

import jakarta.persistence.*;
import lombok.*;
import org.example.dndn.domain.common.model.BaseEntity;
import org.example.dndn.domain.worker.model.enums.AttendanceStatus;
import org.example.dndn.domain.worker.model.enums.EmploymentKind;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalTime;

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

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long idx;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "worker_idx", nullable = false)
    private Worker worker;

    @Column(name = "work_date", nullable = false)
    private LocalDate workDate;

    private LocalTime clockIn;
    private LocalTime clockOut;

    @Column(precision = 3, scale = 1)
    private BigDecimal manDays;

    @Enumerated(EnumType.STRING)
    @Column(length = 15)
    private AttendanceStatus attendanceStatus;

    @Enumerated(EnumType.STRING)
    @Column(name = "employment_kind", nullable = false, length = 16)
    private EmploymentKind employmentKind;

    @Column(name = "site_code", length = 30)
    private String siteCode;
}
