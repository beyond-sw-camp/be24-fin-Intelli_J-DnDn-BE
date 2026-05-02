package org.example.dndn.worker.model.entity;

import jakarta.persistence.*;
import lombok.*;
import org.example.dndn.common.model.BaseEntity;
import org.example.dndn.worker.model.enums.AttendanceStatus;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalTime;

/**
 * 근무자 일자별 근태 기록.
 * **`AttendanceStatus` 의 저장 위치는 여기뿐**이다 (`Worker` 에 두지 않음 — 마스터와 일별 근태 분리).
 * - 근무자 관리 페이지의 출·퇴근 컬럼 / 상태 칩 (조회 기준일의 행이 없으면 UI 에서 결근 등으로 표시)
 * - 상세 프로필의 월별 캘린더
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

    /** 그날 배치되어 있던 구역명 (캘린더 셀 표기용) */
    @Column(length = 100)
    private String zone;

    /** 마감된 현장은 수정 불가 */
    @Column(nullable = false)
    private boolean closed;
}
