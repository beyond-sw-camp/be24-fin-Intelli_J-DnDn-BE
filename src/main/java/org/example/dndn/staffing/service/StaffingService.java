package org.example.dndn.staffing.service;

import lombok.RequiredArgsConstructor;
import org.example.dndn.auth.model.entity.SystemUser;
import org.example.dndn.auth.security.AuthAccessService;
import org.example.dndn.common.exception.BaseException;
import org.example.dndn.project.model.entity.MasterSchedule;
import org.example.dndn.project.model.entity.Project;
import org.example.dndn.staffing.model.StaffingAssignment;
import org.example.dndn.staffing.model.StaffingDto;
import org.example.dndn.staffing.model.StaffingLog;
import org.example.dndn.staffing.model.Trade;
import org.example.dndn.staffing.model.TradeNeed;
import org.example.dndn.staffing.model.ZoneMain;
import org.example.dndn.staffing.model.ZoneSub;
import org.example.dndn.staffing.repository.StaffingAssignmentRepository;
import org.example.dndn.staffing.repository.StaffingLogRepository;
import org.example.dndn.staffing.repository.TradeNeedRepository;
import org.example.dndn.staffing.repository.ZoneMainRepository;
import org.example.dndn.staffing.repository.ZoneSubRepository;
import org.example.dndn.worker.model.entity.AttendanceRecord;
import org.example.dndn.worker.model.entity.Worker;
import org.example.dndn.worker.model.enums.AffiliationKind;
import org.example.dndn.worker.model.enums.AttendanceStatus;
import org.example.dndn.worker.model.enums.EmploymentKind;
import org.example.dndn.worker.model.enums.JobRank;
import org.example.dndn.worker.repository.AttendanceRecordRepository;
import org.example.dndn.worker.repository.WorkerDocumentRepository;
import org.example.dndn.worker.repository.WorkerRepository;
import org.example.dndn.worker.service.FatigueCalculationService;
import org.example.dndn.workplan.WorkPlanRepository;
import org.example.dndn.workplan.model.entity.WorkPlan;
import org.example.dndn.workplan.model.entity.WorkPlanWorker;
import org.example.dndn.workplan.model.enums.PlanType;
import org.example.dndn.workplan.model.enums.WorkerTrade;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static org.example.dndn.common.model.BaseResponseStatus.ASSIGN_OVERFLOW;
import static org.example.dndn.common.model.BaseResponseStatus.FAIL;
import static org.example.dndn.common.model.BaseResponseStatus.STAFFING_WORKER_NOT_PERMITTED;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class StaffingService {

    /** 명단·스냅샷 조회 시 “출근 처리됨”으로 볼 근태(지각 포함). */
    private static final List<AttendanceStatus> STAFFING_ATTENDANCE_ONSITE =
            List.of(AttendanceStatus.PRESENT, AttendanceStatus.LATE);
    private static final Pattern TIME_TOKEN = Pattern.compile("\\b\\d{1,2}:\\d{2}\\b");
    private static final String SAFETY_EDUCATION_DOCUMENT_KEYWORD = "기초안전보건교육";

    private final StaffingAssignmentRepository assignmentRepository;
    private final StaffingLogRepository staffingLogRepository;
    private final ZoneMainRepository zoneMainRepository;
    private final ZoneSubRepository zoneSubRepository;
    private final TradeNeedRepository tradeNeedRepository;
    private final WorkerRepository workerRepository;
    private final WorkerDocumentRepository workerDocumentRepository;
    private final AttendanceRecordRepository attendanceRecordRepository;
    private final FatigueCalculationService fatigueCalculationService;
    private final WorkPlanRepository workPlanRepository;
    private final AuthAccessService authAccessService;

    // STAFFING_003 — 인력 배치 보드 좌측 기본 구역 트리(ZoneMain · ZoneSub 요약 및 집계)
    @Transactional
    public List<StaffingDto.ZoneMainRes> loadZoneMainTree(LocalDate rosterDate, String siteCode) {
        LocalDate date = normalizeDate(rosterDate);
        List<ZoneSub> scheduleSubZones = syncScheduleZonesFromWorkPlans(date);
        if (!scheduleSubZones.isEmpty()) {
            if (siteCode != null && !siteCode.isBlank()) {
                String fragment = "[" + siteCode.trim() + "]";
                scheduleSubZones = scheduleSubZones.stream()
                        .filter(zs -> {
                            Project p = zs.getZoneMain().getProject();
                            return p != null && p.getName() != null && p.getName().contains(fragment);
                        })
                        .toList();
            }
            return buildZoneMainResponses(scheduleSubZones, date);
        }

        List<ZoneMain> zoneMains = (siteCode != null && !siteCode.isBlank())
                ? zoneMainRepository.findAllByProject_NameContainingOrderByDisplayOrderAsc("[" + siteCode.trim() + "]")
                : zoneMainRepository.findAllByOrderByDisplayOrderAsc();

        List<ZoneSub> subZones = zoneMains.stream()
                .flatMap(zm -> zm.getZoneSubs().stream())
                .toList();
        return buildZoneMainResponses(subZones, date);
    }

    /** STAFFING_004 — 상세 구역(ZoneSub) 단건 및 직종별 충원률 원천 데이터 */
    public StaffingDto.ZoneSubRes loadZoneSubDetail(Long zoneSubIdx, LocalDate rosterDate) {
        LocalDate date = normalizeDate(rosterDate);
        ZoneSub zs = zoneSubRepository.findWithStaffingRelationsByIdx(zoneSubIdx)
                .orElseThrow(() -> new BaseException(FAIL));
        EnumMap<Trade, Integer> filledByTrade = countAssignmentsByTrade(zs, date);
        return buildZoneSubResponse(zs, date, filledByTrade);
    }

    // STAFFING_005 — 상세 구역 이름·직종별 필요 인원 갱신. 전부 삭제 후 요청 목록으로 재등록
    @Transactional
    public void updateZoneSub(Long zoneSubIdx, StaffingDto.ZoneUpdateReq req) {
        if (req == null) {
            throw new BaseException(FAIL);
        }
        ZoneSub zs = zoneSubRepository.findById(zoneSubIdx).orElseThrow(() -> new BaseException(FAIL));
        if (req.getTitle() == null || req.getTitle().isBlank()) {
            throw new BaseException(FAIL);
        }
        zs.rename(req.getTitle().trim());

        tradeNeedRepository.deleteAllByZoneSub_Idx(zoneSubIdx);
        tradeNeedRepository.flush();

        EnumMap<Trade, Integer> mergedNeeds = mergeTradeNeedRequests(req.getTradeNeeds());

        int sum = 0;
        for (Map.Entry<Trade, Integer> entry : mergedNeeds.entrySet()) {
            if (entry.getValue() <= 0) continue;
            tradeNeedRepository.save(TradeNeed.builder()
                    .zoneSub(zs)
                    .trade(entry.getKey())
                    .need(entry.getValue())
                    .build());
            sum += entry.getValue();
        }

        int assignedNow = zs.getAssignments().size();
        zs.updateRequired(sum > 0 ? sum : Math.max(assignedNow, 1));
    }

    // STAFFING_006 GET — 해당 ZoneSub 에 배치된 작업자 목록 (명단 일자 기준 상용/일용 스냅샷 선택)
    public List<StaffingDto.AssignedWorkerRes> loadAssignedWorkersForZoneSub(
            Long zoneSubIdx, LocalDate rosterDate) {
        LocalDate date = normalizeDate(rosterDate);
        if (!zoneSubRepository.existsById(zoneSubIdx)) {
            throw new BaseException(FAIL);
        }
        List<StaffingAssignment> rows =
                assignmentRepository.findAllByZoneSubAndWorkDateWithHierarchy(zoneSubIdx, date);
        if (rows.isEmpty()) {
            return List.of();
        }
        List<Long> ids = rows.stream().map(StaffingAssignment::getWorkerIdx).distinct().toList();
        Map<Long, Worker> workerMap = workerRepository.findAllById(ids).stream()
                .filter(w -> w.getJobRank() == JobRank.WORKER)
                .collect(Collectors.toMap(Worker::getIdx, w -> w, (a, b) -> a));

        Map<Long, EmploymentKind> rosterEkByWorkerIdx = new HashMap<>();
        if (!ids.isEmpty()) {
            for (AttendanceRecord ar :
                    attendanceRecordRepository.findAllByWorkDateAndWorkerIdxIn(
                            date, ids, STAFFING_ATTENDANCE_ONSITE)) {
                rosterEkByWorkerIdx.put(ar.getWorker().getIdx(), ar.getEmploymentKind());
            }
        }

        Set<Long> safetyEducationCompletedWorkerIds = findSafetyEducationCompletedWorkerIds(ids);

        return rows.stream()
                .map(a -> {
                    Worker worker = workerMap.get(a.getWorkerIdx());
                    return worker != null
                            ? StaffingDto.AssignedWorkerRes.from(
                                    worker,
                                    a,
                                    rosterEkByWorkerIdx.get(worker.getIdx()),
                                    safetyEducationCompletedWorkerIds.contains(worker.getIdx()))
                            : null;
                })
                .filter(Objects::nonNull)
                .toList();
    }

    // STAFFING_006 DELETE — 해당 ZoneSub 에서 배치 행만 삭제(구역 배치 확정은 /staffing/save 시 staffing_log 에 기록).
    @Transactional
    public void unassignWorkerFromZoneSub(Long zoneSubIdx, Long workerIdx, LocalDate rosterDate) {
        LocalDate date = normalizeDate(rosterDate);
        SystemUser currentUser = authAccessService.currentUser().orElse(null);
        if (currentUser != null) {
            Worker worker = workerRepository.findById(workerIdx)
                    .orElseThrow(() -> new BaseException(FAIL));
            if (!canCurrentUserStaffWorker(currentUser, worker)) {
                throw new BaseException(STAFFING_WORKER_NOT_PERMITTED);
            }
        }
        assignmentRepository.deleteByZoneSub_IdxAndWorkerIdxAndWorkDate(zoneSubIdx, workerIdx, date);
    }

    // STAFFING_008 근태 명단 필터 — PRESENT·LATE(지각)만 포함, 현장 분리
    public StaffingDto.WorkerPoolRes getWorkerPool(StaffingDto.PoolSearchReq req, LocalDate rosterDate) {
        LocalDate date = normalizeDate(rosterDate);
        if (req == null) {
            req = StaffingDto.PoolSearchReq.builder().build();
        }
        String siteCode = req.getSiteCode();

        List<AttendanceRecord> rosterRows = (siteCode != null && !siteCode.isBlank())
                ? attendanceRecordRepository.findAllByWorkDateAndWorkerJobRankAndSiteCode(
                        date, JobRank.WORKER, siteCode.trim(), STAFFING_ATTENDANCE_ONSITE)
                : attendanceRecordRepository.findAllByWorkDateAndWorkerJobRank(
                        date, JobRank.WORKER, STAFFING_ATTENDANCE_ONSITE);
        if (rosterRows.isEmpty()) {
            return StaffingDto.WorkerPoolRes.builder().totalCount(0).rows(List.of()).build();
        }

        List<Long> workerIds = rosterRows.stream().map(ar -> ar.getWorker().getIdx()).toList();

        Set<Long> safetyEducationCompletedWorkerIds = findSafetyEducationCompletedWorkerIds(workerIds);

        Map<Long, StaffingAssignment> firstAssignByWorker = new LinkedHashMap<>();
        if (!workerIds.isEmpty()) {
            for (StaffingAssignment a : assignmentRepository.findAllWithZonesByWorkerIdxInAndWorkDate(workerIds, date)) {
                firstAssignByWorker.putIfAbsent(a.getWorkerIdx(), a);
            }
        }

        HashSet<Long> staffedIdxes = new HashSet<>(firstAssignByWorker.keySet());

        String kw = req.getKeyword() == null ? "" : req.getKeyword().trim().toLowerCase();
        AffiliationKind affFilter = req.getAffiliationKind();
        boolean onlyUnassigned = req.isUnassignedOnly();
        SystemUser currentUser = authAccessService.currentUser().orElse(null);

        List<StaffingDto.AssignedWorkerRes> rows = new ArrayList<>(rosterRows.size());
        for (AttendanceRecord ar : rosterRows) {
            Worker w = ar.getWorker();
            if (!canCurrentUserStaffWorker(currentUser, w)) {
                continue;
            }
            if (affFilter != null && w.getAffiliationKind() != affFilter) {
                continue;
            }
            if (!kw.isEmpty()) {
                String name = w.getName() == null ? "" : w.getName().toLowerCase();
                if (!name.contains(kw)) {
                    continue;
                }
            }
            if (onlyUnassigned && staffedIdxes.contains(w.getIdx())) {
                continue;
            }

            StaffingAssignment a = firstAssignByWorker.get(w.getIdx());
            rows.add(StaffingDto.AssignedWorkerRes.from(
                    w,
                    a,
                    ar.getEmploymentKind(),
                    safetyEducationCompletedWorkerIds.contains(w.getIdx())));
        }

        return StaffingDto.WorkerPoolRes.builder().totalCount(rows.size()).rows(rows).build();
    }

    // STAFFING_007 — 미투입({@code JobRank.WORKER})만 상세 구역에 초안 배치. staffing_assignment 만 저장; 근태 구역은 POST /staffing/save.
    @Transactional
    public void assignWorkers(Long zoneSubIdx, StaffingDto.AssignReq req, LocalDate rosterDate) {
        LocalDate date = normalizeDate(rosterDate);
        if (req == null) {
            throw new BaseException(FAIL);
        }

        List<Long> ids = req.getWorkerIds();
        if (ids == null || ids.isEmpty()) {
            return;
        }

        ZoneSub zs = zoneSubRepository.findWithStaffingRelationsByIdx(zoneSubIdx)
                .orElseThrow(() -> new BaseException(FAIL));

        LinkedHashSet<Long> unique = new LinkedHashSet<>(ids);
        List<Long> toBind = new ArrayList<>(unique.size());
        Map<Long, String> siteCodeByWorkerIdx = new HashMap<>();
        SystemUser currentUser = authAccessService.currentUser().orElse(null);
        for (Long workerIdx : unique) {
            if (workerIdx == null) {
                throw new BaseException(FAIL);
            }
            if (assignmentRepository.existsByZoneSub_IdxAndWorkerIdxAndWorkDate(zoneSubIdx, workerIdx, date)) {
                continue;
            }
            if (assignmentRepository.existsByWorkerIdxAndWorkDate(workerIdx, date)) {
                throw new BaseException(FAIL);
            }

            Worker worker = workerRepository.findById(workerIdx).orElseThrow(() -> new BaseException(FAIL));
            if (worker.getJobRank() != JobRank.WORKER) {
                throw new BaseException(FAIL);
            }
            if (!canCurrentUserStaffWorker(currentUser, worker)) {
                throw new BaseException(STAFFING_WORKER_NOT_PERMITTED);
            }
            toBind.add(workerIdx);
            siteCodeByWorkerIdx.put(workerIdx, worker.getSiteCode() != null ? worker.getSiteCode() : "");
        }

        if (toBind.isEmpty()) {
            return;
        }

        int assigned = assignmentRepository.countByZoneSub_IdxAndWorkDate(zoneSubIdx, date);
        int remaining = Math.max(0, zs.getRequired() - assigned);
        if (toBind.size() > remaining) {
            throw new BaseException(ASSIGN_OVERFLOW);
        }

        for (Long workerIdx : toBind) {
            assignmentRepository.save(StaffingAssignment.builder()
                    .zoneSub(zs)
                    .workerIdx(workerIdx)
                    .workDate(date)
                    .siteCode(siteCodeByWorkerIdx.get(workerIdx))
                    .build());
        }
    }

    private Set<Long> findSafetyEducationCompletedWorkerIds(List<Long> workerIds) {
        if (workerIds == null || workerIds.isEmpty()) {
            return Set.of();
        }

        return workerDocumentRepository.findAllByWorkerIdxInAndTitleContaining(
                        workerIds,
                        SAFETY_EDUCATION_DOCUMENT_KEYWORD)
                .stream()
                .map(document -> document.getWorkerIdx())
                .collect(Collectors.toSet());
    }

    private static EnumMap<Trade, Integer> mergeTradeNeedRequests(List<StaffingDto.TradeNeedReq> rows) {
        EnumMap<Trade, Integer> out = new EnumMap<>(Trade.class);
        if (rows == null) {
            return out;
        }
        for (StaffingDto.TradeNeedReq row : rows) {
            if (row == null || row.getTrade() == null) {
                continue;
            }
            int n = Math.max(0, row.getNeed());
            if (n <= 0) continue;
            out.merge(row.getTrade(), n, Integer::sum);
        }
        return out;
    }

    private EnumMap<Trade, Integer> countAssignmentsByTrade(ZoneSub zs, LocalDate workDate) {
        List<StaffingAssignment> assigns =
                assignmentRepository.findAllByZoneSubAndWorkDateWithHierarchy(zs.getIdx(), workDate);
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

    // STAFFING_002 — 투입 인원 초기화 (siteCode 전달 시 해당 현장 배치만 삭제)
    @Transactional
    public void resetBoard(LocalDate rosterDate, String siteCode) {
        LocalDate date = normalizeDate(rosterDate);
        if (siteCode != null && !siteCode.isBlank()) {
            assignmentRepository.deleteAllByWorkDateAndSiteCode(date, siteCode.trim());
        } else {
            assignmentRepository.deleteAllByWorkDate(date);
        }
    }

    /**
     * staffing_log 기반 확정 배치 근무자 조회.
     * workDate + siteCode 로 필터링 후, 근무자별 가장 최근 로그만 남겨 반환한다.
     */
    public List<StaffingDto.ConfirmedWorkerRes> getConfirmedWorkers(LocalDate rosterDate, String siteCode) {
        LocalDate date = normalizeDate(rosterDate);
        List<StaffingLog> logs = (siteCode != null && !siteCode.isBlank())
                ? staffingLogRepository.findAllBySiteCodeAndWorkDateOrderByCreatedAtDesc(siteCode.trim(), date)
                : staffingLogRepository.findAllByWorkDateOrderByCreatedAtDesc(date);
        if (logs.isEmpty()) {
            return List.of();
        }

        // 근무자별 최신 로그만 유지 (logs 는 created_at DESC 정렬 보장)
        Map<Long, StaffingLog> latestByWorker = new LinkedHashMap<>();
        for (StaffingLog log : logs) {
            latestByWorker.putIfAbsent(log.getWorkerIdx(), log);
        }

        List<Long> workerIds = new ArrayList<>(latestByWorker.keySet());
        Map<Long, Worker> workerMap = workerRepository.findAllById(workerIds).stream()
                .collect(Collectors.toMap(Worker::getIdx, w -> w, (a, b) -> a));

        return workerIds.stream()
                .map(wid -> {
                    StaffingLog log = latestByWorker.get(wid);
                    Worker w = workerMap.get(wid);
                    if (w == null) return null;
                    String affiliationLabel = w.getAffiliationKind() == AffiliationKind.DIRECT ? "본사" : "협력사";
                    String sub = w.getAffiliationKind() == AffiliationKind.DIRECT ? "직영" : w.getTrade();
                    String affiliationLine = affiliationLabel + " / " + (sub == null ? "" : sub.trim());
                    String main = log.getZoneMainTitle() != null ? log.getZoneMainTitle() : "";
                    String sub2 = log.getZoneSubTitle() != null ? log.getZoneSubTitle() : "";
                    String placement = sub2.isBlank() ? main : (main + " · " + sub2);
                    return StaffingDto.ConfirmedWorkerRes.builder()
                            .workerIdx(wid)
                            .name(w.getName())
                            .affiliationKind(w.getAffiliationKind())
                            .affiliationLine(affiliationLine)
                            .zoneMainTitle(log.getZoneMainTitle())
                            .zoneSubTitle(log.getZoneSubTitle())
                            .placement(placement.isBlank() ? "미배치" : placement)
                            .tradeName(log.getTradeName())
                            .confirmedAt(log.getCreatedAt())
                            .build();
                })
                .filter(Objects::nonNull)
                .toList();
    }

    /**
     * 배치 확정(최종배치): 현재 {@code staffing_assignment} 의 구역 스냅샷을 {@code staffing_log} 에 기록한다.
     * 기록 후 배치 행은 유지된다(보드에서 계속 조회 가능).
     */
    @Transactional
    public StaffingDto.SaveSummaryRes finalizePlacementsToAttendance(LocalDate rosterDate, String siteCode) {
        LocalDate date = normalizeDate(rosterDate);
        List<StaffingAssignment> all = (siteCode != null && !siteCode.isBlank())
                ? assignmentRepository.findAllWithZoneHierarchyByWorkDateAndSiteCodeOrderByIdxAsc(date, siteCode.trim())
                : assignmentRepository.findAllWithZoneHierarchyByWorkDateOrderByIdxAsc(date);
        if (all.isEmpty()) {
            return StaffingDto.SaveSummaryRes.builder().assignedCount(0).unassignedCount(0).build();
        }

        List<Long> workerIds = all.stream().map(StaffingAssignment::getWorkerIdx).distinct().toList();
        Map<Long, String> siteCodeByWorkerIdx = workerRepository.findAllById(workerIds).stream()
                .collect(Collectors.toMap(Worker::getIdx, w -> w.getSiteCode() != null ? w.getSiteCode() : "", (a, b) -> a));

        for (StaffingAssignment a : all) {
            ZoneSub zs = a.getZoneSub();
            ZoneMain zm = zs.getZoneMain();
            staffingLogRepository.save(StaffingLog.builder()
                    .workerIdx(a.getWorkerIdx())
                    .workDate(date)
                    .zoneSubIdx(zs.getIdx())
                    .zoneMainTitle(zm.getTitle())
                    .zoneSubTitle(zs.getTitle())
                    .tradeName(zs.getTradeName())
                    .siteCode(siteCodeByWorkerIdx.getOrDefault(a.getWorkerIdx(), ""))
                    .build());
        }
        return StaffingDto.SaveSummaryRes.builder()
                .assignedCount(all.size())
                .unassignedCount(0)
                .build();
    }

    /**
     * STAFFING_001 인력 자동 추천 배치.
     * <ul>
     *   <li>대상: 당일 명단(PRESENT/LATE)·{@link JobRank#WORKER}·{@link AffiliationKind#DIRECT}(본사) 이면서 아직
     *       {@code staffing_assignment} 가 없는 인원만(협력사 전문직은 외부 작업지시 범위로 제외).</li>
     *   <li>배치 전 피로도를 해당 일 기준 재산정·저장한 뒤, 고위험·고득점 작업자를 앞쪽에 두어
     *       구역별 공종 위험도 상한이 낮은 상세구역부터 순차로 채운다.</li>
     * </ul>
     */
    @Transactional
    public StaffingDto.SaveSummaryRes autoRecommend(LocalDate rosterDate, String siteCode) {
        LocalDate date = normalizeDate(rosterDate);

        List<AttendanceRecord> rosterRows = (siteCode != null && !siteCode.isBlank())
                ? attendanceRecordRepository.findAllByWorkDateAndWorkerJobRankAndSiteCode(
                        date, JobRank.WORKER, siteCode.trim(), STAFFING_ATTENDANCE_ONSITE)
                : attendanceRecordRepository.findAllByWorkDateAndWorkerJobRank(
                        date, JobRank.WORKER, STAFFING_ATTENDANCE_ONSITE);

        List<Long> directUnassignedIds = new ArrayList<>();
        for (AttendanceRecord ar : rosterRows) {
            Worker w = ar.getWorker();
            if (w.getAffiliationKind() != AffiliationKind.DIRECT) {
                continue;
            }
            if (assignmentRepository.existsByWorkerIdxAndWorkDate(w.getIdx(), date)) {
                continue;
            }
            directUnassignedIds.add(w.getIdx());
        }

        if (directUnassignedIds.isEmpty()) {
            return StaffingDto.SaveSummaryRes.builder().assignedCount(0).unassignedCount(0).build();
        }

        for (Long wid : directUnassignedIds) {
            fatigueCalculationService.recalculateAndPersist(wid, date);
        }

        Map<Long, Worker> workers =
                workerRepository.findAllById(directUnassignedIds).stream()
                        .filter(w -> w.getJobRank() == JobRank.WORKER && w.getAffiliationKind() == AffiliationKind.DIRECT)
                        .collect(Collectors.toMap(Worker::getIdx, w -> w, (a, b) -> a));

        List<Worker> ordered = directUnassignedIds.stream()
                .map(workers::get)
                .filter(Objects::nonNull)
                .sorted(autoRecommendWorkerOrdering())
                .toList();

        ArrayDeque<Worker> queue = new ArrayDeque<>(ordered);

        List<ZoneSub> synced = syncScheduleZonesFromWorkPlans(date);
        List<ZoneSub> zoneSubs = new ArrayList<>(
                synced.isEmpty()
                        ? zoneSubRepository.findAllOrderedWithStaffingGraph()
                        : synced);
        zoneSubs.sort(
                Comparator.<ZoneSub>comparingInt(StaffingService::zoneCeilingTradeRiskScore)
                        .thenComparingInt(zs -> zs.getZoneMain().getDisplayOrder())
                        .thenComparingInt(ZoneSub::getDisplayOrder)
                        .thenComparingLong(ZoneSub::getIdx));

        int assignedNow = 0;
        for (ZoneSub zs : zoneSubs) {
            if (queue.isEmpty()) {
                break;
            }
            int remainingSlots = slotCapacityRemaining(zs, date);
            if (remainingSlots <= 0) {
                continue;
            }
            while (remainingSlots > 0 && !queue.isEmpty()) {
                Worker w = queue.pollFirst();
                assignmentRepository.save(StaffingAssignment.builder()
                        .zoneSub(zs)
                        .workerIdx(w.getIdx())
                        .workDate(date)
                        .siteCode(w.getSiteCode())
                        .build());
                assignedNow++;
                remainingSlots--;
            }
        }
        return StaffingDto.SaveSummaryRes.builder()
                .assignedCount(assignedNow)
                .unassignedCount(queue.size())
                .build();
    }

    private static Comparator<Worker> autoRecommendWorkerOrdering() {
        Comparator<Worker> byFatigue = Comparator.<Worker>comparingInt(w -> w.isFatigueHighRisk() ? 1 : 0)
                .thenComparingInt(Worker::getFatigueScoreTotal)
                .thenComparingLong(Worker::getIdx);
        return byFatigue.reversed();
    }

    /** 상세구역 {@code trade_need} 중 필요(need{@literal >}0)한 공종의 위험도 상한(max). 필요행이 없으면 미분류(10). */
    private static int zoneCeilingTradeRiskScore(ZoneSub zs) {
        if (zs.getTradeNeeds() == null || zs.getTradeNeeds().isEmpty()) {
            return Trade.fatigueRiskWeightOrDefault(null);
        }
        int max =
                zs.getTradeNeeds().stream()
                        .filter(tn -> tn.getNeed() > 0 && tn.getTrade() != null)
                        .mapToInt(tn -> tn.getTrade().fatigueRiskWeight())
                        .max()
                        .orElse(Trade.fatigueRiskWeightOrDefault(null));
        return max > 0 ? max : Trade.fatigueRiskWeightOrDefault(null);
    }

    private int slotCapacityRemaining(ZoneSub zs, LocalDate workDate) {
        int assignedCount = assignmentRepository.countByZoneSub_IdxAndWorkDate(zs.getIdx(), workDate);
        int cap = zs.getRequired();
        return Math.max(0, cap - assignedCount);
    }

    private LocalDate normalizeDate(LocalDate rosterDate) {
        return rosterDate != null ? rosterDate : LocalDate.now();
    }

    private List<ZoneSub> syncScheduleZonesFromWorkPlans(LocalDate date) {
        List<WorkPlan> weeklyPlans = workPlanRepository.findAllByPlanTypeWithStaffingGraph(PlanType.WEEKLY).stream()
                .filter(authAccessService::canAccessWorkPlan)
                .filter(plan -> isActiveOnDate(plan, date))
                .sorted(Comparator
                        .comparing((WorkPlan plan) -> safeDate(plan.getStartDate()))
                        .thenComparing(plan -> nullToEmpty(plan.getName()))
                        .thenComparing(WorkPlan::getIdx))
                .toList();

        if (weeklyPlans.isEmpty()) {
            return List.of();
        }

        Set<String> allGroupKeys = weeklyPlans.stream().map(this::scheduleGroupKey).collect(Collectors.toSet());
        Map<String, ZoneMain> existingMains = zoneMainRepository.findAllBySourceKeyIn(allGroupKeys)
                .stream().collect(Collectors.toMap(ZoneMain::getSourceKey, zm -> zm, (a, b) -> a));

        Set<Long> allPlanIdxes = weeklyPlans.stream().map(WorkPlan::getIdx).collect(Collectors.toSet());
        Map<Long, ZoneSub> existingSubsByPlanIdx = zoneSubRepository.findAllByWorkPlanIdxIn(allPlanIdxes)
                .stream().collect(Collectors.toMap(ZoneSub::getWorkPlanIdx, zs -> zs, (a, b) -> a));

        Map<String, ZoneMain> groupByKey = new LinkedHashMap<>();
        int groupOrder = 0;
        int subOrder = 0;
        for (WorkPlan plan : weeklyPlans) {
            String groupKey = scheduleGroupKey(plan);
            ZoneMain group = groupByKey.get(groupKey);
            if (group == null) {
                group = existingMains.getOrDefault(groupKey, null);
                if (group == null) {
                    group = ZoneMain.builder()
                            .title(scheduleGroupTitle(plan))
                            .displayOrder(groupByKey.size())
                            .scheduleGenerated(true)
                            .sourceKey(groupKey)
                            .build();
                }
                group.updateScheduleGroup(scheduleGroupTitle(plan), groupOrder++, groupKey, resolveProjectFromPlan(plan));
                group = zoneMainRepository.save(group);
                groupByKey.put(groupKey, group);
            }

            ZoneSub sub = existingSubsByPlanIdx.get(plan.getIdx());
            if (sub == null) {
                sub = ZoneSub.builder()
                        .zoneMain(group)
                        .title(nullToDefault(plan.getName(), "작업"))
                        .required(resolveRequiredCount(plan))
                        .displayOrder(subOrder)
                        .workPlanIdx(plan.getIdx())
                        .workDate(date)
                        .location(plan.getLocation())
                        .tradeName(resolveTradeName(plan))
                        .workTime(resolveWorkTime(plan.getNote()))
                        .build();
            }
            sub.updateFromWorkPlan(
                    group,
                    nullToDefault(plan.getName(), "작업"),
                    resolveRequiredCount(plan),
                    subOrder++,
                    plan.getIdx(),
                    date,
                    plan.getLocation(),
                    resolveTradeName(plan),
                    resolveWorkTime(plan.getNote()));
            sub = zoneSubRepository.save(sub);
            replaceTradeNeedsFromWorkPlan(sub, plan);
        }
        return zoneSubRepository.findAllScheduleSubZonesByWorkDate(date);
    }

    private void replaceTradeNeedsFromWorkPlan(ZoneSub sub, WorkPlan plan) {
        tradeNeedRepository.deleteAllByZoneSub_Idx(sub.getIdx());
        tradeNeedRepository.flush();

        EnumMap<Trade, Integer> needs = new EnumMap<>(Trade.class);
        if (plan.getWorkers() != null) {
            for (WorkPlanWorker worker : plan.getWorkers()) {
                Trade trade = mapWorkerTrade(worker.getTrade());
                int count = Math.max(0, worker.getCount() != null ? worker.getCount() : 0);
                if (trade != null && count > 0) {
                    needs.merge(trade, count, Integer::sum);
                }
            }
        }

        int required = resolveRequiredCount(plan);
        int needTotal = needs.values().stream().mapToInt(Integer::intValue).sum();
        if (required > 0 && needTotal == 0) {
            needs.put(Trade.TILE, required);
        }

        for (Map.Entry<Trade, Integer> entry : needs.entrySet()) {
            if (entry.getValue() <= 0) continue;
            tradeNeedRepository.save(TradeNeed.builder()
                    .zoneSub(sub)
                    .trade(entry.getKey())
                    .need(entry.getValue())
                    .build());
        }
    }

    private List<StaffingDto.ZoneMainRes> buildZoneMainResponses(List<ZoneSub> subZones, LocalDate date) {
        Set<Long> subIdxes = subZones.stream().map(ZoneSub::getIdx).collect(Collectors.toSet());
        Map<Long, Long> countBySubIdx = subIdxes.isEmpty() ? Map.of()
                : assignmentRepository.countGroupedByZoneSubIdxAndWorkDate(subIdxes, date)
                        .stream()
                        .collect(Collectors.toMap(
                                StaffingAssignmentRepository.ZoneSubCountProjection::getZoneSubIdx,
                                StaffingAssignmentRepository.ZoneSubCountProjection::getCnt));

        Map<Long, List<ZoneSub>> grouped = subZones.stream()
                .collect(Collectors.groupingBy(
                        zs -> zs.getZoneMain().getIdx(),
                        LinkedHashMap::new,
                        Collectors.toList()));

        List<StaffingDto.ZoneMainRes> result = new ArrayList<>();
        for (List<ZoneSub> rows : grouped.values()) {
            if (rows.isEmpty()) continue;
            ZoneMain main = rows.get(0).getZoneMain();
            List<StaffingDto.ZoneSubSummaryRes> summaries = rows.stream()
                    .map(zs -> buildZoneSubSummary(zs, countBySubIdx.getOrDefault(zs.getIdx(), 0L).intValue()))
                    .toList();
            int totalAssigned = summaries.stream().mapToInt(StaffingDto.ZoneSubSummaryRes::getAssignedCount).sum();
            int totalRequired = summaries.stream().mapToInt(StaffingDto.ZoneSubSummaryRes::getRequired).sum();
            result.add(StaffingDto.ZoneMainRes.builder()
                    .idx(main.getIdx())
                    .title(main.getTitle())
                    .source(main.isScheduleGenerated() ? "WORK_PLAN" : "MANUAL")
                    .totalAssigned(totalAssigned)
                    .totalRequired(totalRequired)
                    .subZones(summaries)
                    .build());
        }
        return result;
    }

    private StaffingDto.ZoneSubSummaryRes buildZoneSubSummary(ZoneSub zs, int assignedCount) {
        return StaffingDto.ZoneSubSummaryRes.builder()
                .idx(zs.getIdx())
                .workPlanId(zs.getWorkPlanIdx())
                .title(zs.getTitle())
                .location(zs.getLocation())
                .tradeName(zs.getTradeName())
                .workTime(zs.getWorkTime())
                .workDate(zs.getWorkDate())
                .required(zs.getRequired())
                .assignedCount(assignedCount)
                .build();
    }

    private StaffingDto.ZoneSubRes buildZoneSubResponse(
            ZoneSub zs,
            LocalDate date,
            EnumMap<Trade, Integer> filledByTrade) {
        return StaffingDto.ZoneSubRes.builder()
                .idx(zs.getIdx())
                .zoneMainIdx(zs.getZoneMain().getIdx())
                .workPlanId(zs.getWorkPlanIdx())
                .title(zs.getTitle())
                .location(zs.getLocation())
                .tradeName(zs.getTradeName())
                .workTime(zs.getWorkTime())
                .workDate(zs.getWorkDate())
                .required(zs.getRequired())
                .assignedCount(assignmentRepository.countByZoneSub_IdxAndWorkDate(zs.getIdx(), date))
                .tradeNeeds(zs.getTradeNeeds().stream()
                        .map(tn -> StaffingDto.TradeNeedRes.from(
                                tn, filledByTrade.getOrDefault(tn.getTrade(), 0)))
                        .toList())
                .build();
    }

    private boolean isActiveOnDate(WorkPlan plan, LocalDate date) {
        if (plan == null || plan.getStartDate() == null) {
            return false;
        }
        LocalDate end = plan.effectiveEndDate() != null ? plan.effectiveEndDate() : plan.getEndDate();
        if (end == null) {
            end = plan.getStartDate();
        }
        return !date.isBefore(plan.getStartDate()) && !date.isAfter(end);
    }

    private String scheduleGroupKey(WorkPlan plan) {
        if (plan.getParentWorkPlan() != null && plan.getParentWorkPlan().getIdx() != null) {
            return "MONTHLY_WORK_PLAN:" + plan.getParentWorkPlan().getIdx();
        }
        if (plan.getTradeProcess() != null && plan.getTradeProcess().getIdx() != null) {
            return "TRADE_PROCESS:" + plan.getTradeProcess().getIdx();
        }
        return "TRADE:" + nullToDefault(resolveTradeName(plan), "UNKNOWN");
    }

    private String scheduleGroupTitle(WorkPlan plan) {
        if (plan.getParentWorkPlan() != null && notBlank(plan.getParentWorkPlan().getName())) {
            return plan.getParentWorkPlan().getName();
        }
        if (plan.getTradeProcess() != null && notBlank(plan.getTradeProcess().getProcessName())) {
            return plan.getTradeProcess().getProcessName();
        }
        return nullToDefault(resolveTradeName(plan), "일정 미분류");
    }

    private int resolveRequiredCount(WorkPlan plan) {
        if (plan.getRequiredCount() != null && plan.getRequiredCount() > 0) {
            return plan.getRequiredCount();
        }
        if (plan.getWorkers() == null) {
            return 0;
        }
        return plan.getWorkers().stream()
                .map(WorkPlanWorker::getCount)
                .filter(Objects::nonNull)
                .mapToInt(Integer::intValue)
                .sum();
    }

    private String resolveTradeName(WorkPlan plan) {
        // 공종 카테고리(골조공사·마감공사 등)를 우선 반환 — 공정 레이블(형틀·미장 등)보다 상위 분류
        if (plan.getTrade() != null && notBlank(plan.getTrade().getCategory())) {
            return plan.getTrade().getCategory();
        }
        if (plan.getTradeProcess() != null && notBlank(plan.getTradeProcess().getTradeName())) {
            return plan.getTradeProcess().getTradeName();
        }
        return "";
    }

    private String resolveWorkTime(String note) {
        Matcher matcher = TIME_TOKEN.matcher(nullToEmpty(note));
        List<String> tokens = new ArrayList<>();
        while (matcher.find()) {
            tokens.add(matcher.group());
        }
        if (tokens.size() >= 4) {
            return normalizeTime(tokens.get(tokens.size() - 2)) + " ~ " + normalizeTime(tokens.get(tokens.size() - 1));
        }
        if (tokens.size() >= 2) {
            return normalizeTime(tokens.get(0)) + " ~ " + normalizeTime(tokens.get(1));
        }
        return "";
    }

    private static String normalizeTime(String time) {
        String[] parts = time.split(":");
        if (parts.length != 2) {
            return time;
        }
        return parts[0].length() == 1 ? "0" + time : time;
    }

    private Trade mapWorkerTrade(WorkerTrade trade) {
        if (trade == null) {
            return null;
        }
        return switch (trade) {
            case REBAR -> Trade.REBAR;
            case WELDER -> Trade.WELDER;
            case CARPENTER, FORMWORK -> Trade.CARPENTER;
            default -> Trade.TILE;
        };
    }

    private static LocalDate safeDate(LocalDate date) {
        return date != null ? date : LocalDate.MAX;
    }

    private static boolean notBlank(String text) {
        return text != null && !text.isBlank();
    }

    private static String nullToEmpty(String text) {
        return text == null ? "" : text;
    }

    private static String nullToDefault(String text, String fallback) {
        return notBlank(text) ? text : fallback;
    }

    /**
     * 현재 로그인 사용자가 해당 작업자를 인력배치할 수 있는지 검사.
     * <ul>
     *   <li>SITE_DIRECTOR / SITE_MANAGER → 본사(DIRECT) 작업자만 배치 가능</li>
     *   <li>SECTION_LEADER / SECTION_SUPERVISOR → 자신의 공종과 일치하는 작업자만 배치 가능</li>
     *   <li>ADMIN / HEADQUARTOR → 제한 없음</li>
     * </ul>
     */
    private boolean canCurrentUserStaffWorker(SystemUser user, Worker worker) {
        if (user == null) return true;
        return switch (user.getRole()) {
            case SITE_DIRECTOR, SITE_MANAGER ->
                    worker.getAffiliationKind() == AffiliationKind.DIRECT;
            case SECTION_LEADER, SECTION_SUPERVISOR ->
                    authAccessService.tradeMatches(worker.getTrade(), user.getTrade());
            default -> true;
        };
    }

    private Project resolveProjectFromPlan(WorkPlan plan) {
        if (plan.getTradeProcess() != null) {
            MasterSchedule ms = plan.getTradeProcess().getMasterSchedule();
            if (ms != null) return ms.getProject();
        }
        if (plan.getParentWorkPlan() != null && plan.getParentWorkPlan().getTradeProcess() != null) {
            MasterSchedule ms = plan.getParentWorkPlan().getTradeProcess().getMasterSchedule();
            if (ms != null) return ms.getProject();
        }
        return null;
    }
}
