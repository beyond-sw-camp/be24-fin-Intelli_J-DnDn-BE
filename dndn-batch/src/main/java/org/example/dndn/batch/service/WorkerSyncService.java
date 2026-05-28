package org.example.dndn.batch.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.example.dndn.domain.staffing.model.Trade;
import org.example.dndn.domain.worker.fixture.WorkerScenarioFixtureRow;
import org.example.dndn.domain.worker.model.entity.*;
import org.example.dndn.domain.worker.model.enums.AttendanceEventType;
import org.example.dndn.domain.worker.model.enums.AttendanceStatus;
import org.example.dndn.domain.worker.model.enums.EmploymentKind;
import org.example.dndn.domain.worker.repository.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.*;
import java.util.stream.Collectors;

import static java.util.Objects.requireNonNullElse;

@Slf4j
@Service
@RequiredArgsConstructor
public class WorkerSyncService {

    private static final int ACCIDENT_LAST_DAYS_WINDOW = 30;
    private static final int LOOKBACK_SCAN_DAYS = 120;
    private static final int SCORE_CAP = 100;
    private static final int PT_ACCIDENT = 20;
    private static final int TRADE_UNKNOWN_POINTS = 5;
    private static final int PROGRESS_INTERVAL = 50;

    private final WorkerRepository workerRepository;
    private final AttendanceRecordRepository attendanceRepository;
    private final AttendanceLogRepository attendanceLogRepository;
    private final WorkerDocumentRepository documentRepository;
    private final SafetyAccidentRepository accidentRepository;

    public record SyncResult(int created, int updated, int total) {}

    /**
     * 50명 단위 chunk 처리 — WorkerSyncItemWriter에서 청크마다 호출.
     * upsert + 벌크 동기화 + 피로도 재계산을 하나의 @Transactional로 처리한다.
     *
     * 피로도를 chunk 안에서 처리할 수 있는 이유:
     *   - streak은 근로자 간 독립적 → 같은 현장 다른 청크의 데이터가 불필요
     *   - 픽스처는 attendanceRecords=List.of()를 반환하므로 historical 로그는 건드리지 않음
     *   - ref = syncDate-1(어제) → 아직 미출근이므로 오늘은 streak 미포함(6일차 = 10점)
     *   - 실제 게이트 출근 인식 시 별도 재계산 → 오늘 포함(7일차 = 20점)으로 상향
     *   - sync 실패 시 피로도도 함께 롤백 → 불일치 상태 없음
     */
    @Transactional
    public SyncResult syncChunk(String siteCode, List<WorkerScenarioFixtureRow> rows, LocalDate syncDate) {
        if (rows.isEmpty()) return new SyncResult(0, 0, 0);

        int created = 0, updated = 0;

        // 이 청크의 externalCode IN (...) — 청크당 1회 SELECT
        Set<String> externalCodes = rows.stream()
                .map(WorkerScenarioFixtureRow::getExternalCode)
                .filter(Objects::nonNull)
                .collect(Collectors.toSet());
        Map<String, Worker> existingByCode = workerRepository.findAllByExternalCodeIn(externalCodes)
                .stream()
                .collect(Collectors.toMap(Worker::getExternalCode, w -> w, (a, b) -> a));

        List<Worker> workers = new ArrayList<>(rows.size());
        for (WorkerScenarioFixtureRow item : rows) {
            Worker existing = item.getExternalCode() != null
                    ? existingByCode.get(item.getExternalCode()) : null;
            Worker worker;
            if (existing != null) {
                existing.updateFromSync(item.toWorkerEntity());
                worker = existing;
                updated++;
            } else {
                worker = workerRepository.save(item.toWorkerEntity());
                created++;
            }
            workers.add(worker);
        }
        workerRepository.flush();

        List<Long> workerIdxes = workers.stream().map(Worker::getIdx).toList();

        bulkNormalizeRosterDay(workers, workerIdxes, syncDate);
        bulkMergeDocuments(workers, rows);
        for (int i = 0; i < workers.size(); i++) mergeAccidents(workers.get(i), rows.get(i));

        // 피로도 재계산 — ref=어제 기준으로 오늘(PENDING)은 streak 미포함
        // 실제 게이트 출근 인식 시 ref=오늘로 재계산되어 7일차(20점)로 상향된다.
        LocalDate refYesterday = syncDate.minusDays(1);
        log.info("[Writer] siteCode={} {}명 피로도 재계산 (ref={})", siteCode, workers.size(), refYesterday);
        bulkRecalculateFatigue(workers, refYesterday);

        return new SyncResult(created, updated, rows.size());
    }

