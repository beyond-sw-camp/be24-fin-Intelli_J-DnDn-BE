package org.example.dndn.worker.service;

import lombok.RequiredArgsConstructor;
import org.example.dndn.common.exception.BaseException;
import org.example.dndn.staffing.repository.StaffingLogRepository;
import org.example.dndn.worker.model.dto.WorkerDetailDto;
import org.example.dndn.worker.model.entity.AttendanceRecord;
import org.example.dndn.worker.model.entity.Worker;
import org.example.dndn.worker.model.enums.EmploymentKind;
import org.example.dndn.worker.repository.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import org.example.dndn.staffing.model.StaffingLog;

import java.time.LocalDate;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import static org.example.dndn.common.model.BaseResponseStatus.WORKER_NOT_FOUND;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class WorkerDetailService {
    private final WorkerRepository workerRepository;
    private final AttendanceRecordRepository attendanceRepository;
    private final WorkerDocumentRepository documentRepository;
    private final SafetyAccidentRepository accidentRepository;
    private final StaffingLogRepository staffingLogRepository;
    private final FatigueCalculationService fatigueCalculationService;

    // MANAGEMENT_004 작업자 상세 프로필 조회 (기본 정보 카드) — 열람 시 피로도 재산정·저장 후 응답
    @Transactional(readOnly = false)
    public WorkerDetailDto.ProfileRes getProfile(Long workerIdx) {
        WorkerDetailDto.FatigueSummaryRes fatigue =
                fatigueCalculationService.recalculateAndPersist(workerIdx, LocalDate.now());
        Worker w = findWorker(workerIdx);
        EmploymentKind rosterEk = attendanceRepository
                .findByWorkerIdxAndWorkDate(workerIdx, LocalDate.now())
                .map(AttendanceRecord::getEmploymentKind)
                .orElse(w.getEmploymentKind());
        return WorkerDetailDto.ProfileRes.from(w, rosterEk, fatigue);
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

    /**
     * MANAGEMENT_007 구역 배치 확정 이력 조회 — {@code staffing_log} 최종배치 기준 내림차순.
     * 동일 날짜·동일 구역(zoneMainTitle + zoneSubTitle)으로 여러 번 확정된 경우 가장 최근 1건만 남긴다.
     */
    public List<WorkerDetailDto.DeploymentRes> getDeployments(Long workerIdx) {
        ensureExists(workerIdx);
        List<StaffingLog> all = staffingLogRepository.findAllByWorkerIdxOrderByCreatedAtDesc(workerIdx);
        // created_at DESC 정렬이므로 putIfAbsent → 동일 키 첫 번째(최신) 유지
        Map<String, StaffingLog> deduped = new LinkedHashMap<>();
        for (StaffingLog log : all) {
            String key = (log.getWorkDate() != null ? log.getWorkDate().toString() : "")
                    + "|" + (log.getZoneMainTitle() != null ? log.getZoneMainTitle() : "")
                    + "|" + (log.getZoneSubTitle() != null ? log.getZoneSubTitle() : "");
            deduped.putIfAbsent(key, log);
        }
        return deduped.values().stream()
                .map(WorkerDetailDto.DeploymentRes::from)
                .collect(Collectors.toList());
    }

    /** MANAGEMENT_009 안전 사고 이력 조회 */
    public List<WorkerDetailDto.AccidentRes> getAccidents(Long workerIdx) {
        ensureExists(workerIdx);
        return accidentRepository.findAllByWorkerIdxOrderByOccurredAtDesc(workerIdx).stream()
                .map(WorkerDetailDto.AccidentRes::from)
                .collect(Collectors.toList());
    }

    private Worker findWorker(Long workerIdx) {
        return workerRepository.findById(workerIdx)
                .orElseThrow(() -> new BaseException(WORKER_NOT_FOUND));
    }

    private void ensureExists(Long workerIdx) {
        if (!workerRepository.existsById(workerIdx)) {
            throw new BaseException(WORKER_NOT_FOUND);
        }
    }
}
