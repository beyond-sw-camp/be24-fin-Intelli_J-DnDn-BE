package org.example.dndncore.loadtest;

import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.example.dndncore.project.model.entity.MasterSchedule;
import org.example.dndncore.project.model.entity.Project;
import org.example.dndncore.project.model.entity.TradeProcess;
import org.example.dndncore.project.model.enums.DocType;
import org.example.dndncore.workplan.model.entity.WorkPlan;
import org.example.dndncore.workplan.model.enums.PlanStatus;
import org.example.dndncore.workplan.model.enums.PlanType;
import org.example.dndncore.workplan.model.enums.WorkTrade;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.core.annotation.Order;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import java.sql.Date;
import java.sql.Timestamp;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * Dev/load-test seed for heavy list and date-query scenarios.
 *
 * <p>Creates 20 sites by default. Each site gets master schedule data, trade processes,
 * monthly/weekly work plans, 2,500 daily reports, and 2,500 work orders.</p>
 */
@Slf4j
@Component
@Order(50)
@RequiredArgsConstructor
@ConditionalOnProperty(prefix = "dndn.seed.load-test", name = "enabled", havingValue = "true")
public class LoadTestDataInitializer implements ApplicationRunner {

    private static final String SITE_CODE_PREFIX = "LT";
    private static final LocalDate PROJECT_START = LocalDate.of(2024, 3, 1);
    private static final LocalDate PROJECT_END = LocalDate.of(2026, 11, 30);
    private static final int MONTHLY_PLAN_MONTHS = 33;
    private static final int WEEKLY_PLANS_PER_SITE = 250;

    private static final List<TradeSeed> TRADES = List.of(
            new TradeSeed("토공", "토공 및 흙막이", WorkTrade.EARTHWORK, "대지/지하층", 8.0f),
            new TradeSeed("골조", "철근콘크리트 골조", WorkTrade.FRAME, "지상 골조", 24.0f),
            new TradeSeed("철근", "철근 배근", WorkTrade.REBAR, "기준층", 10.0f),
            new TradeSeed("형틀", "거푸집 및 동바리", WorkTrade.FORM, "기준층", 9.0f),
            new TradeSeed("방수", "지하/옥상/욕실 방수", WorkTrade.WATERPROOF, "지하/옥상/세대", 6.0f),
            new TradeSeed("전기", "전기/통신/소방전기", WorkTrade.ELECTRIC, "전층", 11.0f),
            new TradeSeed("설비", "기계설비/위생/환기", WorkTrade.FACILITY, "전층", 12.0f),
            new TradeSeed("조적", "조적/ALC 벽체", WorkTrade.MASONRY, "세대/공용부", 5.0f),
            new TradeSeed("타일", "타일/석공 마감", WorkTrade.TILE, "세대 욕실/공용부", 7.0f),
            new TradeSeed("도장", "도장 및 최종마감", WorkTrade.PAINT, "세대/공용부", 8.0f)
    );

    private static final List<String> STATUS_CODES = List.of("DRAFT", "APPROVED", "IN_PROGRESS", "COMPLETED");
    private static final List<String> WORK_TIMES = List.of("08:00~17:00", "07:30~16:30", "08:00~18:00");

    private final PlatformTransactionManager transactionManager;
    private final JdbcTemplate jdbcTemplate;

    @PersistenceContext
    private EntityManager entityManager;

    @Value("${dndn.seed.load-test.site-count:20}")
    private int siteCount;

    @Value("${dndn.seed.load-test.daily-reports-per-site:2500}")
    private int dailyReportsPerSite;

    @Value("${dndn.seed.load-test.work-orders-per-site:2500}")
    private int workOrdersPerSite;

    @Override
    public void run(ApplicationArguments args) {
        TransactionTemplate tx = new TransactionTemplate(transactionManager);

        log.info("[LoadTestDataInitializer] seed start: sites={}, dailyReports/site={}, workOrders/site={}",
                siteCount, dailyReportsPerSite, workOrdersPerSite);

        for (int siteNo = 1; siteNo <= siteCount; siteNo++) {
            int currentSiteNo = siteNo;
            tx.executeWithoutResult(status -> seedSite(currentSiteNo));
        }

        log.info("[LoadTestDataInitializer] seed completed");
    }

