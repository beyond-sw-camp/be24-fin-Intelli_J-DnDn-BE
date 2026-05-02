package org.example.dndn.worker.repository;

import org.example.dndn.worker.model.entity.AttendanceRecord;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

public interface AttendanceRecordRepository extends JpaRepository<AttendanceRecord, Long> {
    // 특정 날짜의 모든 근태 (목록 N+1 방지용)
    List<AttendanceRecord> findAllByWorkDate(LocalDate workDate);

    // 상세 프로필 캘린더용 (workerId + 기간)
    List<AttendanceRecord> findAllByWorkerIdxAndWorkDateBetweenOrderByWorkDateDesc(
            Long workerIdx, LocalDate from, LocalDate to);

    // 일자별 근태 upsert
    Optional<AttendanceRecord> findByWorkerIdxAndWorkDate(Long workerIdx, LocalDate workDate);
}
