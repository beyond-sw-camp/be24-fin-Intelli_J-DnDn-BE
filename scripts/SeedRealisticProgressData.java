import java.math.BigDecimal;
import java.math.RoundingMode;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.Date;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.Statement;
import java.sql.Timestamp;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

public class SeedRealisticProgressData {

    private static final String MARKER = "[DEMO_PROGRESS_20260508]";
    private static final long DEFAULT_PROJECT_ID = 5L;
    private static final LocalDate DEFAULT_CUTOFF = LocalDate.of(2026, 5, 8);
    private static final LocalDateTime NOW = LocalDateTime.now();
    private static final Timestamp NOW_TS = Timestamp.valueOf(NOW);

    public static void main(String[] args) throws Exception {
        Options options = Options.parse(args);
        Map<String, String> env = loadEnv(Path.of(".env"));

        Class.forName("org.mariadb.jdbc.Driver");

        try (Connection connection = DriverManager.getConnection(
                required(env, "DB_URL"),
                required(env, "DB_USER"),
                required(env, "DB_PASS")
        )) {
            connection.setAutoCommit(false);

            try {
                Project project = loadProject(connection, options.projectId);
                if (project == null) {
                    throw new IllegalStateException("Project not found: " + options.projectId);
                }

                long existingWorkPlans = countProjectWorkPlans(connection, options.projectId);
                long existingDemoPlans = countProjectDemoWorkPlans(connection, options.projectId);

                if (existingDemoPlans > 0 && !options.force) {
                    throw new IllegalStateException("Demo work plans already exist. Re-run with --force to replace them.");
                }

                if (existingWorkPlans > existingDemoPlans && !options.force) {
                    throw new IllegalStateException("Non-demo work plans already exist. Re-run with --force only if replacing demo data is intended.");
                }

                if (options.force && existingDemoPlans > 0) {
                    deleteDemoData(connection, options.projectId);
                }

                List<TradeProcess> processes = loadTradeProcesses(connection, options.projectId);
                if (processes.isEmpty()) {
                    throw new IllegalStateException("No extracted trade processes found for project: " + options.projectId);
                }

                Stats stats = new Stats();
                for (TradeProcess process : processes) {
                    seedProcess(connection, project, process, options.cutoff, stats);
                }

                connection.commit();
                System.out.println("Seed completed");
                System.out.println("projectId=" + options.projectId);
                System.out.println("cutoff=" + options.cutoff);
                System.out.println("tradeProcesses=" + processes.size());
                System.out.println("yearlyPlans=" + stats.yearlyPlans);
                System.out.println("monthlyPlans=" + stats.monthlyPlans);
                System.out.println("weeklyPlans=" + stats.weeklyPlans);
                System.out.println("workOrders=" + stats.workOrders);
                System.out.println("dailyReports=" + stats.dailyReports);
            } catch (Exception e) {
                connection.rollback();
                throw e;
            }
        }
    }