    private void seedSite(int siteNo) {
        SiteSeed site = siteSeed(siteNo);
        Project project = findProject(site.projectName());
        if (project == null) {
            project = Project.builder()
                    .name(site.projectName())
                    .location(site.location())
                    .startDate(PROJECT_START)
                    .endDate(PROJECT_END)
                    .build();
            entityManager.persist(project);
            entityManager.flush();
        }

        long existingReportCount = countDailyReports(project.getIdx());
        long existingWorkOrderCount = countWorkOrders(project.getIdx());
        if (existingReportCount >= dailyReportsPerSite && existingWorkOrderCount >= workOrdersPerSite) {
            log.info("[LoadTestDataInitializer] skip site: code={}, projectId={}, reports={}, workOrders={}",
                    site.code(), project.getIdx(), existingReportCount, existingWorkOrderCount);
            entityManager.clear();
            return;
        }

        MasterSchedule masterSchedule = ensureMasterSchedule(project, site);
        List<TradeProcess> tradeProcesses = ensureTradeProcesses(masterSchedule);
        List<WorkPlan> monthlyPlans = ensureMonthlyPlans(tradeProcesses, site);
        List<WorkPlan> weeklyPlans = ensureWeeklyPlans(tradeProcesses, monthlyPlans, site);

        entityManager.flush();

        if (existingReportCount < dailyReportsPerSite) {
            insertDailyReports(project, site, monthlyPlans, weeklyPlans, dailyReportsPerSite - (int) existingReportCount);
        }
        if (existingWorkOrderCount < workOrdersPerSite) {
            insertWorkOrders(project, site, weeklyPlans, workOrdersPerSite - (int) existingWorkOrderCount);
        }

        entityManager.clear();
        log.info("[LoadTestDataInitializer] seeded site: code={}, projectId={}, reports +{}, workOrders +{}",
                site.code(),
                project.getIdx(),
                Math.max(0, dailyReportsPerSite - existingReportCount),
                Math.max(0, workOrdersPerSite - existingWorkOrderCount));
    }

    private MasterSchedule ensureMasterSchedule(Project project, SiteSeed site) {
        List<MasterSchedule> existing = entityManager.createQuery("""
                        select ms
                        from MasterSchedule ms
                        where ms.project.idx = :projectId
                          and ms.docType = :docType
                        order by ms.idx
                        """, MasterSchedule.class)
                .setParameter("projectId", project.getIdx())
                .setParameter("docType", DocType.MASTER)
                .setMaxResults(1)
                .getResultList();

        if (!existing.isEmpty()) {
            return existing.get(0);
        }

        MasterSchedule masterSchedule = MasterSchedule.builder()
                .project(project)
                .docType(DocType.MASTER)
                .fileUrl("load-test://master-schedule/" + site.code())
                .fileName(site.code() + "_master_schedule.xlsx")
                .isPartner(false)
                .affiliationName("DNDN Load Test")
                .name("load-test-seed")
                .build();
        entityManager.persist(masterSchedule);
        entityManager.flush();
        return masterSchedule;
    }

    private List<TradeProcess> ensureTradeProcesses(MasterSchedule masterSchedule) {
        List<TradeProcess> existing = entityManager.createQuery("""
                        select tp
                        from TradeProcess tp
                        where tp.masterSchedule.idx = :masterScheduleId
                        order by tp.idx
                        """, TradeProcess.class)
                .setParameter("masterScheduleId", masterSchedule.getIdx())
                .getResultList();

        if (existing.size() >= TRADES.size()) {
            return existing.subList(0, TRADES.size());
        }

        Map<String, TradeProcess> existingByTrade = existing.stream()
                .collect(Collectors.toMap(TradeProcess::getTradeName, tp -> tp, (left, right) -> left));

        int totalDays = (int) ChronoUnit.DAYS.between(PROJECT_START, PROJECT_END) + 1;
        for (int i = 0; i < TRADES.size(); i++) {
            TradeSeed trade = TRADES.get(i);
            if (existingByTrade.containsKey(trade.tradeName())) {
                continue;
            }

            LocalDate start = PROJECT_START.plusDays((long) totalDays * i / TRADES.size());
            LocalDate end = PROJECT_START.plusDays((long) totalDays * (i + 1) / TRADES.size() - 1);
            TradeProcess tradeProcess = TradeProcess.builder()
                    .masterSchedule(masterSchedule)
                    .tradeName(trade.tradeName())
                    .processName(trade.processName())
                    .partnerCompany(trade.tradeName() + " 협력사")
                    .plannedStart(start)
                    .plannedEnd(end)
                    .weightPct(trade.weightPct())
                    .isMilestone(true)
                    .build();
            entityManager.persist(tradeProcess);
            existing.add(tradeProcess);
        }

        entityManager.flush();
        return existing;
    }

