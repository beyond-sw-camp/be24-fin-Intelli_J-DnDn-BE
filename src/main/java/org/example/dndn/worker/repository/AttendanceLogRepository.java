package org.example.dndn.worker.repository;

import org.example.dndn.worker.model.entity.AttendanceLog;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.LocalDate;
import java.util.List;

public interface AttendanceLogRepository extends JpaRepository<AttendanceLog, Long> {

    List<AttendanceLog> findAllByWorkerIdxAndWorkDateOrderByRecognizedAt(Long workerIdx, LocalDate workDate);
}
