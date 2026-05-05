package org.example.dndn.worker.service;

import lombok.RequiredArgsConstructor;
import org.example.dndn.worker.model.entity.AttendanceRecord;
import org.example.dndn.worker.repository.AttendanceRecordRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.Optional;

// 인력 배치 초기화(STAFFING_002) 등과 연동하여 AttendanceRecord의 당일 구역·공종 필드를 초기화
@Service
@RequiredArgsConstructor
public class AttendanceDeploymentSyncService {

    private final AttendanceRecordRepository attendanceRepository;

    @Transactional
    public void clearPlacementIfPresent(Long workerIdx, LocalDate workDate) {
        Optional<AttendanceRecord> prev = attendanceRepository.findByWorkerIdxAndWorkDate(workerIdx, workDate);
        if (prev.isEmpty()) {
            return;
        }
        AttendanceRecord old = prev.get();

        attendanceRepository.delete(old);
        attendanceRepository.flush();
        attendanceRepository.save(AttendanceRecord.builder()
                .worker(old.getWorker())
                .workDate(old.getWorkDate())
                .clockIn(old.getClockIn())
                .clockOut(old.getClockOut())
                .manDays(old.getManDays())
                .attendanceStatus(old.getAttendanceStatus())
                .zoneMain(null)
                .zoneSub(null)
                .assignedTrade(old.getAssignedTrade())
                .employmentKind(old.getEmploymentKind())
                .build());
    }
}