    private List<WorkPlan> ensureMonthlyPlans(List<TradeProcess> tradeProcesses, SiteSeed site) {
        Long projectId = tradeProcesses.get(0).getMasterSchedule().getProject().getIdx();
        List<WorkPlan> existing = entityManager.createQuery("""
                        select wp
                        from WorkPlan wp
                        join wp.tradeProcess tp
                        where tp.masterSchedule.project.idx = :projectId
                          and wp.planType = :planType
                        order by wp.idx
                        """, WorkPlan.class)
                .setParameter("projectId", projectId)
                .setParameter("planType", PlanType.MONTHLY)
                .getResultList();

        int target = MONTHLY_PLAN_MONTHS * TRADES.size();
        if (existing.size() >= target) {
            return existing.subList(0, target);
        }

        for (int monthIndex = 0; monthIndex < MONTHLY_PLAN_MONTHS; monthIndex++) {
            LocalDate monthStart = PROJECT_START.plusMonths(monthIndex);
            LocalDate monthEnd = monthStart.withDayOfMonth(monthStart.lengthOfMonth());
            for (int tradeIndex = 0; tradeIndex < TRADES.size(); tradeIndex++) {
                int planOrder = monthIndex * TRADES.size() + tradeIndex;
                if (planOrder < existing.size()) {
                    continue;
                }

                TradeSeed trade = TRADES.get(tradeIndex);
                WorkPlan monthlyPlan = WorkPlan.builder()
                        .tradeProcess(tradeProcesses.get(tradeIndex))
                        .name(String.format("%s %04d-%02d 월간공정", trade.tradeName(), monthStart.getYear(), monthStart.getMonthValue()))
                        .location(site.zoneName(tradeIndex))
                        .trade(trade.workTrade())
                        .planType(PlanType.MONTHLY)
                        .status(resolveStatus(monthStart, monthEnd))
                        .startDate(monthStart)
                        .endDate(monthEnd)
                        .requiredCount(18 + tradeIndex * 2)
                        .partner(trade.tradeName() + " 협력사")
                        .manager("월간관리자-" + site.code() + "-" + (tradeIndex + 1))
                        .contact("010-77" + String.format("%02d", tradeIndex) + "-" + site.code().replace("-", ""))
                        .note("부하테스트 월간 공정 데이터")
                        .build();
                entityManager.persist(monthlyPlan);
                existing.add(monthlyPlan);
            }
        }

        entityManager.flush();
        return existing;
    }

    private List<WorkPlan> ensureWeeklyPlans(List<TradeProcess> tradeProcesses, List<WorkPlan> monthlyPlans, SiteSeed site) {
        Long projectId = tradeProcesses.get(0).getMasterSchedule().getProject().getIdx();
        List<WorkPlan> existing = entityManager.createQuery("""
                        select wp
                        from WorkPlan wp
                        join wp.tradeProcess tp
                        where tp.masterSchedule.project.idx = :projectId
                          and wp.planType = :planType
                        order by wp.idx
                        """, WorkPlan.class)
                .setParameter("projectId", projectId)
                .setParameter("planType", PlanType.WEEKLY)
                .getResultList();

        if (existing.size() >= WEEKLY_PLANS_PER_SITE) {
            return existing.subList(0, WEEKLY_PLANS_PER_SITE);
        }

        int totalDays = (int) ChronoUnit.DAYS.between(PROJECT_START, PROJECT_END) + 1;
        for (int i = existing.size(); i < WEEKLY_PLANS_PER_SITE; i++) {
            int tradeIndex = i % TRADES.size();
            TradeSeed trade = TRADES.get(tradeIndex);
            LocalDate start = PROJECT_START.plusDays((long) i * 4 % totalDays);
            LocalDate end = start.plusDays(5);
            if (end.isAfter(PROJECT_END)) {
                end = PROJECT_END;
            }

            int monthIndex = (int) ChronoUnit.MONTHS.between(PROJECT_START.withDayOfMonth(1), start.withDayOfMonth(1));
            int monthlyIndex = Math.max(0, Math.min(monthlyPlans.size() - 1, monthIndex * TRADES.size() + tradeIndex));

            WorkPlan weeklyPlan = WorkPlan.builder()
                    .tradeProcess(tradeProcesses.get(tradeIndex))
                    .parentWorkPlan(monthlyPlans.get(monthlyIndex))
                    .name(String.format("%s 주간작업 %03d", trade.processName(), i + 1))
                    .location(site.zoneName(tradeIndex))
                    .trade(trade.workTrade())
                    .planType(PlanType.WEEKLY)
                    .status(resolveStatus(start, end))
                    .startDate(start)
                    .endDate(end)
                    .requiredCount(8 + (i % 18))
                    .partner(trade.tradeName() + " 협력사")
                    .manager("주간관리자-" + site.code() + "-" + (i % 9 + 1))
                    .contact("010-88" + String.format("%02d", i % 100) + "-" + site.code().replace("-", ""))
                    .note("부하테스트 주간 공정 데이터")
                    .build();
            entityManager.persist(weeklyPlan);
            existing.add(weeklyPlan);
        }

        entityManager.flush();
        return existing;
    }

