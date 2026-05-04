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
 * **`AttendanceStatus` 의 저장 위치는 여기뿐**이다 (`Worker` 에 두지 않음 — 마스터와 일별 근태 분리).
 * **`EmploymentKind`(상용/일용)** 는 명단·캘린더용으로 **일별로 본 테이블에 스냅샷**한다. 인력 연동 마스터 값은 {@link org.example.dndn.worker.model.entity.Worker#getEmploymentKind()} 를 따른다 ({@code Worker.subLabel} 은 마스터 공종).
 * - 근무자 관리 페이지의 출·퇴근 컬럼 / 상태 칩 (조회 기준일의 행이 없으면 UI 에서 결근 등으로 표시)
 * - 상세 프로필의 월별 캘린더, **구역·공종 배치 요약(`MANAGEMENT_007`)** — 별도 `worker_zone_history` 테이블 없이 본 테이블만 사용
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

    /** 그날 배치 기본 구역 (동 단위·코어 존 등) */
    @Column(name = "zone_main", length = 50)
    private String zoneMain;

    /** 상세 위치 (층·코너·작업면 등). 없으면 null */
    @Column(name = "zone_sub", length = 100)
    private String zoneSub;

    /** 당일 투입 공종(목공·철근 등). 인사·현장 시스템에서 내려준 문자열 스냅샷 */
    @Column(name = "assigned_trade", length = 80)
    private String assignedTrade;

    /** 당일 고용 구분 — 상용({@link EmploymentKind#REGULAR}) */
    @Enumerated(EnumType.STRING)
    @Column(name = "employment_kind", nullable = false, length = 16)
    private EmploymentKind employmentKind;
}
