package org.example.dndn.worker.service;

import lombok.RequiredArgsConstructor;
import org.example.dndn.worker.model.entity.AttendanceRecord;
import org.example.dndn.worker.repository.AttendanceRecordRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.Optional;

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
        replaceZones(prev.get(), null, null);
    }

    // 당일 명단 행이 있으면 구역 문자열만 갱신. 공종은 유지(STAFFING_002·006 등 연동용).
    @Transactional
    public void applyZonePlacementIfPresent(Long workerIdx, LocalDate workDate, String zoneMain, String zoneSub) {
        Optional<AttendanceRecord> prev = attendanceRepository.findByWorkerIdxAndWorkDate(workerIdx, workDate);
        if (prev.isEmpty()) {
            return;
        }
        replaceZones(prev.get(), blankToNull(zoneMain), blankToNull(zoneSub));
    }

    private void replaceZones(AttendanceRecord old, String zoneMain, String zoneSub) {
        attendanceRepository.delete(old);
        attendanceRepository.flush();
        attendanceRepository.save(AttendanceRecord.builder()
                .worker(old.getWorker())
                .workDate(old.getWorkDate())
                .clockIn(old.getClockIn())
                .clockOut(old.getClockOut())
                .manDays(old.getManDays())
                .attendanceStatus(old.getAttendanceStatus())
                .zoneMain(zoneMain)
                .zoneSub(zoneSub)
                .assignedTrade(old.getAssignedTrade())
                .employmentKind(old.getEmploymentKind())
                .build());
    }

    private static String blankToNull(String s) {
        if (s == null) {
            return null;
        }
        String t = s.trim();
        return t.isEmpty() ? null : t;
    }
}
