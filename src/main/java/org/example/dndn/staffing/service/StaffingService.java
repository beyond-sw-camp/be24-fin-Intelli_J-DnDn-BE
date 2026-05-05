package org.example.dndn.staffing.service;

import lombok.RequiredArgsConstructor;
import org.example.dndn.common.exception.BaseException;
import org.example.dndn.staffing.model.StaffingAssignment;
import org.example.dndn.staffing.model.StaffingDto;
import org.example.dndn.staffing.model.Trade;
import org.example.dndn.staffing.model.TradeNeed;
import org.example.dndn.staffing.model.ZoneMain;
import org.example.dndn.staffing.model.ZoneSub;
import org.example.dndn.staffing.repository.StaffingAssignmentRepository;
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
import org.example.dndn.worker.repository.WorkerRepository;
import org.example.dndn.worker.service.AttendanceDeploymentSyncService;
import org.example.dndn.worker.service.FatigueCalculationService;
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
import java.util.stream.Collectors;

import static org.example.dndn.common.model.BaseResponseStatus.ASSIGN_OVERFLOW;
import static org.example.dndn.common.model.BaseResponseStatus.FAIL;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class StaffingService {

    /** 명단·스냅샷 조회 시 “출근 처리됨”으로 볼 근태(지각 포함). */
    private static final List<AttendanceStatus> STAFFING_ATTENDANCE_ONSITE =
            List.of(AttendanceStatus.PRESENT, AttendanceStatus.LATE);

    private final StaffingAssignmentRepository assignmentRepository;
    private final ZoneMainRepository zoneMainRepository;
    private final ZoneSubRepository zoneSubRepository;
    private final TradeNeedRepository tradeNeedRepository;
    private final WorkerRepository workerRepository;
    private final AttendanceRecordRepository attendanceRecordRepository;
    private final AttendanceDeploymentSyncService attendanceDeploymentSyncService;
    private final FatigueCalculationService fatigueCalculationService;

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
        LocalDate date = rosterDate != null ? rosterDate : LocalDate.now();
        if (!zoneSubRepository.existsById(zoneSubIdx)) {
            throw new BaseException(FAIL);
        }
        List<StaffingAssignment> rows = assignmentRepository.findAllByZoneSubWithHierarchy(zoneSubIdx);
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

        return rows.stream()
                .map(a -> {
                    Worker worker = workerMap.get(a.getWorkerIdx());
                    return worker != null
                            ? StaffingDto.AssignedWorkerRes.from(
                                    worker, a, rosterEkByWorkerIdx.get(worker.getIdx()))
                            : null;
                })
                .filter(Objects::nonNull)
                .toList();
    }

    // STAFFING_006 DELETE — 해당 ZoneSub 에서 배치 행만 삭제(근태 구역은 최종배치 /staffing/save 시에만 반영).
    @Transactional
    public void unassignWorkerFromZoneSub(Long zoneSubIdx, Long workerIdx, LocalDate rosterDate) {
        if (!assignmentRepository.existsByZoneSub_IdxAndWorkerIdx(zoneSubIdx, workerIdx)) {
            return;
        }
        assignmentRepository.deleteByZoneSub_IdxAndWorkerIdx(zoneSubIdx, workerIdx);
        assignmentRepository.flush();
    }

    // STAFFING_008 근태 명단 필터 — PRESENT·LATE(지각)만 포함
    public StaffingDto.WorkerPoolRes getWorkerPool(StaffingDto.PoolSearchReq req, LocalDate rosterDate) {
        LocalDate date = rosterDate != null ? rosterDate : LocalDate.now();
        if (req == null) {
            req = StaffingDto.PoolSearchReq.builder().build();
        }

        List<AttendanceRecord> rosterRows =
                attendanceRecordRepository.findAllByWorkDateAndWorkerJobRank(
                        date, JobRank.WORKER, STAFFING_ATTENDANCE_ONSITE);
        if (rosterRows.isEmpty()) {
            return StaffingDto.WorkerPoolRes.builder().totalCount(0).rows(List.of()).build();
        }

        List<Long> workerIds = rosterRows.stream().map(ar -> ar.getWorker().getIdx()).toList();

        Map<Long, StaffingAssignment> firstAssignByWorker = new LinkedHashMap<>();
        if (!workerIds.isEmpty()) {
            for (StaffingAssignment a : assignmentRepository.findAllWithZonesByWorkerIdxIn(workerIds)) {
                firstAssignByWorker.putIfAbsent(a.getWorkerIdx(), a);
            }
        }

        HashSet<Long> staffedIdxes = new HashSet<>(assignmentRepository.findDistinctAssignedWorkerIdxes());

        String kw = req.getKeyword() == null ? "" : req.getKeyword().trim().toLowerCase();
        AffiliationKind affFilter = req.getAffiliationKind();
        boolean onlyUnassigned = req.isUnassignedOnly();

        List<StaffingDto.AssignedWorkerRes> rows = new ArrayList<>(rosterRows.size());
        for (AttendanceRecord ar : rosterRows) {
            Worker w = ar.getWorker();
            if (affFilter != null && w.getAffiliationKind() != affFilter) {
                continue;
            }
            if (!kw.isEmpty()) {
                String name = w.getName() == null ? "" : w.getName().toLowerCase();
                String pc = (w.getPartnerCompany() == null ? "" : w.getPartnerCompany()).toLowerCase();
                if (!name.contains(kw) && !pc.contains(kw)) {
                    continue;
                }
            }
            if (onlyUnassigned && staffedIdxes.contains(w.getIdx())) {
                continue;
            }

            StaffingAssignment a = firstAssignByWorker.get(w.getIdx());
            rows.add(StaffingDto.AssignedWorkerRes.from(w, a, ar.getEmploymentKind()));
        }

        return StaffingDto.WorkerPoolRes.builder().totalCount(rows.size()).rows(rows).build();
    }

    // STAFFING_007 — 미투입({@code JobRank.WORKER})만 상세 구역에 초안 배치. staffing_assignment 만 저장; 근태 구역은 POST /staffing/save.
    @Transactional
    public void assignWorkers(Long zoneSubIdx, StaffingDto.AssignReq req, LocalDate rosterDate) {
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
        for (Long workerIdx : unique) {
            if (workerIdx == null) {
                throw new BaseException(FAIL);
            }
            if (assignmentRepository.existsByZoneSub_IdxAndWorkerIdx(zoneSubIdx, workerIdx)) {
                continue;
            }
            if (assignmentRepository.existsByWorkerIdx(workerIdx)) {
                throw new BaseException(FAIL);
            }

            Worker worker = workerRepository.findById(workerIdx).orElseThrow(() -> new BaseException(FAIL));
            if (worker.getJobRank() != JobRank.WORKER) {
                throw new BaseException(FAIL);
            }
            toBind.add(workerIdx);
        }

        if (toBind.isEmpty()) {
            return;
        }

        int assigned = zs.getAssignments().size();
        int remaining = Math.max(0, zs.getRequired() - assigned);
        if (toBind.size() > remaining) {
            throw new BaseException(ASSIGN_OVERFLOW);
        }

        for (Long workerIdx : toBind) {
            assignmentRepository.save(StaffingAssignment.builder()
                    .zoneSub(zs)
                    .workerIdx(workerIdx)
                    .confirmed(false)
                    .build());
        }
        assignmentRepository.flush();
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

    /**
     * 배치 확정(최종배치): 현재 {@code staffing_assignment} 의 구역을 해당 일 근태 행의
     * {@code zone_main} / {@code zone_sub} 에 반영하고, 배치 행을 {@code confirmed = true} 로 둔다.
     */
    @Transactional
    public StaffingDto.SaveSummaryRes finalizePlacementsToAttendance(LocalDate rosterDate) {
        LocalDate date = rosterDate != null ? rosterDate : LocalDate.now();
        List<StaffingAssignment> all = assignmentRepository.findAllWithZoneHierarchyOrderByIdxAsc();
        if (all.isEmpty()) {
            return StaffingDto.SaveSummaryRes.builder().assignedCount(0).unassignedCount(0).build();
        }
        for (StaffingAssignment a : all) {
            ZoneSub zs = a.getZoneSub();
            ZoneMain zm = zs.getZoneMain();
            attendanceDeploymentSyncService.applyZonePlacementIfPresent(
                    a.getWorkerIdx(), date, zm.getTitle(), zs.getTitle());
            a.markConfirmed(true);
        }
        assignmentRepository.saveAll(all);
        assignmentRepository.flush();
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
    public StaffingDto.SaveSummaryRes autoRecommend(LocalDate rosterDate) {
        LocalDate date = rosterDate != null ? rosterDate : LocalDate.now();

        List<AttendanceRecord> rosterRows =
                attendanceRecordRepository.findAllByWorkDateAndWorkerJobRank(
                        date, JobRank.WORKER, STAFFING_ATTENDANCE_ONSITE);

        List<Long> directUnassignedIds = new ArrayList<>();
        for (AttendanceRecord ar : rosterRows) {
            Worker w = ar.getWorker();
            if (w.getAffiliationKind() != AffiliationKind.DIRECT) {
                continue;
            }
            if (assignmentRepository.existsByWorkerIdx(w.getIdx())) {
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

        List<ZoneSub> zoneSubs = new ArrayList<>(zoneSubRepository.findAllOrderedWithStaffingGraph());
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
            int remainingSlots = slotCapacityRemaining(zs);
            if (remainingSlots <= 0) {
                continue;
            }
            while (remainingSlots > 0 && !queue.isEmpty()) {
                Worker w = queue.pollFirst();
                assignmentRepository.save(StaffingAssignment.builder()
                        .zoneSub(zs)
                        .workerIdx(w.getIdx())
                        .confirmed(false)
                        .build());
                assignedNow++;
                remainingSlots--;
            }
        }
        assignmentRepository.flush();

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

    private static int slotCapacityRemaining(ZoneSub zs) {
        int assignedCount = zs.getAssignments() != null ? zs.getAssignments().size() : 0;
        int cap = zs.getRequired();
        return Math.max(0, cap - assignedCount);
    }
}