    private static void seedProcess(
            Connection connection,
            Project project,
            TradeProcess process,
            LocalDate cutoff,
            Stats stats
    ) throws Exception {
        if (process.plannedStart == null || process.plannedEnd == null) {
            stats.skippedProcesses++;
            return;
        }

        WorkTemplate template = WorkTemplate.from(process.tradeName, process.processName);
        String partner = nonBlank(process.partnerCompany, process.tradeName + " 협력사");
        String manager = "전성훈";
        String contact = "010-0000-0000";

        for (int year = 2024; year <= 2026; year++) {
            LocalDate yearStart = LocalDate.of(year, 1, 1);
            LocalDate yearEnd = LocalDate.of(year, 12, 31);
            LocalDate segmentStart = max(process.plannedStart, yearStart);
            LocalDate segmentEnd = min(process.plannedEnd, yearEnd);

            if (segmentStart.isAfter(segmentEnd)) {
                continue;
            }

            boolean started = !segmentStart.isAfter(cutoff);
            double progress = started
                    ? plannedPct(segmentStart, segmentEnd, min(cutoff, segmentEnd))
                    : 0.0;

            long yearlyId = insertWorkPlan(
                    connection,
                    process.id,
                    null,
                    "YEARLY",
                    process.processName + " " + year + "년 연간 실행계획",
                    template.workTrade,
                    project.location,
                    segmentStart,
                    segmentEnd,
                    started ? segmentStart : null,
                    progress,
                    started ? "IN_PROGRESS" : "PLANNED",
                    partner,
                    manager,
                    contact,
                    template.workerTotal(),
                    MARKER + " 연간 실행계획 / 기준일 " + cutoff
            );
            insertResources(connection, yearlyId, template);
            stats.yearlyPlans++;
        }

        boolean processStarted = !process.plannedStart.isAfter(cutoff);
        double monthlyProgress = processStarted
                ? plannedPct(process.plannedStart, process.plannedEnd, min(cutoff, process.plannedEnd))
                : 0.0;

        long monthlyId = insertWorkPlan(
                connection,
                process.id,
                null,
                "MONTHLY",
                process.processName + " 세부 실행계획",
                template.workTrade,
                project.location,
                process.plannedStart,
                process.plannedEnd,
                processStarted ? process.plannedStart : null,
                monthlyProgress,
                processStarted ? "IN_PROGRESS" : "PLANNED",
                partner,
                manager,
                contact,
                template.workerTotal(),
                MARKER + " 세부 실행계획 / 계획 대비 실적 동기화"
        );
        insertResources(connection, monthlyId, template);
        stats.monthlyPlans++;

        if (!processStarted) {
            return;
        }

        LocalDate reportUntil = min(process.plannedEnd, cutoff);
        LocalDate cursor = process.plannedStart;
        int weekNo = 1;
        double previousMonthlyProgress = 0.0;

        while (!cursor.isAfter(reportUntil)) {
            LocalDate weekEnd = min(cursor.plusDays(6), reportUntil);

            long weeklyId = insertWorkPlan(
                    connection,
                    process.id,
                    monthlyId,
                    "WEEKLY",
                    process.processName + " 주간 작업 " + weekNo,
                    template.workTrade,
                    project.location,
                    cursor,
                    weekEnd,
                    cursor,
                    100.0,
                    "IN_PROGRESS",
                    partner,
                    manager,
                    contact,
                    template.workerTotal(),
                    MARKER + " 주간 실행계획 / 작업시간: 08:00 ~ 17:30"
            );
            insertResources(connection, weeklyId, template);
            stats.weeklyPlans++;

            long workOrderId = insertWorkOrder(
                    connection,
                    project.id,
                    weeklyId,
                    process.tradeName,
                    process.processName + " 작업지시",
                    cursor,
                    template.workerTotal(),
                    "도면 및 시방 기준에 따라 " + process.processName + " 작업을 진행합니다.",
                    "구간별 작업 전 안전점검, 작업 중 품질 확인, 작업 종료 후 정리정돈을 완료합니다.",
                    "개인보호구 착용, 장비 유도자 배치, 작업 전 TBM 실시"
            );
            insertWorkOrderEquipments(connection, workOrderId, template);
            stats.workOrders++;

            LocalDate reportDate = cursor;
            while (!reportDate.isAfter(weekEnd)) {
                double currentProgress = plannedPct(process.plannedStart, process.plannedEnd, reportDate);
                double increment = Math.max(0.0, roundPct(currentProgress - previousMonthlyProgress));
                insertDailyReport(
                        connection,
                        weeklyId,
                        monthlyId,
                        reportDate,
                        currentProgress,
                        increment,
                        template.workerTotal(),
                        project.location,
                        "정상 진행",
                        process.processName + " 계획 물량 시공 및 품질 확인",
                        nextPlanText(process.processName, reportDate, reportUntil)
                );
                previousMonthlyProgress = currentProgress;
                stats.dailyReports++;
                reportDate = reportDate.plusDays(1);
            }

            cursor = weekEnd.plusDays(1);
            weekNo++;
        }
    }

