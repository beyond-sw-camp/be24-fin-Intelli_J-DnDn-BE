package org.example.dndncore.auth;

import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.example.dndncore.auth.model.entity.SystemUser;
import org.example.dndncore.auth.model.enums.UserRole;
import org.example.dndncore.auth.repository.SystemUserRepository;
import org.example.dndncore.project.model.entity.MasterSchedule;
import org.example.dndncore.project.model.entity.Milestone;
import org.example.dndncore.project.model.entity.Project;
import org.example.dndncore.project.model.entity.TradeProcess;
import org.example.dndncore.project.model.enums.DocType;
import org.example.dndncore.project.model.enums.MilestoneStatus;
import org.example.dndncore.project.repository.MasterScheduleRepository;
import org.example.dndncore.project.repository.ProjectRepository;
import org.example.dndncore.project.repository.TradeProcessRepository;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.core.annotation.Order;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Optional;

/**
 * 4개 현장과 현장별 권한 계정/공정 더미데이터를 시드한다.
 *
 * <h3>생성 데이터</h3>
 * <ul>
 *   <li>{@code project} : 현장 4개. {@code name} 컬럼은 <b>{@code [siteCode] 표시명}</b> 형식으로 저장한다
 *       (FE 의 {@code parseProjectLabel} 가 이 대괄호 안의 값을 "현장 코드" 컬럼으로 추출하기 때문).</li>
 *   <li>{@code master_schedule} : 현장당 1개 (DocType.MASTER).</li>
 *   <li>{@code trade_process} : 현장당 5개. {@code isMilestone = true} 로 저장해
 *       {@link TradeProcessRepository#findMilestoneTradeNamesByProjectId} 의 공종 드롭다운에 노출되도록 한다.
 *       {@code tradeName} 은 SECTION_LEADER / SECTION_SUPERVISOR 계정의 {@code trade} 필드와 동일하게 맞춘다.</li>
 *   <li>{@code milestone} : trade_process 당 1개.</li>
 *   <li>{@code account} : 현장당 17개 (= 1 SITE_DIRECTOR + 1 SITE_MANAGER + 5 × (1 SECTION_LEADER + 2 SECTION_SUPERVISOR)).</li>
 * </ul>
 *
 * 모든 더미 계정의 초기 비밀번호는 {@link #DEFAULT_PASSWORD} 이며, loginId / 이름이 이미 존재하면
 * 해당 항목은 건너뛰므로 중복 실행해도 안전하다.
 */
@Slf4j
@Component
@RequiredArgsConstructor
@Order(2)
public class SiteAuthDummy implements ApplicationRunner {

    private final SystemUserRepository userRepository;
    private final ProjectRepository projectRepository;
    private final MasterScheduleRepository masterScheduleRepository;
    private final TradeProcessRepository tradeProcessRepository;
    private final PasswordEncoder passwordEncoder;

    @PersistenceContext
    private EntityManager entityManager;

    private static final String DEFAULT_PASSWORD = "Dummy1234!";
    private static final String DUMMY_FILE_URL = "dummy://master-schedule";
    private static final String DUMMY_FILE_NAME = "dummy-master-schedule.xlsx";

    private static final List<SiteSeed> SITES = List.of(
            new SiteSeed("GN-A", "강남 재건축 A공구",   "서울 강남구 역삼동 123",  LocalDate.of(2025, 3, 1),  LocalDate.of(2027, 12, 31)),
            new SiteSeed("SP-B", "송파 주상복합 신축",   "서울 송파구 잠실동 45",   LocalDate.of(2025, 6, 15), LocalDate.of(2028, 5, 31)),
            new SiteSeed("HD-C", "부산 해운대 오피스타워", "부산 해운대구 우동 88",   LocalDate.of(2024, 11, 1), LocalDate.of(2027, 8, 31)),
            new SiteSeed("SD-D", "인천 송도 데이터센터",  "인천 연수구 송도동 207",  LocalDate.of(2025, 9, 1),  LocalDate.of(2027, 6, 30))
    );

    /** 공종명(= TradeProcess.tradeName = SystemUser.trade) + loginId 에 사용할 짧은 코드. */
    private static final List<TradeSeed> TRADES = List.of(
            new TradeSeed("토목", "cv"),
            new TradeSeed("골조", "fr"),
            new TradeSeed("전기", "el"),
            new TradeSeed("설비", "mp"),
            new TradeSeed("마감", "fn")
    );

    @Override
    @Transactional
    public void run(ApplicationArguments args) {
        List<Project> existingProjects = projectRepository.findAll();
        for (SiteSeed site : SITES) {
            Project project = upsertProject(site, existingProjects);
            seedScheduleStructure(project, site);
            seedSiteAccounts(site);
        }
    }

    /* ==================== 현장(프로젝트) ==================== */

