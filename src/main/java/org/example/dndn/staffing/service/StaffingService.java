package org.example.dndn.staffing.service;

import lombok.RequiredArgsConstructor;
import org.example.dndn.common.exception.BaseException;
import org.example.dndn.staffing.model.StaffingAssignment;
import org.example.dndn.staffing.model.StaffingDto;
import org.example.dndn.staffing.model.Trade;
import org.example.dndn.staffing.model.ZoneSub;
import org.example.dndn.staffing.repository.StaffingAssignmentRepository;
import org.example.dndn.staffing.repository.ZoneMainRepository;
import org.example.dndn.staffing.repository.ZoneSubRepository;
import org.example.dndn.worker.model.entity.Worker;
import org.example.dndn.worker.repository.WorkerRepository;
import org.example.dndn.worker.service.AttendanceDeploymentSyncService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import static org.example.dndn.common.model.BaseResponseStatus.FAIL;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class StaffingService {

    private final StaffingAssignmentRepository assignmentRepository;
    private final ZoneMainRepository zoneMainRepository;
    private final ZoneSubRepository zoneSubRepository;
    private final WorkerRepository workerRepository;
    private final AttendanceDeploymentSyncService attendanceDeploymentSyncService;

    // STAFFING_003 — 인력 배치 보드 좌측 기본 구역 트리(ZoneMain · ZoneSub 요약 및 집계)
    public List<StaffingDto.ZoneMainRes> loadZoneMainTree() {
        return zoneMainRepository.findAllByOrderByDisplayOrderAsc().stream()
                .map(StaffingDto.ZoneMainRes::from)
                .toList();
    }

    /** STAFFING_004 — 상세 구역(ZoneSub) 단건 및 직종별 충원률 원천 데이터 */
    public StaffingDto.ZoneSubRes loadZoneSubDetail(Long zoneSubIdx) {
        ZoneSub zs = zoneSubRepository.findWithStaffingRelationsByIdx(zoneSubIdx)
                .orElseThrow(() -> new BaseException(FAIL));
        EnumMap<Trade, Integer> filledByTrade = countAssignmentsByTrade(zs);
        return StaffingDto.ZoneSubRes.from(zs, filledByTrade);
    }

    private EnumMap<Trade, Integer> countAssignmentsByTrade(ZoneSub zs) {
        List<StaffingAssignment> assigns = zs.getAssignments();
        if (assigns.isEmpty()) {
            return new EnumMap<>(Trade.class);
        }

        List<Long> workerIds = assigns.stream()
                .map(StaffingAssignment::getWorkerIdx)
                .distinct()
                .toList();

        Map<Long, Worker> workers = workerRepository.findAllById(workerIds).stream()
                .collect(Collectors.toMap(Worker::getIdx, w -> w, (a, b) -> a));

        EnumMap<Trade, Integer> out = new EnumMap<>(Trade.class);
        for (StaffingAssignment a : assigns) {
            Worker w = workers.get(a.getWorkerIdx());
            Trade t = Trade.classifyWorker(w);
            if (t != null) {
                out.merge(t, 1, Integer::sum);
            }
        }
        return out;
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