    private static long insertWorkPlan(
            Connection connection,
            long tradeProcessId,
            Long parentWorkPlanId,
            String planType,
            String name,
            String trade,
            String location,
            LocalDate startDate,
            LocalDate endDate,
            LocalDate actualStart,
            double actualProgressPct,
            String status,
            String partner,
            String manager,
            String contact,
            int requiredCount,
            String note
    ) throws Exception {
        String sql = """
                INSERT INTO work_plan (
                    created_at, updated_at, actual_progress_pct, actual_start, contact,
                    end_date, location, manager, name, note, partner, plan_type,
                    required_count, start_date, status, trade, parent_work_plan_id, trade_process_id
                ) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                """;

        try (PreparedStatement ps = connection.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)) {
            ps.setTimestamp(1, NOW_TS);
            ps.setTimestamp(2, NOW_TS);
            ps.setBigDecimal(3, BigDecimal.valueOf(actualProgressPct).setScale(2, RoundingMode.HALF_UP));
            setDate(ps, 4, actualStart);
            ps.setString(5, contact);
            setDate(ps, 6, endDate);
            ps.setString(7, location);
            ps.setString(8, manager);
            ps.setString(9, name);
            ps.setString(10, note);
            ps.setString(11, partner);
            ps.setString(12, planType);
            ps.setInt(13, requiredCount);
            setDate(ps, 14, startDate);
            ps.setString(15, status);
            ps.setString(16, trade);
            if (parentWorkPlanId == null) {
                ps.setNull(17, java.sql.Types.BIGINT);
            } else {
                ps.setLong(17, parentWorkPlanId);
            }
            ps.setLong(18, tradeProcessId);
            ps.executeUpdate();

            try (ResultSet keys = ps.getGeneratedKeys()) {
                if (keys.next()) {
                    return keys.getLong(1);
                }
            }
        }

