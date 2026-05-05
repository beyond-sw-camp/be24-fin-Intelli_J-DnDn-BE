package org.example.dndn.staffing.service;

import lombok.RequiredArgsConstructor;
import org.example.dndn.staffing.model.StaffingDto;
import org.example.dndn.staffing.repository.StaffingAssignmentRepository;
import org.example.dndn.staffing.repository.ZoneMainRepository;
import org.example.dndn.worker.service.AttendanceDeploymentSyncService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.List;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class StaffingService {

    private final StaffingAssignmentRepository assignmentRepository;
    private final ZoneMainRepository zoneMainRepository;
    private final AttendanceDeploymentSyncService attendanceDeploymentSyncService;

    // STAFFING_003 — 인력 배치 보드 좌측 기본 구역 트리(ZoneMain · ZoneSub 요약 및 집계)
    public List<StaffingDto.ZoneMainRes> loadZoneMainTree() {
        return zoneMainRepository.findAllByOrderByDisplayOrderAsc().stream()
                .map(StaffingDto.ZoneMainRes::from)
                .toList();
    }

    // STAFFING_002 — 투입 인원 초기화
    @Transactional
    public void resetBoard(LocalDate rosterDate) {
        LocalDate date = rosterDate != null ? rosterDate : LocalDate.now();
        for (Long workerIdx : assignmentRepository.findDistinctAssignedWorkerIdxes()) {
            attendanceDeploymentSyncService.clearPlacementIfPresent(workerIdx, date);
        }
        assignmentRepository.deleteAll();
    }
}
