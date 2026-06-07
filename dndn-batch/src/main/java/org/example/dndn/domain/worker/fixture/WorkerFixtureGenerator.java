package org.example.dndn.domain.worker.fixture;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.example.dndn.domain.project.model.entity.MasterSchedule;
import org.example.dndn.domain.project.model.entity.Project;
import org.example.dndn.domain.project.model.entity.TradeProcess;
import org.example.dndn.domain.project.repository.MasterScheduleRepository;
import org.example.dndn.domain.project.repository.ProjectRepository;
import org.example.dndn.domain.project.repository.TradeProcessRepository;
import org.example.dndn.domain.worker.model.enums.AffiliationKind;
import org.example.dndn.domain.worker.model.enums.EmploymentKind;
import org.example.dndn.domain.worker.model.enums.JobRank;
import org.springframework.stereotype.Component;

import java.time.LocalDate;
import java.util.*;
import java.util.stream.Collectors;

@Component
@RequiredArgsConstructor
@Slf4j
public class WorkerFixtureGenerator {

    private final ProjectRepository projectRepository;
    private final MasterScheduleRepository masterScheduleRepository;
    private final TradeProcessRepository tradeProcessRepository;

    /** 픽스처가 모든 근로자에게 일괄 생성하는 서류 제목 목록.
     *  RosterCleanupTasklet이 이 목록으로 worker_document를 사전 일괄 삭제하므로
     *  syncChunk(병렬)에서는 INSERT만 수행한다. */
    public static final List<String> FIXTURE_DOCUMENT_TITLES =
            List.of("기초안전보건교육 이수증", "신분증 사본");

    private static final List<String> LAST_NAMES = List.of(
            "김", "이", "박", "최", "정", "강", "조", "윤", "장", "임",
            "한", "오", "서", "신", "권", "황", "안", "송", "류", "홍",
            "문", "양", "배", "백", "허", "남", "심", "노", "하", "곽",
            "성", "차", "주", "우", "구", "나", "전", "민", "진", "지"
    );
    private static final List<String> FIRST_NAMES = List.of(
            "민준", "서준", "도윤", "예준", "시우", "주원", "하준", "지호", "준서", "준혁",
            "은우", "도현", "현우", "지훈", "건우", "우진", "민재", "현준", "선우", "서진",
            "민서", "하은", "지우", "서현", "지민", "수아", "하윤", "소율", "지유", "채원",
            "윤서", "다은", "예은", "수빈", "지안", "서윤", "하린", "유진", "나연", "가은",
            "태윤", "승우", "재윤", "시윤", "지완", "동현", "성민", "재현", "승현", "태현",
            "유나", "소연", "예린", "수현", "지원", "서영", "민지", "예지", "하영", "유림"
    );
    private static final List<String> BLOOD_TYPES = List.of("A", "B", "AB", "O");
    private static final List<String> RELATIONS = List.of("배우자", "부", "모", "형제", "자녀");
    private static final Set<String> EXCLUDED_TRADES = Set.of("준공", "착공", "마일스톤");
    /** SiteAuthDummy 와 동일한 기본 공종 — 신규 현장(공정표 미업로드) 픽스처 생성용 */
    private static final List<String> DEFAULT_TRADE_NAMES = List.of("토목", "골조", "전기", "설비", "마감");
    private static final int DIRECT_WORKER_COUNT = 20;
    private static final int WORKERS_PER_TRADE = 40;

    public List<WorkerScenarioFixtureRow> generate(String siteCode) {
        Optional<Project> projectOpt = projectRepository.findFirstByNameContaining("[" + siteCode.trim() + "]");
        if (projectOpt.isEmpty()) {
            log.warn("[FixtureGenerator] siteCode={} — project 테이블에 [코드] 형식 현장 없음", siteCode);
            return List.of();
        }

        Project project = projectOpt.get();
        String siteName = extractSiteName(project.getName(), siteCode);
        List<String> tradeNames = resolveTradeNames(project.getIdx());
        if (tradeNames.isEmpty()) {
            log.info("[FixtureGenerator] siteCode={} — master_schedule/trade_process 없음, 기본 공종 {}개로 픽스처 생성",
                    siteCode, DEFAULT_TRADE_NAMES.size());
            tradeNames = DEFAULT_TRADE_NAMES;
        }

        Set<String> usedNames = new HashSet<>();
        int nameSequence = 0;

        List<WorkerScenarioFixtureRow> rows = new ArrayList<>();
        rows.add(buildRow(siteCode, siteName, 0, 0, JobRank.FIELD_SUPERVISOR, AffiliationKind.DIRECT, null, usedNames, nameSequence++));
        for (int p = 1; p <= DIRECT_WORKER_COUNT; p++) {
            rows.add(buildRow(siteCode, siteName, 0, p, JobRank.WORKER, AffiliationKind.DIRECT, null, usedNames, nameSequence++));
        }

        int tradeIdx = 1;
        for (String tradeName : tradeNames) {
            rows.add(buildRow(siteCode, siteName, tradeIdx, 0, JobRank.SECTION_LEADER, AffiliationKind.PARTNER, tradeName, usedNames, nameSequence++));
            for (int p = 1; p <= WORKERS_PER_TRADE; p++) {
                rows.add(buildRow(siteCode, siteName, tradeIdx, p, JobRank.WORKER, AffiliationKind.PARTNER, tradeName, usedNames, nameSequence++));
            }
            tradeIdx++;
        }
        return rows;
    }