    private void insertDailyReports(Project project, SiteSeed site, List<WorkPlan> monthlyPlans, List<WorkPlan> weeklyPlans, int count) {
        String sql = """
                insert into daily_report
                (work_plan_idx, monthly_work_plan_idx, actual_progress, today_progress, progress_increment_pct,
                 monthly_progress_pct, actual_worker_count, location, issue, report_date, today_work, tomorrow_plan,
                 created_at, updated_at)
                values (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                """;

        Timestamp now = Timestamp.valueOf(LocalDateTime.now());
        int totalDays = (int) ChronoUnit.DAYS.between(PROJECT_START, PROJECT_END) + 1;

        jdbcTemplate.batchUpdate(sql, new org.springframework.jdbc.core.BatchPreparedStatementSetter() {
            @Override
            public void setValues(java.sql.PreparedStatement ps, int i) throws java.sql.SQLException {
                WorkPlan weeklyPlan = weeklyPlans.get(i % weeklyPlans.size());
                WorkPlan monthlyPlan = monthlyPlans.get(i % monthlyPlans.size());
                TradeSeed trade = TRADES.get(i % TRADES.size());
                LocalDate reportDate = PROJECT_START.plusDays(i % totalDays);
                double baseProgress = Math.min(100.0, 2.0 + (i % dailyReportsPerSite) * 98.0 / dailyReportsPerSite);
                double todayProgress = 35.0 + (i % 66);
                double increment = 0.05 + (i % 12) * 0.03;

                ps.setLong(1, weeklyPlan.getIdx());
                ps.setLong(2, monthlyPlan.getIdx());
                ps.setDouble(3, round1(baseProgress));
                ps.setDouble(4, round1(Math.min(100.0, todayProgress)));
                ps.setDouble(5, round2(increment));
                ps.setDouble(6, round1(baseProgress));
                ps.setInt(7, 10 + (i % 45));
                ps.setString(8, weeklyPlan.getLocation());
                ps.setString(9, issueText(i));
                ps.setDate(10, Date.valueOf(reportDate));
                ps.setString(11, String.format("[%s] %s 작업 진행. 타설/검측/자재반입 상태 기록 #%04d",
                        site.code(), trade.processName(), i + 1));
                ps.setString(12, String.format("[%s] %s 후속 작업 및 안전점검 예정 #%04d",
                        site.code(), trade.tradeName(), i + 1));
                ps.setTimestamp(13, now);
                ps.setTimestamp(14, now);
            }

            @Override
            public int getBatchSize() {
                return count;
            }
        });

        log.info("[LoadTestDataInitializer] daily_report inserted: projectId={}, count={}", project.getIdx(), count);
    }

    private void insertWorkOrders(Project project, SiteSeed site, List<WorkPlan> weeklyPlans, int count) {
        String sql = """
                insert into work_order
                (site_idx, partner_company_idx, work_plan_id, trade_type, title, instruction_content, work_detail,
                 work_time, safety_content, due_date, status_code, worker_count, is_deleted, created_at, updated_at)
                values (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                """;

        Timestamp now = Timestamp.valueOf(LocalDateTime.now());
        int totalDays = (int) ChronoUnit.DAYS.between(PROJECT_START, PROJECT_END) + 1;

        jdbcTemplate.batchUpdate(sql, new org.springframework.jdbc.core.BatchPreparedStatementSetter() {
            @Override
            public void setValues(java.sql.PreparedStatement ps, int i) throws java.sql.SQLException {
                WorkPlan weeklyPlan = weeklyPlans.get(i % weeklyPlans.size());
                TradeSeed trade = TRADES.get(i % TRADES.size());
                LocalDate dueDate = PROJECT_START.plusDays((i * 2L) % totalDays);
                String title = String.format("[%s] %s 작업지시서 #%04d", site.code(), trade.tradeName(), i + 1);

                ps.setLong(1, project.getIdx());
                ps.setLong(2, site.partnerCompanyIdx(i));
                ps.setLong(3, weeklyPlan.getIdx());
                ps.setString(4, trade.tradeName());
                ps.setString(5, title);
                ps.setString(6, trade.processName() + " 구간 작업 지시 및 품질 체크");
                ps.setString(7, String.format("%s / %s / 작업순번 %04d", weeklyPlan.getLocation(), weeklyPlan.getName(), i + 1));
                ps.setString(8, WORK_TIMES.get(i % WORK_TIMES.size()));
                ps.setString(9, safetyText(i));
                ps.setDate(10, Date.valueOf(dueDate));
                ps.setString(11, STATUS_CODES.get(i % STATUS_CODES.size()));
                ps.setInt(12, 8 + (i % 38));
                ps.setBoolean(13, false);
                ps.setTimestamp(14, now);
                ps.setTimestamp(15, now);
            }

            @Override
            public int getBatchSize() {
                return count;
            }
        });

        log.info("[LoadTestDataInitializer] work_order inserted: projectId={}, count={}", project.getIdx(), count);
    }