    /**
     * {@code [code] displayName} 형태로 프로젝트를 저장한다.
     * 이전 버전의 더미가 만들어둔 "괄호 없는 표시명" 프로젝트가 있으면 이름만 새 형식으로 갱신한다.
     */
    private Project upsertProject(SiteSeed site, List<Project> existingProjects) {
        String combinedName = combinedProjectName(site);
        Optional<Project> existing = existingProjects.stream()
                .filter(p -> {
                    String n = p.getName() == null ? "" : p.getName().trim();
                    return n.equals(combinedName) || n.equals(site.displayName);
                })
                .findFirst();

        if (existing.isPresent()) {
            Project p = existing.get();
            if (!combinedName.equals(p.getName())) {
                p.update(combinedName, p.getLocation(), p.getStartDate(), p.getEndDate());
                log.info("[SiteAuthDummy] 현장 이름 갱신: {} -> {}", site.displayName, combinedName);
            }
            return p;
        }

        Project saved = projectRepository.save(Project.builder()
                .name(combinedName)
                .location(site.location)
                .startDate(site.startDate)
                .endDate(site.endDate)
                .build());
        log.info("[SiteAuthDummy] 현장 생성: code={}, name={}", site.code, combinedName);
        return saved;
    }

    private static String combinedProjectName(SiteSeed site) {
        return "[" + site.code + "] " + site.displayName;
    }

    /* ==================== 공정표 / 공종 / 마일스톤 ==================== */

    /**
     * 현장 한 개에 대해 master_schedule(MASTER) 1개, trade_process 5개(isMilestone=true),
     * 그리고 trade_process 당 milestone 1개를 생성한다.
     * 이미 MASTER 공정표가 존재하면 (이전 시드 결과로 간주하여) 전부 건너뛴다.
     */
    private void seedScheduleStructure(Project project, SiteSeed site) {
        boolean hasMaster = !masterScheduleRepository
                .findAllByProject_IdxAndDocType(project.getIdx(), DocType.MASTER)
                .isEmpty();
        if (hasMaster) {
            return;
        }

        MasterSchedule schedule = masterScheduleRepository.save(MasterSchedule.builder()
                .project(project)
                .docType(DocType.MASTER)
                .fileUrl(DUMMY_FILE_URL)
                .fileName(DUMMY_FILE_NAME)
                .isPartner(false)
                .affiliationName("본사")
                .name("시스템 시드")
                .build());
        log.info("[SiteAuthDummy] master_schedule 생성: projectIdx={}", project.getIdx());

        long totalDays = Math.max(1, ChronoUnit.DAYS.between(site.startDate, site.endDate));
        int tradeCount = TRADES.size();
        float weightPerTrade = 100f / tradeCount;

        for (int i = 0; i < tradeCount; i++) {
            TradeSeed trade = TRADES.get(i);
            LocalDate segStart = site.startDate.plusDays(totalDays * i / tradeCount);
            LocalDate segEnd = site.startDate.plusDays(totalDays * (i + 1) / tradeCount - 1);
            if (segEnd.isBefore(segStart)) {
                segEnd = segStart;
            }

            TradeProcess tp = tradeProcessRepository.save(TradeProcess.builder()
                    .masterSchedule(schedule)
                    .tradeName(trade.name)
                    .processName(trade.name + " 주요 공정")
                    .partnerCompany(trade.name + " 협력사")
                    .plannedStart(segStart)
                    .plannedEnd(segEnd)
                    .weightPct(weightPerTrade)
                    .isMilestone(true)
                    .build());

            Milestone milestone = Milestone.builder()
                    .tradeProcess(tp)
                    .name(trade.name + " 마일스톤")
                    .plannedDate(segEnd)
                    .status(MilestoneStatus.PLANNED)
                    .build();
            entityManager.persist(milestone);

            log.info("[SiteAuthDummy] trade_process+milestone 생성: site={}, trade={}, mileIdx pending flush",
                    site.code, trade.name);
        }
    }

    /* ==================== 계정 ==================== */

    private void seedSiteAccounts(SiteSeed site) {
        String prefix = site.code.toLowerCase();

        seedAccountIfAbsent(
                prefix + "-dir",
                site.displayName + " 총책임자",
                UserRole.SITE_DIRECTOR,
                site.code,
                null);

        seedAccountIfAbsent(
                prefix + "-mgr",
                site.displayName + " 현장관리자",
                UserRole.SITE_MANAGER,
                site.code,
                null);

        for (TradeSeed trade : TRADES) {
            String tradePrefix = prefix + "-" + trade.code;

            seedAccountIfAbsent(
                    tradePrefix + "-ld",
                    site.displayName + " " + trade.name + " 책임자",
                    UserRole.SECTION_LEADER,
                    site.code,
                    trade.name);

            for (int i = 1; i <= 2; i++) {
                seedAccountIfAbsent(
                        tradePrefix + "-sv" + i,
                        site.displayName + " " + trade.name + " 관리자" + i,
                        UserRole.SECTION_SUPERVISOR,
                        site.code,
                        trade.name);
            }
        }
    }

    private void seedAccountIfAbsent(String loginId, String name, UserRole role,
                                     String siteCode, String trade) {
        if (userRepository.existsByLoginId(loginId)) {
            return;
        }
        userRepository.save(SystemUser.builder()
                .loginId(loginId)
                .password(passwordEncoder.encode(DEFAULT_PASSWORD))
                .name(name)
                .role(role)
                .siteCode(siteCode)
                .trade(trade)
                .active(true)
                .build());
        log.info("[SiteAuthDummy] 계정 생성: loginId={}, role={}, siteCode={}, trade={}",
                loginId, role, siteCode, trade);
    }

    /* ==================== 시드 정의 ==================== */

    private record SiteSeed(
            String code,
            String displayName,
            String location,
            LocalDate startDate,
            LocalDate endDate
    ) {}

    private record TradeSeed(String name, String code) {}
}