    /**
     * 오늘자 attendance_record(PENDING) 생성.
     *
     * <p>DELETE는 rosterCleanupStep(단일 트랜잭션, 직렬)에서 선행 완료 → INSERT만 수행 → gap lock 없음.</p>
     *
     * <p>attendance_log는 건드리지 않는다. 피로도 streak 기준일은 syncDate-1(어제)이므로
     * 오늘 CLOCK_IN 로그 없이도 어제까지의 연속일이 정확히 계산된다.
     * 실제 게이트 인식으로 CLOCK_IN 로그가 생기면 그때 ref=오늘로 재계산된다.</p>
     */
    private void bulkNormalizeRosterDay(
            List<Worker> workers, List<Long> workerIdxes, LocalDate rosterDate) {

        // employment_kind 보존용 경량 프로젝션 조회
        Map<Long, EmploymentKind> ekByWorkerIdx = attendanceRepository
                .findEmploymentKindsByWorkerIdxes(workerIdxes, rosterDate)
                .stream()
                .collect(Collectors.toMap(
                        WorkerEmploymentKindProjection::getWorkerIdx,
                        WorkerEmploymentKindProjection::getEk,
                        (a, b) -> a));

        List<AttendanceRecord> recordsToSave = new ArrayList<>(workers.size());

        for (Worker worker : workers) {
            Long wid = worker.getIdx();
            EmploymentKind preservedEk = ekByWorkerIdx.getOrDefault(
                    wid, requireNonNullElse(worker.getEmploymentKind(), EmploymentKind.REGULAR));

            recordsToSave.add(AttendanceRecord.builder()
                    .worker(worker)
                    .workDate(rosterDate)
                    .clockIn(null).clockOut(null).manDays(null)
                    .attendanceStatus(AttendanceStatus.PENDING)
                    .employmentKind(preservedEk)
                    .siteCode(worker.getSiteCode())
                    .build());
        }
        attendanceRepository.saveAll(recordsToSave);
    }

    // 픽스처 서류 INSERT — rosterCleanupStep에서 사전 삭제 완료되므로 INSERT만 수행
    // (DELETE는 rosterCleanupStep의 deleteAllByTitleIn()으로 이관 → 병렬 gap lock 데드락 원천 차단)
    private void bulkMergeDocuments(
            List<Worker> workers, List<WorkerScenarioFixtureRow> rows) {

        List<WorkerDocument> toSave = new ArrayList<>();
        for (int i = 0; i < workers.size(); i++) {
            Worker worker = workers.get(i);
            WorkerScenarioFixtureRow row = rows.get(i);
            if (row.getDocuments() == null) continue;
            for (WorkerScenarioFixtureRow.DocumentFixtureRow r : row.getDocuments()) {
                toSave.add(WorkerDocument.builder()
                        .worker(worker)
                        .title(r.getTitle())
                        .fileUrl(r.getFileUrl())
                        .storedFileName(r.getStoredFileName())
                        .build());
            }
        }
        if (!toSave.isEmpty()) {
            documentRepository.saveAll(toSave);
        }
    }

    // skip-if-exists 누적 방식 — 중복 체크 후 신규만 저장
    private void mergeAccidents(Worker worker, WorkerScenarioFixtureRow row) {
        if (row.getAccidents() == null) return;
        for (WorkerScenarioFixtureRow.AccidentFixtureRow r : row.getAccidents()) {
            String zm = requireNonNullElse(r.getZoneMain(), "").trim();
            String zs = requireNonNullElse(r.getZoneSub(), "").trim();
            if (accidentRepository.existsByWorkerIdxAndOccurredAtAndAccidentTypeAndZoneMainAndZoneSub(
                    worker.getIdx(), r.getOccurredAt(),
                    requireNonNullElse(r.getAccidentType(), ""),
                    zm.isEmpty() ? null : zm, zs.isEmpty() ? null : zs)) continue;
            accidentRepository.save(SafetyAccident.builder()
                    .worker(worker)
                    .occurredAt(r.getOccurredAt())
                    .accidentType(r.getAccidentType())
                    .zoneMain(zm.isEmpty() ? null : zm)
                    .zoneSub(zs.isEmpty() ? null : zs)
                    .resolution(r.getResolution())
                    .build());
        }
    }