        throw new IllegalStateException("Failed to insert work_plan: " + name);
    }

    private static long insertWorkOrder(
            Connection connection,
            long projectId,
            long weeklyWorkPlanId,
            String tradeName,
            String title,
            LocalDate dueDate,
            int workerCount,
            String instruction,
            String workDetail,
            String safetyContent
    ) throws Exception {
        String sql = """
                INSERT INTO work_order (
                    created_at, updated_at, due_date, instruction_content, is_deleted,
                    partner_company_idx, safety_content, site_idx, status_code, title,
                    trade_type, work_detail, work_plan_id, work_time, worker_count
                ) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                """;

        try (PreparedStatement ps = connection.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)) {
            ps.setTimestamp(1, NOW_TS);
            ps.setTimestamp(2, NOW_TS);
            setDate(ps, 3, dueDate);
            ps.setString(4, instruction);
            ps.setBoolean(5, false);
            ps.setNull(6, java.sql.Types.BIGINT);
            ps.setString(7, safetyContent);
            ps.setLong(8, projectId);
            ps.setString(9, "COMPLETED");
            ps.setString(10, title);
            ps.setString(11, tradeName);
            ps.setString(12, workDetail);
            ps.setLong(13, weeklyWorkPlanId);
            ps.setString(14, "08:00 ~ 17:30");
            ps.setInt(15, workerCount);
            ps.executeUpdate();

            try (ResultSet keys = ps.getGeneratedKeys()) {
                if (keys.next()) {
                    return keys.getLong(1);
                }
            }
        }

        throw new IllegalStateException("Failed to insert work_order: " + title);
    }

    private static void insertDailyReport(
            Connection connection,
            long weeklyWorkPlanId,
            long monthlyWorkPlanId,
            LocalDate reportDate,
            double monthlyProgressPct,
            double progressIncrementPct,
            int workerCount,
            String location,
            String issue,
            String todayWork,
            String tomorrowPlan
    ) throws Exception {
        String sql = """
                INSERT INTO daily_report (
                    created_at, updated_at, actual_progress, actual_worker_count, issue,
                    location, monthly_progress_pct, progress_increment_pct, report_date,
                    today_progress, today_work, tomorrow_plan, monthly_work_plan_idx, work_plan_idx
                ) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                """;

        try (PreparedStatement ps = connection.prepareStatement(sql)) {
            ps.setTimestamp(1, NOW_TS);
            ps.setTimestamp(2, NOW_TS);
            ps.setDouble(3, monthlyProgressPct);
            ps.setInt(4, workerCount);
            ps.setString(5, issue);
            ps.setString(6, location);
            ps.setDouble(7, monthlyProgressPct);
            ps.setDouble(8, progressIncrementPct);
            setDate(ps, 9, reportDate);
            ps.setDouble(10, 100.0);
            ps.setString(11, todayWork);
            ps.setString(12, tomorrowPlan);
            ps.setLong(13, monthlyWorkPlanId);
            ps.setLong(14, weeklyWorkPlanId);
            ps.executeUpdate();
        }
    }

    private static void insertResources(Connection connection, long workPlanId, WorkTemplate template) throws Exception {
        String workerSql = """
                INSERT INTO work_plan_worker (created_at, updated_at, count, trade, work_plan_idx)
                VALUES (?, ?, ?, ?, ?)
                """;
        try (PreparedStatement ps = connection.prepareStatement(workerSql)) {
            for (Map.Entry<String, Integer> worker : template.workers.entrySet()) {
                ps.setTimestamp(1, NOW_TS);
                ps.setTimestamp(2, NOW_TS);
                ps.setInt(3, worker.getValue());
                ps.setString(4, worker.getKey());
                ps.setLong(5, workPlanId);
                ps.addBatch();
            }
            ps.executeBatch();
        }

        String equipmentSql = """
                INSERT INTO work_plan_equipment (created_at, updated_at, count, type, work_plan_idx)
                VALUES (?, ?, ?, ?, ?)
                """;
        try (PreparedStatement ps = connection.prepareStatement(equipmentSql)) {
            for (Map.Entry<String, Integer> equipment : template.equipment.entrySet()) {
                ps.setTimestamp(1, NOW_TS);
                ps.setTimestamp(2, NOW_TS);
                ps.setInt(3, equipment.getValue());
                ps.setString(4, equipment.getKey());
                ps.setLong(5, workPlanId);
                ps.addBatch();
            }
            ps.executeBatch();
        }
    }

    private static void insertWorkOrderEquipments(Connection connection, long workOrderId, WorkTemplate template) throws Exception {
        String sql = """
                INSERT INTO work_order_equipment (
                    created_at, updated_at, equipment_count, equipment_name, gate_idx, is_deleted, work_order_idx
                ) VALUES (?, ?, ?, ?, ?, ?, ?)
                """;
        int gate = 1;
        try (PreparedStatement ps = connection.prepareStatement(sql)) {
            for (Map.Entry<String, Integer> equipment : template.equipment.entrySet()) {
                ps.setTimestamp(1, NOW_TS);
                ps.setTimestamp(2, NOW_TS);
                ps.setInt(3, equipment.getValue());
                ps.setString(4, equipment.getKey());
                ps.setInt(5, gate++);
                ps.setBoolean(6, false);
                ps.setLong(7, workOrderId);
                ps.addBatch();
            }
            ps.executeBatch();
        }
    }

    private static Project loadProject(Connection connection, long projectId) throws Exception {
        String sql = "SELECT idx, name, location, start_date, end_date FROM project WHERE idx = ?";
        try (PreparedStatement ps = connection.prepareStatement(sql)) {
            ps.setLong(1, projectId);
            try (ResultSet rs = ps.executeQuery()) {
                if (!rs.next()) {
                    return null;
                }
                return new Project(
                        rs.getLong("idx"),
                        rs.getString("name"),
                        rs.getString("location"),
                        toLocalDate(rs.getDate("start_date")),
                        toLocalDate(rs.getDate("end_date"))
                );
            }
        }
    }

    private static List<TradeProcess> loadTradeProcesses(Connection connection, long projectId) throws Exception {
        String sql = """
                SELECT tp.idx, tp.trade_name, tp.process_name, tp.partner_company,
                       tp.planned_start, tp.planned_end, tp.weight_pct
                FROM trade_process tp
                JOIN master_schedule ms ON ms.idx = tp.master_schedule_id
                WHERE ms.project_id = ?
                  AND (tp.is_milestone IS NULL OR tp.is_milestone = false)
                ORDER BY tp.planned_start, tp.idx
                """;
        List<TradeProcess> processes = new ArrayList<>();
        try (PreparedStatement ps = connection.prepareStatement(sql)) {
            ps.setLong(1, projectId);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    processes.add(new TradeProcess(
                            rs.getLong("idx"),
                            rs.getString("trade_name"),
                            rs.getString("process_name"),
                            rs.getString("partner_company"),
                            toLocalDate(rs.getDate("planned_start")),
                            toLocalDate(rs.getDate("planned_end")),
                            rs.getDouble("weight_pct")
                    ));
                }
            }
        }
        return processes;
    }

    private static long countProjectWorkPlans(Connection connection, long projectId) throws Exception {
        String sql = """
                SELECT COUNT(*)
                FROM work_plan wp
                JOIN trade_process tp ON tp.idx = wp.trade_process_id
                JOIN master_schedule ms ON ms.idx = tp.master_schedule_id
                WHERE ms.project_id = ?
                """;
        return count(connection, sql, projectId, null);
    }

    private static long countProjectDemoWorkPlans(Connection connection, long projectId) throws Exception {
        String sql = """
                SELECT COUNT(*)
                FROM work_plan wp
                JOIN trade_process tp ON tp.idx = wp.trade_process_id
                JOIN master_schedule ms ON ms.idx = tp.master_schedule_id
                WHERE ms.project_id = ?
                  AND wp.note LIKE ?
                """;
        return count(connection, sql, projectId, MARKER + "%");
    }

    private static long count(Connection connection, String sql, long projectId, String markerLike) throws Exception {
        try (PreparedStatement ps = connection.prepareStatement(sql)) {
            ps.setLong(1, projectId);
            if (markerLike != null) {
                ps.setString(2, markerLike);
            }
            try (ResultSet rs = ps.executeQuery()) {
                rs.next();
                return rs.getLong(1);
            }
        }
    }

    private static void deleteDemoData(Connection connection, long projectId) throws Exception {
        String planSubquery = """
                SELECT wp.idx
                FROM work_plan wp
                JOIN trade_process tp ON tp.idx = wp.trade_process_id
                JOIN master_schedule ms ON ms.idx = tp.master_schedule_id
                WHERE ms.project_id = ?
                  AND wp.note LIKE ?
                """;

        executeDelete(connection,
                "DELETE FROM daily_report WHERE work_plan_idx IN (" + planSubquery + ") OR monthly_work_plan_idx IN (" + planSubquery + ")",
                projectId, MARKER + "%", projectId, MARKER + "%");
        executeDelete(connection,
                "DELETE FROM work_order_equipment WHERE work_order_idx IN (SELECT wo.idx FROM work_order wo WHERE wo.work_plan_id IN (" + planSubquery + "))",
                projectId, MARKER + "%");
        executeDelete(connection,
                "DELETE FROM work_order WHERE work_plan_id IN (" + planSubquery + ")",
                projectId, MARKER + "%");
        executeDelete(connection,
                "DELETE FROM work_plan_worker WHERE work_plan_idx IN (" + planSubquery + ")",
                projectId, MARKER + "%");
        executeDelete(connection,
                "DELETE FROM work_plan_equipment WHERE work_plan_idx IN (" + planSubquery + ")",
                projectId, MARKER + "%");

        deleteDemoPlansByType(connection, projectId, "WEEKLY");
        deleteDemoPlansByType(connection, projectId, "MONTHLY");
        deleteDemoPlansByType(connection, projectId, "YEARLY");
    }

    private static void deleteDemoPlansByType(Connection connection, long projectId, String planType) throws Exception {
        String sql = """
                DELETE wp
                FROM work_plan wp
                JOIN trade_process tp ON tp.idx = wp.trade_process_id
                JOIN master_schedule ms ON ms.idx = tp.master_schedule_id
                WHERE ms.project_id = ?
                  AND wp.note LIKE ?
                  AND wp.plan_type = ?
                """;
        try (PreparedStatement ps = connection.prepareStatement(sql)) {
            ps.setLong(1, projectId);
            ps.setString(2, MARKER + "%");
            ps.setString(3, planType);
            ps.executeUpdate();
        }
    }

    private static void executeDelete(Connection connection, String sql, Object... values) throws Exception {
        try (PreparedStatement ps = connection.prepareStatement(sql)) {
            for (int i = 0; i < values.length; i++) {
                Object value = values[i];
                if (value instanceof Long longValue) {
                    ps.setLong(i + 1, longValue);
                } else {
                    ps.setString(i + 1, String.valueOf(value));
                }
            }
            ps.executeUpdate();
        }
    }

    private static Map<String, String> loadEnv(Path path) throws Exception {
        Map<String, String> env = new LinkedHashMap<>();
        for (String rawLine : Files.readAllLines(path)) {
            String line = rawLine.trim();
            if (line.isEmpty() || line.startsWith("#") || !line.contains("=")) {
                continue;
            }
            int pos = line.indexOf('=');
            env.put(line.substring(0, pos), stripQuotes(line.substring(pos + 1)));
        }
        return env;
    }

    private static String required(Map<String, String> env, String key) {
        String value = env.get(key);
        if (value == null || value.isBlank()) {
            throw new IllegalStateException("Missing .env value: " + key);
        }
        return value;
    }

    private static String stripQuotes(String value) {
        if (value == null) {
            return null;
        }
        String trimmed = value.trim();
        if ((trimmed.startsWith("\"") && trimmed.endsWith("\""))
                || (trimmed.startsWith("'") && trimmed.endsWith("'"))) {
            return trimmed.substring(1, trimmed.length() - 1);
        }
        return trimmed;
    }

    private static LocalDate toLocalDate(Date date) {
        return date == null ? null : date.toLocalDate();
    }

    private static void setDate(PreparedStatement ps, int index, LocalDate date) throws Exception {
        if (date == null) {
            ps.setNull(index, java.sql.Types.DATE);
        } else {
            ps.setDate(index, Date.valueOf(date));
        }
    }

    private static double plannedPct(LocalDate start, LocalDate end, LocalDate today) {
        if (start == null || end == null || today == null) {
            return 0.0;
        }
        if (today.isBefore(start)) {
            return 0.0;
        }
        if (today.isAfter(end)) {
            return 100.0;
        }
        long totalDays = ChronoUnit.DAYS.between(start, end) + 1;
        long elapsedDays = ChronoUnit.DAYS.between(start, today) + 1;
        if (totalDays <= 0) {
            return 100.0;
        }
        return roundPct(elapsedDays * 100.0 / totalDays);
    }

    private static double roundPct(double value) {
        return Math.round(value * 10.0) / 10.0;
    }

    private static LocalDate min(LocalDate left, LocalDate right) {
        return left.isBefore(right) ? left : right;
    }

    private static LocalDate max(LocalDate left, LocalDate right) {
        return left.isAfter(right) ? left : right;
    }

    private static String nonBlank(String value, String fallback) {
        return value == null || value.isBlank() ? fallback : value;
    }

    private static String nextPlanText(String processName, LocalDate reportDate, LocalDate reportUntil) {
        if (reportDate.isEqual(reportUntil)) {
            return processName + " 후속 공정 준비 및 자재 반입 점검";
        }
        return processName + " 잔여 구간 계획 물량 시공";
    }

    private record Options(long projectId, LocalDate cutoff, boolean force) {
        static Options parse(String[] args) {
            long projectId = DEFAULT_PROJECT_ID;
            LocalDate cutoff = DEFAULT_CUTOFF;
            boolean force = false;

            for (String arg : args) {
                if ("--force".equals(arg)) {
                    force = true;
                } else if (arg.startsWith("--project-id=")) {
                    projectId = Long.parseLong(arg.substring("--project-id=".length()));
                } else if (arg.startsWith("--cutoff=")) {
                    cutoff = LocalDate.parse(arg.substring("--cutoff=".length()));
                } else if (!arg.isBlank()) {
                    throw new IllegalArgumentException("Unknown argument: " + arg);
                }
            }

            return new Options(projectId, cutoff, force);
        }
    }

    private record Project(long id, String name, String location, LocalDate startDate, LocalDate endDate) {
    }

    private record TradeProcess(
            long id,
            String tradeName,
            String processName,
            String partnerCompany,
            LocalDate plannedStart,
            LocalDate plannedEnd,
            double weightPct
    ) {
    }

    private static class WorkTemplate {
        final String workTrade;
        final Map<String, Integer> workers;
        final Map<String, Integer> equipment;

        WorkTemplate(String workTrade, Map<String, Integer> workers, Map<String, Integer> equipment) {
            this.workTrade = workTrade;
            this.workers = workers;
            this.equipment = equipment;
        }

        int workerTotal() {
            return workers.values().stream().mapToInt(Integer::intValue).sum();
        }

        static WorkTemplate from(String tradeName, String processName) {
            String source = ((tradeName == null ? "" : tradeName) + " " + (processName == null ? "" : processName))
                    .toLowerCase(Locale.ROOT);

            if (source.contains("토공") || source.contains("터파기") || source.contains("흙막이")) {
                return new WorkTemplate("EARTHWORK",
                        orderedMap("SKILLED", 4, "COMMON", 6),
                        orderedMap("EXCAVATOR", 1, "DUMP_TRUCK", 3));
            }
            if (source.contains("골조") || source.contains("철근콘크리트")) {
                return new WorkTemplate("FRAME",
                        orderedMap("REBAR", 8, "FORMWORK", 6, "COMMON", 4),
                        orderedMap("TOWER_CRANE", 1, "CONCRETE_PUMP_TRUCK", 1));
            }
            if (source.contains("전기") || source.contains("통신") || source.contains("cctv")) {
                return new WorkTemplate("ELECTRIC",
                        orderedMap("ELECTRICIAN", 6, "COMMON", 3),
                        orderedMap("AERIAL_WORK_PLATFORM", 1));
            }
            if (source.contains("기계") || source.contains("설비") || source.contains("배관") || source.contains("소방")) {
                return new WorkTemplate("FACILITY",
                        orderedMap("PLUMBER", 5, "WELDER", 2, "COMMON", 3),
                        orderedMap("CONSTRUCTION_HOIST", 1, "MOBILE_CRANE", 1));
            }
            if (source.contains("방수")) {
                return new WorkTemplate("WATERPROOF",
                        orderedMap("WATERPROOFER", 5, "COMMON", 3),
                        orderedMap("OTHER", 1));
            }
            if (source.contains("조적")) {
                return new WorkTemplate("MASONRY",
                        orderedMap("MASON", 6, "COMMON", 3),
                        orderedMap("CONSTRUCTION_HOIST", 1));
            }
            if (source.contains("미장")) {
                return new WorkTemplate("PLASTER",
                        orderedMap("PLASTERER", 6, "COMMON", 3),
                        orderedMap("CONSTRUCTION_HOIST", 1));
            }
            if (source.contains("도장")) {
                return new WorkTemplate("PAINT",
                        orderedMap("PAINTER", 6, "COMMON", 2),
                        orderedMap("AERIAL_WORK_PLATFORM", 1));
            }
            if (source.contains("타일")) {
                return new WorkTemplate("TILE",
                        orderedMap("TILER", 6, "COMMON", 2),
                        orderedMap("CONSTRUCTION_HOIST", 1));
            }
            if (source.contains("조경")) {
                return new WorkTemplate("LANDSCAPE",
                        orderedMap("SKILLED", 3, "COMMON", 5),
                        orderedMap("FORKLIFT", 1, "WATER_TRUCK", 1));
            }
            if (source.contains("포장")) {
                return new WorkTemplate("PAVEMENT",
                        orderedMap("SKILLED", 4, "COMMON", 4),
                        orderedMap("ROAD_ROLLER", 1, "WATER_TRUCK", 1));
            }
            if (source.contains("기초") || source.contains("파일") || source.contains("지반")) {
                return new WorkTemplate("REBAR",
                        orderedMap("REBAR", 5, "FORMWORK", 4, "COMMON", 4),
                        orderedMap("PILE_DRIVER", 1, "CONCRETE_PUMP_TRUCK", 1));
            }
            if (source.contains("가설") || source.contains("크레인") || source.contains("리프트")) {
                return new WorkTemplate("ETC",
                        orderedMap("SKILLED", 4, "COMMON", 5),
                        orderedMap("TOWER_CRANE", 1, "CONSTRUCTION_HOIST", 1));
            }
            return new WorkTemplate("ETC",
                    orderedMap("SKILLED", 3, "COMMON", 5),
                    orderedMap("FORKLIFT", 1));
        }
    }

    private static Map<String, Integer> orderedMap(Object... values) {
        Map<String, Integer> map = new LinkedHashMap<>();
        for (int i = 0; i < values.length; i += 2) {
            map.put((String) values[i], (Integer) values[i + 1]);
        }
        return map;
    }

    private static class Stats {
        int yearlyPlans;
        int monthlyPlans;
        int weeklyPlans;
        int workOrders;
        int dailyReports;
        int skippedProcesses;
    }
}