    private Project findProject(String name) {
        List<Project> projects = entityManager.createQuery("""
                        select p
                        from Project p
                        where p.name = :name
                        """, Project.class)
                .setParameter("name", name)
                .setMaxResults(1)
                .getResultList();
        return projects.isEmpty() ? null : projects.get(0);
    }

    private long countDailyReports(Long projectId) {
        Long count = jdbcTemplate.queryForObject("""
                        select count(*)
                        from daily_report dr
                        join work_plan wp on dr.work_plan_idx = wp.idx
                        join trade_process tp on wp.trade_process_id = tp.idx
                        join master_schedule ms on tp.master_schedule_id = ms.idx
                        where ms.project_id = ?
                        """,
                Long.class,
                projectId);
        return count == null ? 0 : count;
    }

    private long countWorkOrders(Long projectId) {
        Long count = jdbcTemplate.queryForObject("""
                        select count(*)
                        from work_order
                        where site_idx = ?
                          and (is_deleted = false or is_deleted is null)
                        """,
                Long.class,
                projectId);
        return count == null ? 0 : count;
    }

    private PlanStatus resolveStatus(LocalDate start, LocalDate end) {
        LocalDate today = LocalDate.now();
        return !today.isBefore(start) && !today.isAfter(end)
                ? PlanStatus.IN_PROGRESS
                : PlanStatus.PLANNED;
    }

    private SiteSeed siteSeed(int siteNo) {
        String code = SITE_CODE_PREFIX + "-" + String.format("%02d", siteNo);
        return new SiteSeed(
                code,
                "[" + code + "] 부하테스트 현장 " + String.format("%02d", siteNo),
                "서울특별시 테스트구 " + siteNo + "번지"
        );
    }

    private String issueText(int index) {
        return switch (index % 8) {
            case 0 -> "특이사항 없음";
            case 1 -> "자재 반입 시간 조정";
            case 2 -> "우천 대비 보양 강화";
            case 3 -> "협력사 작업 간섭 조정";
            case 4 -> "검측 대기";
            case 5 -> "장비 동선 통제 필요";
            case 6 -> "안전난간 보강";
            default -> "품질 체크리스트 보완";
        };
    }

    private String safetyText(int index) {
        return switch (index % 6) {
            case 0 -> "작업 전 TBM 실시, 추락방지망 및 안전난간 확인";
            case 1 -> "중장비 유도자 배치, 장비 후진 경보 확인";
            case 2 -> "전기 공구 누전차단기 점검 및 보호구 착용";
            case 3 -> "화기 작업 허가서 확인, 소화기 배치";
            case 4 -> "개구부 덮개 및 라인마킹 확인";
            default -> "작업구역 통제선 설치 및 보행동선 분리";
        };
    }

    private double round1(double value) {
        return Math.round(value * 10.0) / 10.0;
    }

    private double round2(double value) {
        return Math.round(value * 100.0) / 100.0;
    }

    private record SiteSeed(String code, String projectName, String location) {
        private String zoneName(int tradeIndex) {
            return switch (tradeIndex % 5) {
                case 0 -> "지하층/B구간";
                case 1 -> "지상층/A구간";
                case 2 -> "기준층/C구간";
                case 3 -> "공용부/D구간";
                default -> "외부/부대토목";
            };
        }

        private long partnerCompanyIdx(int index) {
            return Math.abs((long) code.hashCode()) + (index % 20) + 1L;
        }
    }

    private record TradeSeed(
            String tradeName,
            String processName,
            WorkTrade workTrade,
            String defaultLocation,
            float weightPct
    ) {
    }
}
