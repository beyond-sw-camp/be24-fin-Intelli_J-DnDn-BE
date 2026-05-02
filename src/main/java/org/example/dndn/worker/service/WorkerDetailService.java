package org.example.dndn.worker.service;

import jakarta.transaction.Transactional;
import lombok.RequiredArgsConstructor;
import org.example.dndn.common.exception.BaseException;
import org.example.dndn.worker.model.dto.WorkerDetailDto;
import org.example.dndn.worker.model.entity.Worker;
import org.example.dndn.worker.repository.*;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.util.List;
import java.util.stream.Collectors;

import static org.example.dndn.common.model.BaseResponseStatus.FAIL;

@Service
@RequiredArgsConstructor
@Transactional
public class WorkerDetailService {
    private final WorkerRepository workerRepository;
    private final AttendanceRecordRepository attendanceRepository;
    private final WorkerDocumentRepository documentRepository;
    private final WorkerZoneHistoryRepository zoneHistoryRepository;
    private final WorkerSanctionRepository sanctionRepository;

    // MANAGEMENT_004 작업자 상세 프로필 조회 (기본 정보 카드)
    public WorkerDetailDto.ProfileRes getProfile(Long workerIdx) {
        Worker w = workerRepository.findById(workerIdx).orElseThrow(() -> new BaseException(FAIL));
        return WorkerDetailDto.ProfileRes.from(w);
    }

    // MANAGEMENT_005 안전 및 서류 현황 조회
    public List<WorkerDetailDto.DocRes> getDocuments(Long workerIdx) {
        ensureExists(workerIdx);
        return documentRepository.findAllByWorkerIdx(workerIdx).stream()
                .map(WorkerDetailDto.DocRes::from)
                .collect(Collectors.toList());
    }

    // MANAGEMENT_006 최근 출결 이력 조회 (월별 캘린더).
    public List<WorkerDetailDto.AttendanceRes> getAttendance(Long workerIdx, String yearMonth) {
        ensureExists(workerIdx);
        LocalDate from, to;
        if (yearMonth == null || yearMonth.isBlank()) {
            LocalDate now = LocalDate.now();
            from = now.withDayOfMonth(1);
            to = now.withDayOfMonth(now.lengthOfMonth());
        } else {
            String[] split = yearMonth.split("-");
            int y = Integer.parseInt(split[0]);
            int m = Integer.parseInt(split[1]);
            from = LocalDate.of(y, m, 1);
            to = from.withDayOfMonth(from.lengthOfMonth());
        }
        return attendanceRepository
                .findAllByWorkerIdxAndWorkDateBetweenOrderByWorkDateDesc(workerIdx, from, to)
                .stream()
                .map(WorkerDetailDto.AttendanceRes::from)
                .collect(Collectors.toList());
    }

    // MANAGEMENT_007 구역 배치 이력 조회
    public List<WorkerDetailDto.DeploymentRes> getDeployments(Long workerIdx) {
        ensureExists(workerIdx);
        return zoneHistoryRepository.findAllByWorkerIdxOrderByAssignedAtDesc(workerIdx).stream()
                .map(WorkerDetailDto.DeploymentRes::from)
                .collect(Collectors.toList());
    }

    public List<WorkerDetailDto.SanctionRes> getPenalties(Long workerIdx) {
        ensureExists(workerIdx);
        return sanctionRepository.findAllByWorkerIdxOrderByActiveDescOccurredAtDesc(workerIdx).stream()
                .map(WorkerDetailDto.SanctionRes::from)
                .collect(Collectors.toList());
    }

    private void ensureExists(Long workerIdx) {
        if (!workerRepository.existsById(workerIdx)) {
            throw new BaseException(FAIL);
        }
    }
}
