package org.example.dndncore.worker.repository;

import org.example.dndncore.worker.model.entity.AttendanceLog;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.LocalDate;
import java.util.List;

public interface AttendanceLogRepository extends JpaRepository<AttendanceLog, Long> {

    List<AttendanceLog> findAllByWorkerIdxAndWorkDateOrderByRecognizedAt(Long workerIdx, LocalDate workDate);

    List<AttendanceLog> findAllByWorkerIdxAndWorkDateBetween(Long workerIdx, LocalDate from, LocalDate to);

    void deleteAllByWorkerIdxAndWorkDate(Long workerIdx, LocalDate workDate);
}
