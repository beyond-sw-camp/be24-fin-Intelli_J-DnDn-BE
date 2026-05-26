package org.example.dndncore.worker.service;

import lombok.RequiredArgsConstructor;
import org.example.dndncore.common.exception.BaseException;
import org.example.dndncore.staffing.repository.StaffingLogRepository;
import org.example.dndncore.worker.model.dto.WorkerDetailDto;
import org.example.dndncore.worker.model.entity.AttendanceRecord;
import org.example.dndncore.worker.model.entity.Worker;
import org.example.dndncore.worker.model.enums.EmploymentKind;
import org.example.dndncore.worker.repository.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import org.example.dndncore.staffing.model.StaffingLog;

import java.time.LocalDate;
import java.time.ZoneId;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import static org.example.dndncore.common.model.BaseResponseStatus.WORKER_NOT_FOUND;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class WorkerDetailService {
    private static final ZoneId ROSTER_ZONE = ZoneId.of("Asia/Seoul");

    private final WorkerRepository workerRepository;
    private final AttendanceRecordRepository attendanceRepository;
    private final WorkerDocumentRepository documentRepository;
    private final SafetyAccidentRepository accidentRepository;
    private final StaffingLogRepository staffingLogRepository;
    private final FatigueCalculationService fatigueCalculationService;
    private final AttendanceLogCalendarService attendanceLogCalendarService;

    // MANAGEMENT_004 작업자 상세 프로필 조회 (기본 정보 카드) — 열람 시 피로도 재산정·저장 후 응답
    @Transactional(readOnly = false)
    public WorkerDetailDto.ProfileRes getProfile(Long workerIdx) {
        LocalDate rosterToday = LocalDate.now(ROSTER_ZONE);
        WorkerDetailDto.FatigueSummaryRes fatigue =
                fatigueCalculationService.recalculateAndPersist(workerIdx, rosterToday);
        Worker w = findWorker(workerIdx);
        EmploymentKind rosterEk = attendanceRepository
                .findByWorkerIdxAndWorkDate(workerIdx, rosterToday)
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

    // MANAGEMENT_006 최근 출결 이력 조회 (월별 캘린더) — attendance_log 기반, 당일만 record 우선.
    public List<WorkerDetailDto.AttendanceRes> getAttendance(Long workerIdx, String yearMonth) {
        Worker w = findWorker(workerIdx);
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
        return attendanceLogCalendarService.buildMonthlyCalendar(
                workerIdx, from, to, w.getEmploymentKind());
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