    /**
     * 전체 worker를 3쿼리로 처리: IN(사고) + IN(로그) + saveAll.
     * streak·야간근무는 {@code attendance_log}만 사용한다.
     * 배치 경로는 ref=syncDate-1(어제) — 당일 PENDING 로스터는 streak에 미포함.
     */
    private void bulkRecalculateFatigue(List<Worker> workers, LocalDate ref) {
        List<Long> workerIdxes = workers.stream().map(Worker::getIdx).toList();

        // 쿼리 1: 최근 30일 사고 이력이 있는 workerIdx SET
        LocalDate accidentFrom = ref.minusDays(ACCIDENT_LAST_DAYS_WINDOW);
        Set<Long> workerIdxesWithAccident = new HashSet<>(
                accidentRepository.findWorkerIdxesWithAccidentBetween(workerIdxes, accidentFrom, ref));

        // 쿼리 2: 최근 120일 출근 로그 전체 → workerIdx 기준 메모리 그룹화
        LocalDate attendanceFrom = ref.minusDays(LOOKBACK_SCAN_DAYS);
        Map<Long, List<AttendanceLog>> logsByWorker = attendanceLogRepository
                .findAllByWorkerIdxInAndWorkDateBetween(workerIdxes, attendanceFrom, ref)
                .stream()
                .collect(Collectors.groupingBy(AttendanceLog::getWorkerIdx));

        // 메모리 내 피로도 계산 (DB 접근 없음)
        LocalDateTime now = LocalDateTime.now();
        int fatigueTotal = workers.size();
        for (int wi = 0; wi < workers.size(); wi++) {
            Worker worker = workers.get(wi);
            Long wid = worker.getIdx();
            int ptAccident = workerIdxesWithAccident.contains(wid) ? PT_ACCIDENT : 0;

            List<AttendanceLog> logRows = logsByWorker.getOrDefault(wid, List.of());
            Set<LocalDate> workedDates = new HashSet<>();
            Map<LocalDate, LocalTime> clockInByDate = new HashMap<>();
            Map<LocalDate, LocalTime> clockOutByDate = new HashMap<>();
            for (AttendanceLog log : logRows) {
                if (log.getEventType() == AttendanceEventType.CLOCK_IN) {
                    workedDates.add(log.getWorkDate());
                    clockInByDate.merge(log.getWorkDate(), log.getRecognizedAt(),
                            (a, b) -> a.isBefore(b) ? a : b);
                } else {
                    clockOutByDate.merge(log.getWorkDate(), log.getRecognizedAt(),
                            (a, b) -> a.isAfter(b) ? a : b);
                }
            }

            int ptStreak = streakScore(consecutiveOnsiteDaysEnding(workedDates, ref));
            int ptOvernight = overnightPoints(clockInByDate, clockOutByDate, ref);
            int ptTradeRisk = Trade.fatigueRiskWeightOrDefault(Trade.classifyWorker(worker));
            int capped = Math.min(SCORE_CAP, ptAccident + ptStreak + ptOvernight + ptTradeRisk);
            worker.replaceFatigueSnapshot(capped, capped >= 80, ptAccident, ptStreak, ptOvernight, ptTradeRisk, now);

            int doneFatigue = wi + 1;
            if (doneFatigue % PROGRESS_INTERVAL == 0 || doneFatigue == fatigueTotal) {
                log.info("[Sync][가공/피로도] {}/{} 피로도 계산 완료", doneFatigue, fatigueTotal);
            }
        }

        // 쿼리 3: 전체 UPDATE를 saveAll 1회로
        workerRepository.saveAll(workers);
    }

    /**
     * ref 기준으로 역방향 연속 출근 일수.
     * ref 당일 출근 기록이 없으면 전일(ref-1)부터 카운트를 시작한다.
     * 전일도 없으면 0 반환 (streak 끊김).
     *
     * <p>배치 경로: ref=syncDate-1(어제) → 오늘(PENDING)은 미포함, 어제까지의 연속일 계산.</p>
     * <p>게이트 인식 경로: ref=오늘 → 오늘 CLOCK_IN 로그 포함, 7일차 이상 시 점수 상향.</p>
     */
    private static int consecutiveOnsiteDaysEnding(Set<LocalDate> workedDates, LocalDate ref) {
        if (workedDates.isEmpty()) return 0;
        LocalDate start = workedDates.contains(ref) ? ref : ref.minusDays(1);
        if (!workedDates.contains(start)) return 0;
        int streak = 0;
        LocalDate d = start;
        while (workedDates.contains(d)) { streak++; d = d.minusDays(1); }
        return streak;
    }

    private static int streakScore(int days) {
        if (days >= 9) return 40;
        if (days >= 8) return 30;
        if (days >= 7) return 20;
        if (days >= 6) return 10;
        return 0;
    }

    private static int overnightPoints(
            Map<LocalDate, LocalTime> clockInByDate,
            Map<LocalDate, LocalTime> clockOutByDate,
            LocalDate ref) {
        LocalTime in = clockInByDate.get(ref);
        LocalTime out = clockOutByDate.get(ref.minusDays(1));
        if (in == null || out == null) return 0;
        Duration rest = Duration.between(ref.minusDays(1).atTime(out), ref.atTime(in));
        return rest.compareTo(Duration.ofHours(10)) < 0 ? 30 : 0;
    }
}