    private List<String> resolveTradeNames(Long projectIdx) {
        List<Long> scheduleIds = masterScheduleRepository.findAllByProject_Idx(projectIdx)
                .stream()
                .map(MasterSchedule::getIdx)
                .collect(Collectors.toList());
        if (scheduleIds.isEmpty()) {
            return List.of();
        }
        return tradeProcessRepository.findAllByMasterSchedule_IdxIn(scheduleIds)
                .stream()
                .map(TradeProcess::getTradeName)
                .filter(name -> !EXCLUDED_TRADES.contains(name))
                .distinct()
                .sorted()
                .collect(Collectors.toList());
    }

    private WorkerScenarioFixtureRow buildRow(
            String siteCode, String siteName,
            int tradeIdx, int personIdx,
            JobRank jobRank, AffiliationKind affiliationKind, String trade,
            Set<String> usedNames, int nameSequence) {

        String externalCode = String.format("%s-T%02dP%02d", siteCode, tradeIdx, personIdx);
        int seed = (Objects.hash(siteCode, tradeIdx, personIdx)) & 0x7FFFFFFF;
        String resolvedTrade = affiliationKind == AffiliationKind.DIRECT ? "직영" : trade;

        return WorkerScenarioFixtureRow.builder()
                .externalCode(externalCode)
                .name(pickUniqueName(nameSequence, usedNames))
                // ★ 전화번호는 globally unique 인 externalCode 를 seed 로 사용 → 현장 통합 유일성 보장
                .phone(formatPhone(externalCode))
                .emergencyPhone(formatEmergencyPhone(externalCode))
                .emergencyRelation(RELATIONS.get(seed % RELATIONS.size()))
                .jobRank(jobRank)
                .affiliationKind(affiliationKind)
                .trade(resolvedTrade)
                .site(siteName)
                .siteCode(siteCode)
                .bloodType(BLOOD_TYPES.get(seed % BLOOD_TYPES.size()))
                .profileImageUrl(null)
                .registeredAt(LocalDate.of(2025, 1 + (seed % 12), 1 + (seed % 28)))
                .employmentKind(EmploymentKind.REGULAR)
                .documents(List.of(
                        WorkerScenarioFixtureRow.DocumentFixtureRow.builder()
                                .title("기초안전보건교육 이수증")
                                .fileUrl("https://www.w3.org/WAI/ER/tests/xhtml/testfiles/resources/pdf/dummy.pdf")
                                .storedFileName(externalCode + "_기초안전보건교육.pdf")
                                .build(),
                        WorkerScenarioFixtureRow.DocumentFixtureRow.builder()
                                .title("신분증 사본")
                                .fileUrl("https://www.w3.org/WAI/ER/tests/xhtml/testfiles/resources/pdf/dummy.pdf")
                                .storedFileName(externalCode + "_신분증.pdf")
                                .build()
                ))
                .accidents(List.of())
                .attendanceRecords(List.of())
                .build();
    }

    /**
     * 현장 당일 시딩 인원 간 이름 중복을 방지한다.
     * 성(40) × 이름(60) = 2,400개 조합 풀에서 순번대로 1:1 할당한다.
     */
    private String pickUniqueName(int sequence, Set<String> usedNames) {
        int poolSize = LAST_NAMES.size() * FIRST_NAMES.size();
        if (sequence >= poolSize) {
            throw new IllegalStateException(
                    "이름 조합 풀이 부족합니다. sequence=" + sequence + ", poolSize=" + poolSize);
        }
        String last = LAST_NAMES.get(sequence / FIRST_NAMES.size());
        String first = FIRST_NAMES.get(sequence % FIRST_NAMES.size());
        String name = last + first;
        usedNames.add(name);
        return name;
    }

    /**
     * 본인 전화번호 — globally unique 한 externalCode 를 다항 해시로 변환한다.
     *
     * <p>externalCode 는 "{siteCode}-T{tradeIdx}P{personIdx}" 형식으로 전체 현장에 걸쳐
     * 중복이 없으므로, 이를 seed 로 삼으면 현장 통합 유일성이 보장된다.
     * 충돌 공간(9000 × 9000 = 8,100만)이 실제 근무자 수 대비 충분히 커서
     * 생일 역설 확률이 1,000명 기준 약 0.006 % 수준이다.</p>
     */
    private String formatPhone(String externalCode) {
        long h = polyHash(externalCode, 131L);
        int mid  = (int)(1000 + h % 9000);
        int tail = (int)(1000 + (h / 9000) % 9000);
        return String.format("010-%04d-%04d", mid, tail);
    }

    /**
     * 비상 연락처 — 본인 전화번호와 다른 다항식(소수 137)을 사용해 충돌을 방지한다.
     */
    private String formatEmergencyPhone(String externalCode) {
        long h = polyHash(externalCode, 137L);
        int mid  = (int)(1000 + h % 9000);
        int tail = (int)(1000 + (h / 9000) % 9000);
        return String.format("010-%04d-%04d", mid, tail);
    }

    /** 다항 해시 — 소수 multiplier 로 문자열을 long 범위에 고르게 분산시킨다. */
    private long polyHash(String s, long multiplier) {
        long h = 0L;
        for (char c : s.toCharArray()) {
            h = h * multiplier + c;
        }
        return Math.abs(h);
    }

    private String extractSiteName(String projectName, String siteCode) {
        String prefix = "[" + siteCode + "]";
        return projectName.startsWith(prefix) ? projectName.substring(prefix.length()).trim() : projectName;
    }
}
