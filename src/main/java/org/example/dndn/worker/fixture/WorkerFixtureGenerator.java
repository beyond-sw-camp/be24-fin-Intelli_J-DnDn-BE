package org.example.dndn.worker.fixture;

import lombok.RequiredArgsConstructor;
import org.example.dndn.project.model.entity.MasterSchedule;
import org.example.dndn.project.model.entity.Project;
import org.example.dndn.project.model.entity.TradeProcess;
import org.example.dndn.project.repository.MasterScheduleRepository;
import org.example.dndn.project.repository.ProjectRepository;
import org.example.dndn.project.repository.TradeProcessRepository;
import org.example.dndn.worker.model.enums.AffiliationKind;
import org.example.dndn.worker.model.enums.EmploymentKind;
import org.example.dndn.worker.model.enums.JobRank;
import org.springframework.stereotype.Component;

import java.time.LocalDate;
import java.util.*;
import java.util.stream.Collectors;

/**
 * 현장에 등록된 TradeProcess(공종) 기준으로 근무자 픽스처를 결정론적으로 생성한다.
 * externalCode = "{siteCode}-T{tradeIdx:02d}P{personIdx:02d}" 형식으로 고정되어
 * 같은 현장·공종 조합이면 반복 sync 시에도 동일 근무자가 upsert된다.
 */
@Component
@RequiredArgsConstructor
public class WorkerFixtureGenerator {

    private final ProjectRepository projectRepository;
    private final MasterScheduleRepository masterScheduleRepository;
    private final TradeProcessRepository tradeProcessRepository;

    private static final List<String> LAST_NAMES = List.of(
            "김", "이", "박", "최", "정", "강", "조", "윤", "장", "임",
            "한", "오", "서", "신", "권", "황", "안", "송", "류", "홍"
    );
    private static final List<String> FIRST_NAMES = List.of(
            "민준", "서준", "도윤", "예준", "시우", "주원", "하준", "지호", "준서", "준혁",
            "은우", "도현", "현우", "지훈", "건우", "우진", "민재", "현준", "선우", "서진",
            "민서", "하은", "지우", "서현", "지민", "수아", "하윤", "소율", "지유", "채원"
    );
    private static final List<String> BLOOD_TYPES = List.of("A", "B", "AB", "O");
    private static final List<String> RELATIONS = List.of("배우자", "부", "모", "형제", "자녀");

    private static final Set<String> EXCLUDED_TRADES = Set.of("준공", "착공");
    private static final int WORKERS_PER_TRADE = 3;

    public List<WorkerScenarioFixtureRow> generate(String siteCode) {
        Optional<Project> projectOpt = projectRepository.findFirstByNameContaining("[" + siteCode.trim() + "]");
        if (projectOpt.isEmpty()) return List.of();

        Project project = projectOpt.get();
        String siteName = extractSiteName(project.getName(), siteCode);

        List<Long> scheduleIds = masterScheduleRepository.findAllByProject_Idx(project.getIdx())
                .stream().map(MasterSchedule::getIdx).collect(Collectors.toList());
        if (scheduleIds.isEmpty()) return List.of();

        // tradeName → partnerCompany 매핑 (첫 번째 등장값 사용, TreeMap으로 알파벳 정렬 → 인덱스 안정성)
        Map<String, String> tradeMap = tradeProcessRepository.findAllByMasterSchedule_IdxIn(scheduleIds)
                .stream()
                .filter(tp -> !EXCLUDED_TRADES.contains(tp.getTradeName()))
                .collect(Collectors.toMap(
                        TradeProcess::getTradeName,
                        tp -> tp.getPartnerCompany() != null ? tp.getPartnerCompany() : "",
                        (first, second) -> first,
                        TreeMap::new
                ));
        if (tradeMap.isEmpty()) return List.of();

        List<WorkerScenarioFixtureRow> rows = new ArrayList<>();

        // tradeIdx=0: 현장 직영 관리자 1명
        rows.add(buildRow(siteCode, siteName, 0, 0, JobRank.FIELD_SUPERVISOR,
                AffiliationKind.DIRECT, null, "현장관리"));

        int tradeIdx = 1;
        for (Map.Entry<String, String> entry : tradeMap.entrySet()) {
            String tradeName = entry.getKey();
            String company = entry.getValue().isBlank() ? null : entry.getValue();
            AffiliationKind kind = company != null ? AffiliationKind.PARTNER : AffiliationKind.DIRECT;

            // 공종 책임자 1명
            rows.add(buildRow(siteCode, siteName, tradeIdx, 0, JobRank.SECTION_LEADER, kind, company, tradeName));
            // 작업자 N명
            for (int p = 1; p <= WORKERS_PER_TRADE; p++) {
                rows.add(buildRow(siteCode, siteName, tradeIdx, p, JobRank.WORKER, kind, company, tradeName));
            }
            tradeIdx++;
        }

        return rows;
    }

    private WorkerScenarioFixtureRow buildRow(
            String siteCode, String siteName,
            int tradeIdx, int personIdx,
            JobRank jobRank, AffiliationKind affiliationKind,
            String partnerCompany, String subLabel) {

        String externalCode = String.format("%s-T%02dP%02d", siteCode, tradeIdx, personIdx);
        int seed = (Objects.hash(siteCode, tradeIdx, personIdx)) & 0x7FFFFFFF;

        return WorkerScenarioFixtureRow.builder()
                .externalCode(externalCode)
                .name(pickName(seed))
                .phone(formatPhone(seed))
                .emergencyPhone(formatPhone(seed + 1))
                .emergencyRelation(RELATIONS.get(seed % RELATIONS.size()))
                .jobRank(jobRank)
                .affiliationKind(affiliationKind)
                .partnerCompany(partnerCompany)
                .partnerCompanyDetail(subLabel)
                .subLabel(subLabel)
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
                .sanctions(List.of())
                .accidents(List.of())
                .attendanceRecords(List.of())
                .build();
    }

    private String pickName(int seed) {
        return LAST_NAMES.get(seed % LAST_NAMES.size())
                + FIRST_NAMES.get((seed * 7 + 3) % FIRST_NAMES.size());
    }

    private String formatPhone(int seed) {
        int mid = 1000 + (seed * 37 % 9000);
        int tail = 1000 + (seed * 53 % 9000);
        return String.format("010-%04d-%04d", mid, tail);
    }

    private String extractSiteName(String projectName, String siteCode) {
        String prefix = "[" + siteCode + "]";
        return projectName.startsWith(prefix)
                ? projectName.substring(prefix.length()).trim()
                : projectName;
    }
}
