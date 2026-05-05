package org.example.dndn.staffing.service;

import lombok.RequiredArgsConstructor;
import org.example.dndn.staffing.repository.StaffingAssignmentRepository;
import org.example.dndn.worker.service.AttendanceDeploymentSyncService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;

/**
 * 인력 배치 서비스. 단계별 구현(STAFFING_002부터 시작).
 */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class StaffingService {

    private final StaffingAssignmentRepository assignmentRepository;
    private final AttendanceDeploymentSyncService attendanceDeploymentSyncService;

    /**
     * STAFFING_002 — 투입 인원 초기화.
     * 보드 상의 모든 {@code staffing_assignment} 를 삭제하고,
     * 선택한 근무일({@code rosterDate})의 명단 행이 있으면 해당 작업자의 구역·공종 스냅샷을 비운다.
     */
    @Transactional
    public void resetBoard(LocalDate rosterDate) {
        LocalDate date = rosterDate != null ? rosterDate : LocalDate.now();
        for (Long workerIdx : assignmentRepository.findDistinctAssignedWorkerIdxes()) {
            attendanceDeploymentSyncService.clearPlacementIfPresent(workerIdx, date);
        }
        assignmentRepository.deleteAll();
    }
}
