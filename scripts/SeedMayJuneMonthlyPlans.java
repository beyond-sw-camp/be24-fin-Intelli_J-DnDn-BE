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
import java.util.Map;
import java.util.StringJoiner;

public class SeedMayJuneMonthlyPlans {

    private static final long PROJECT_ID = 5L;
    private static final String MARKER = "[DEMO_MONTHLY_20260506]";
    private static final LocalDate CUTOFF = LocalDate.of(2026, 5, 8);
    private static final Timestamp NOW = Timestamp.valueOf(LocalDateTime.now());

    public static void main(String[] args) throws Exception {
        Map<String, String> env = loadEnv(Path.of(".env"));
        Class.forName("org.mariadb.jdbc.Driver");

        try (Connection connection = DriverManager.getConnection(
                required(env, "DB_URL"),
                required(env, "DB_USER"),
                required(env, "DB_PASS")
        )) {
            connection.setAutoCommit(false);

            try {
                Project project = loadProject(connection);
                deleteExistingMarkerData(connection);

                Stats stats = new Stats();
                for (PlanSpec spec : specs()) {
                    seedSpec(connection, project, spec, stats);
                }

                connection.commit();
                System.out.println("May/June monthly seed completed");
                System.out.println("projectId=" + PROJECT_ID);
                System.out.println("marker=" + MARKER);
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

    private static void seedSpec(Connection connection, Project project, PlanSpec spec, Stats stats) throws Exception {
        TradeProcess process = findTradeProcess(connection, spec.processName);
        if (process == null) {
            throw new IllegalStateException("Trade process not found: " + spec.processName);
        }

        LocalDate start = max(spec.startDate, process.plannedStart);
        LocalDate end = min(spec.endDate, process.plannedEnd);
        if (start.isAfter(end)) {
            throw new IllegalStateException(
                    "Spec is outside master schedule: " + spec.name + " / " + process.processName
            );
        }

        Long rootMonthlyId = findRootMonthlyPlanId(connection, process.id);
        if (rootMonthlyId == null) {
            throw new IllegalStateException("Root monthly plan not found for trade_process_id=" + process.id);
        }

        WorkTemplate template = WorkTemplate.from(spec.trade);
        boolean started = !start.isAfter(CUTOFF);
        double monthProgress = started ? plannedPct(start, end, min(CUTOFF, end)) : 0.0;

        long monthlyId = insertWorkPlan(
                connection,
                process.id,
                rootMonthlyId,
                "MONTHLY",
                spec.name,
                spec.trade,
                spec.location,
                start,
                end,
                started ? start : null,
                monthProgress,
                started ? "IN_PROGRESS" : "PLANNED",
                spec.partner,
                spec.manager,
                spec.contact,
                spec.requiredCount,
                MARKER + " MONTH " + spec.key
        );
        insertResources(connection, monthlyId, spec.trade, spec.requiredCount, template);
        stats.monthlyPlans++;

        List<DateRange> weekRanges = splitInTwo(start, end);
        int weekNo = 1;
        double previousMonthProgress = 0.0;
        for (DateRange week : weekRanges) {
            boolean weekStarted = !week.start.isAfter(CUTOFF);
            double weeklyProgress = weekStarted ? plannedPct(week.start, week.end, min(CUTOFF, week.end)) : 0.0;
            String status = weekStarted ? "IN_PROGRESS" : "PLANNED";

            long weeklyId = insertWorkPlan(
                    connection,
                    process.id,
                    monthlyId,
                    "WEEKLY",
                    spec.name + " (" + weekNo + "/2주차)",
                    spec.trade,
                    spec.location,
                    week.start,
                    week.end,
                    weekStarted ? week.start : null,
                    weeklyProgress,
                    status,
                    spec.partner,
                    spec.manager,
                    spec.contact,
                    spec.requiredCount,
                    MARKER + " WEEK " + spec.key + "-" + weekNo + " / 작업시간: 08:00 ~ 17:00"
            );
            insertResources(connection, weeklyId, spec.trade, spec.requiredCount, template);
            stats.weeklyPlans++;

            String orderStatus = week.end.isBefore(CUTOFF) || week.end.isEqual(CUTOFF)
                    ? "COMPLETED"
                    : weekStarted ? "IN_PROGRESS" : "PLANNED";
            long workOrderId = insertWorkOrder(
                    connection,
                    weeklyId,
                    spec,
                    week.end,
                    orderStatus
            );
            insertWorkOrderEquipments(connection, workOrderId, spec.trade, template);
            stats.workOrders++;

            if (weekStarted) {
                LocalDate reportDate = min(CUTOFF, week.end);
                double currentMonthProgress = plannedPct(start, end, reportDate);
                double increment = Math.max(0.0, roundPct(currentMonthProgress - previousMonthProgress));
                insertDailyReport(
                        connection,
                        weeklyId,
                        monthlyId,
                        reportDate,
                        currentMonthProgress,
                        increment,
                        spec.requiredCount,
                        spec.location,
                        spec.name + " 계획 물량 정상 진행",
                        nextPlanText(spec.name, reportDate, end)
                );
                previousMonthProgress = currentMonthProgress;
                stats.dailyReports++;
            }

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
            double progress,
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
            ps.setTimestamp(1, NOW);
            ps.setTimestamp(2, NOW);
            ps.setBigDecimal(3, BigDecimal.valueOf(progress).setScale(2, RoundingMode.HALF_UP));
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

    private static long insertWorkOrder(Connection connection, long weeklyId, PlanSpec spec, LocalDate dueDate, String status)
            throws Exception {
        String sql = """
                INSERT INTO work_order (
                    created_at, updated_at, due_date, instruction_content, is_deleted,
                    partner_company_idx, safety_content, site_idx, status_code, title,
                    trade_type, work_detail, work_plan_id, work_time, worker_count
                ) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                """;

        try (PreparedStatement ps = connection.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)) {
            ps.setTimestamp(1, NOW);
            ps.setTimestamp(2, NOW);
            setDate(ps, 3, dueDate);
            ps.setString(4, spec.name + " 세부 작업 지시");
            ps.setBoolean(5, false);
            ps.setNull(6, java.sql.Types.BIGINT);
            ps.setString(7, "안전모, 안전대 체결 필수 및 작업 전 TBM");
            ps.setLong(8, PROJECT_ID);
            ps.setString(9, status);
            ps.setString(10, spec.name + " 작업지시서");
            ps.setString(11, spec.trade);
            ps.setString(12, "도면 및 시방서 기준 시공, 구간별 품질 확인, 작업 종료 후 정리정돈");
            ps.setLong(13, weeklyId);
            ps.setString(14, "08:00 ~ 17:00");
            ps.setInt(15, spec.requiredCount);
            ps.executeUpdate();

            try (ResultSet keys = ps.getGeneratedKeys()) {
                if (keys.next()) {
                    return keys.getLong(1);
                }
            }
        }

        throw new IllegalStateException("Failed to insert work_order: " + spec.name);
    }

    private static void insertDailyReport(
            Connection connection,
            long weeklyId,
            long monthlyId,
            LocalDate reportDate,
            double progress,
            double increment,
            int workerCount,
            String location,
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
            ps.setTimestamp(1, NOW);
            ps.setTimestamp(2, NOW);
            ps.setDouble(3, progress);
            ps.setInt(4, workerCount);
            ps.setString(5, "정상 진행");
            ps.setString(6, location);
            ps.setDouble(7, progress);
            ps.setDouble(8, increment);
            setDate(ps, 9, reportDate);
            ps.setDouble(10, 100.0);
            ps.setString(11, todayWork);
            ps.setString(12, tomorrowPlan);
            ps.setLong(13, monthlyId);
            ps.setLong(14, weeklyId);
            ps.executeUpdate();
        }
    }

    private static void insertResources(
            Connection connection,
            long workPlanId,
            String trade,
            int workerCount,
            WorkTemplate template
    ) throws Exception {
        try (PreparedStatement ps = connection.prepareStatement("""
                INSERT INTO work_plan_worker (created_at, updated_at, count, trade, work_plan_idx)
                VALUES (?, ?, ?, ?, ?)
                """)) {
            ps.setTimestamp(1, NOW);
            ps.setTimestamp(2, NOW);
            ps.setInt(3, workerCount);
            ps.setString(4, workerTrade(trade));
            ps.setLong(5, workPlanId);
            ps.executeUpdate();
        }

        try (PreparedStatement ps = connection.prepareStatement("""
                INSERT INTO work_plan_equipment (created_at, updated_at, count, type, work_plan_idx)
                VALUES (?, ?, ?, ?, ?)
                """)) {
            ps.setTimestamp(1, NOW);
            ps.setTimestamp(2, NOW);
            ps.setInt(3, template.equipmentCount);
            ps.setString(4, template.equipmentType);
            ps.setLong(5, workPlanId);
            ps.executeUpdate();
        }
    }

    private static void insertWorkOrderEquipments(Connection connection, long workOrderId, String trade, WorkTemplate template)
            throws Exception {
        try (PreparedStatement ps = connection.prepareStatement("""
                INSERT INTO work_order_equipment (
                    created_at, updated_at, equipment_count, equipment_name, gate_idx, is_deleted, work_order_idx
                ) VALUES (?, ?, ?, ?, ?, ?, ?)
                """)) {
            ps.setTimestamp(1, NOW);
            ps.setTimestamp(2, NOW);
            ps.setInt(3, template.equipmentCount);
            ps.setString(4, template.equipmentType);
            ps.setInt(5, 1);
            ps.setBoolean(6, false);
            ps.setLong(7, workOrderId);
            ps.executeUpdate();
        }
    }

    private static Project loadProject(Connection connection) throws Exception {
        try (PreparedStatement ps = connection.prepareStatement("SELECT idx, location FROM project WHERE idx = ?")) {
            ps.setLong(1, PROJECT_ID);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    return new Project(rs.getLong("idx"), rs.getString("location"));
                }
            }
        }
        throw new IllegalStateException("Project not found: " + PROJECT_ID);
    }

    private static TradeProcess findTradeProcess(Connection connection, String processName) throws Exception {
        String sql = """
                SELECT tp.idx, tp.process_name, tp.planned_start, tp.planned_end
                FROM trade_process tp
                JOIN master_schedule ms ON ms.idx = tp.master_schedule_id
                WHERE ms.project_id = ?
                  AND tp.process_name = ?
                  AND (tp.is_milestone IS NULL OR tp.is_milestone = false)
                """;
        try (PreparedStatement ps = connection.prepareStatement(sql)) {
            ps.setLong(1, PROJECT_ID);
            ps.setString(2, processName);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    return new TradeProcess(
                            rs.getLong("idx"),
                            rs.getString("process_name"),
                            rs.getDate("planned_start").toLocalDate(),
                            rs.getDate("planned_end").toLocalDate()
                    );
                }
            }
        }
        return null;
    }

    private static Long findRootMonthlyPlanId(Connection connection, long tradeProcessId) throws Exception {
        String sql = """
                SELECT idx
                FROM work_plan
                WHERE trade_process_id = ?
                  AND plan_type = 'MONTHLY'
                  AND parent_work_plan_id IS NULL
                ORDER BY CASE WHEN note LIKE '[DEMO_PROGRESS_20260508]%' THEN 0 ELSE 1 END, idx
                LIMIT 1
                """;
        try (PreparedStatement ps = connection.prepareStatement(sql)) {
            ps.setLong(1, tradeProcessId);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    return rs.getLong("idx");
                }
            }
        }
        return null;
    }

    private static void deleteExistingMarkerData(Connection connection) throws Exception {
        List<Long> planIds = markerPlanIds(connection);
        if (planIds.isEmpty()) {
            return;
        }

        String inClause = inClause(planIds.size());

        executeUpdate(connection,
                "DELETE FROM daily_report WHERE work_plan_idx IN (" + inClause + ") OR monthly_work_plan_idx IN (" + inClause + ")",
                doubled(planIds));
        executeUpdate(connection,
                "DELETE FROM work_order_equipment WHERE work_order_idx IN (SELECT idx FROM work_order WHERE work_plan_id IN (" + inClause + "))",
                planIds);
        executeUpdate(connection,
                "DELETE FROM work_order WHERE work_plan_id IN (" + inClause + ")",
                planIds);
        executeUpdate(connection,
                "DELETE FROM work_plan_worker WHERE work_plan_idx IN (" + inClause + ")",
                planIds);
        executeUpdate(connection,
                "DELETE FROM work_plan_equipment WHERE work_plan_idx IN (" + inClause + ")",
                planIds);
        executeUpdate(connection,
                "DELETE FROM work_plan WHERE idx IN (" + inClause + ") AND plan_type = 'WEEKLY'",
                planIds);
        executeUpdate(connection,
                "DELETE FROM work_plan WHERE idx IN (" + inClause + ") AND plan_type = 'MONTHLY'",
                planIds);
    }

    private static List<Long> markerPlanIds(Connection connection) throws Exception {
        String sql = """
                SELECT wp.idx
                FROM work_plan wp
                JOIN trade_process tp ON tp.idx = wp.trade_process_id
                JOIN master_schedule ms ON ms.idx = tp.master_schedule_id
                WHERE ms.project_id = ?
                  AND wp.note LIKE ?
                """;
        List<Long> ids = new ArrayList<>();
        try (PreparedStatement ps = connection.prepareStatement(sql)) {
            ps.setLong(1, PROJECT_ID);
            ps.setString(2, MARKER + "%");
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    ids.add(rs.getLong(1));
                }
            }
        }
        return ids;
    }

    private static void executeUpdate(Connection connection, String sql, List<Long> values) throws Exception {
        try (PreparedStatement ps = connection.prepareStatement(sql)) {
            int index = 1;
            for (Long value : values) {
                ps.setLong(index++, value);
            }
            ps.executeUpdate();
        }
    }

    private static List<Long> doubled(List<Long> values) {
        List<Long> doubled = new ArrayList<>(values.size() * 2);
        doubled.addAll(values);
        doubled.addAll(values);
        return doubled;
    }

    private static String inClause(int size) {
        StringJoiner joiner = new StringJoiner(",");
        for (int i = 0; i < size; i++) {
            joiner.add("?");
        }
        return joiner.toString();
    }

    private static List<DateRange> splitInTwo(LocalDate start, LocalDate end) {
        long days = ChronoUnit.DAYS.between(start, end) + 1;
        long firstDays = Math.max(1, (long) Math.ceil(days / 2.0));
        LocalDate firstEnd = min(start.plusDays(firstDays - 1), end);

        List<DateRange> ranges = new ArrayList<>();
        ranges.add(new DateRange(start, firstEnd));
        if (firstEnd.isBefore(end)) {
            ranges.add(new DateRange(firstEnd.plusDays(1), end));
        }
        return ranges;
    }

    private static double plannedPct(LocalDate start, LocalDate end, LocalDate today) {
        if (today.isBefore(start)) return 0.0;
        if (today.isAfter(end)) return 100.0;

        long totalDays = ChronoUnit.DAYS.between(start, end) + 1;
        long elapsedDays = ChronoUnit.DAYS.between(start, today) + 1;
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

    private static void setDate(PreparedStatement ps, int index, LocalDate date) throws Exception {
        if (date == null) {
            ps.setNull(index, java.sql.Types.DATE);
        } else {
            ps.setDate(index, Date.valueOf(date));
        }
    }

    private static String workerTrade(String trade) {
        return switch (trade) {
            case "PAINT" -> "PAINTER";
            case "ELECTRIC" -> "ELECTRICIAN";
            case "MASONRY" -> "MASON";
            case "WATERPROOF" -> "WATERPROOFER";
            case "PLASTER" -> "PLASTERER";
            case "TILE" -> "TILER";
            case "FRAME" -> "REBAR";
            case "FACILITY" -> "PLUMBER";
            default -> "COMMON";
        };
    }

    private static String nextPlanText(String name, LocalDate reportDate, LocalDate end) {
        if (reportDate.isBefore(end)) {
            return name + " 잔여 구간 계획 물량 시공";
        }
        return name + " 후속 공정 준비 및 품질 점검";
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
        String trimmed = value.trim();
        if ((trimmed.startsWith("\"") && trimmed.endsWith("\""))
                || (trimmed.startsWith("'") && trimmed.endsWith("'"))) {
            return trimmed.substring(1, trimmed.length() - 1);
        }
        return trimmed;
    }

    private static List<PlanSpec> specs() {
        List<PlanSpec> specs = new ArrayList<>();

        specs.add(new PlanSpec("MAY-01", "타워크레인·리프트 설치/해체", "5월 타워크레인 운용", "ETC", "현장 전구역", "이현장", "010-1111-2222", "한화건설", 2, "2026-05-01", "2026-05-31"));
        specs.add(new PlanSpec("MAY-02", "지상 저층부 골조", "5월 저층부 골조 마감", "FRAME", "101~104동 저층부", "박골조", "010-2222-3333", "(주)대한골조", 18, "2026-05-01", "2026-05-31"));
        specs.add(new PlanSpec("MAY-03", "지상 중층부 골조", "5월 중층부 골조", "FRAME", "101~104동 중층부", "박골조", "010-2222-3333", "(주)대한골조", 18, "2026-05-01", "2026-05-31"));
        specs.add(new PlanSpec("MAY-04", "조적", "5월 조적 공정", "MASONRY", "101동 저층부", "김조적", "010-3333-4444", "(주)세움조적", 10, "2026-05-01", "2026-05-31"));
        specs.add(new PlanSpec("MAY-05", "미장", "5월 미장 공정", "PLASTER", "101동 저층부 및 공용부", "이미장", "010-4444-5555", "(주)대성미장", 10, "2026-05-01", "2026-05-31"));
        specs.add(new PlanSpec("MAY-06", "단열", "5월 단열 공정", "ETC", "101~102동 외벽", "최단열", "010-5555-6666", "(주)그린단열", 8, "2026-05-01", "2026-05-31"));
        specs.add(new PlanSpec("MAY-07", "수변전·간선 전기공사", "5월 수변전·간선", "ELECTRIC", "지하 2층 전기실", "오전기", "010-6666-7777", "(주)광명전력", 10, "2026-05-01", "2026-05-31"));
        specs.add(new PlanSpec("MAY-08", "세대 전기·조명 공사", "5월 세대 전기·조명", "ELECTRIC", "전 세대 내부", "오전기", "010-6666-7777", "(주)광명전력", 20, "2026-05-01", "2026-05-31"));
        specs.add(new PlanSpec("MAY-09", "통신·홈네트워크·CCTV", "5월 통신·네트워크", "ELECTRIC", "전 세대 내부", "윤통신", "010-7777-8888", "(주)제일통신", 10, "2026-05-01", "2026-05-31"));
        specs.add(new PlanSpec("MAY-10", "소방전기·비상방송", "5월 소방전기", "ELECTRIC", "공용부", "정소방", "010-8888-9999", "(주)안전소방", 8, "2026-05-01", "2026-05-31"));
        specs.add(new PlanSpec("MAY-11", "부대토목·우오수·상하수도", "5월 부대토목 공사", "LANDSCAPE", "단지 외부", "유토목", "010-9999-0000", "(주)대지건설", 12, "2026-05-01", "2026-05-31"));

        specs.add(new PlanSpec("JUN-01", "타워크레인·리프트 설치/해체", "6월 타워크레인 운용", "ETC", "현장 전구역", "이현장", "010-1111-2222", "한화건설", 2, "2026-06-01", "2026-06-30"));
        specs.add(new PlanSpec("JUN-02", "지상 중층부 골조", "6월 중층부 골조", "FRAME", "101~104동 중층부", "박골조", "010-2222-3333", "(주)대한골조", 18, "2026-06-01", "2026-06-30"));
        specs.add(new PlanSpec("JUN-03", "조적", "6월 조적 공정", "MASONRY", "101~102동 중층부", "김조적", "010-3333-4444", "(주)세움조적", 12, "2026-06-01", "2026-06-30"));
        specs.add(new PlanSpec("JUN-04", "미장", "6월 미장 공정", "PLASTER", "101~102동 중층부 및 공용부", "이미장", "010-4444-5555", "(주)대성미장", 12, "2026-06-01", "2026-06-30"));
        specs.add(new PlanSpec("JUN-05", "방수", "6월 방수 공정", "WATERPROOF", "지하 및 옥상 방수 구간", "한방수", "010-5555-1111", "(주)우진방수", 8, "2026-06-01", "2026-06-30"));
        specs.add(new PlanSpec("JUN-06", "창호", "6월 창호 공정", "ETC", "101~104동 외창호", "임창호", "010-5555-2222", "(주)신성창호", 10, "2026-06-01", "2026-06-30"));
        specs.add(new PlanSpec("JUN-07", "타일", "6월 타일 공정", "TILE", "101동 욕실 및 공용부", "서타일", "010-5555-3333", "(주)바른타일", 10, "2026-06-01", "2026-06-30"));
        specs.add(new PlanSpec("JUN-08", "도배", "6월 도배 공정", "ETC", "101동 저층부", "이도배", "010-2222-3333", "(주)바른벽지", 10, "2026-06-01", "2026-06-30"));
        specs.add(new PlanSpec("JUN-09", "수변전·간선 전기공사", "6월 수변전·간선", "ELECTRIC", "지하 2층 전기실", "오전기", "010-6666-7777", "(주)광명전력", 8, "2026-06-01", "2026-06-30"));
        specs.add(new PlanSpec("JUN-10", "세대 전기·조명 공사", "6월 세대 전기·조명", "ELECTRIC", "전 세대 내부", "오전기", "010-6666-7777", "(주)광명전력", 20, "2026-06-01", "2026-06-30"));
        specs.add(new PlanSpec("JUN-11", "통신·홈네트워크·CCTV", "6월 통신·네트워크", "ELECTRIC", "전 세대 내부", "윤통신", "010-7777-8888", "(주)제일통신", 12, "2026-06-01", "2026-06-30"));
        specs.add(new PlanSpec("JUN-12", "소방전기·비상방송", "6월 소방전기", "ELECTRIC", "공용부", "정소방", "010-8888-9999", "(주)안전소방", 8, "2026-06-01", "2026-06-30"));
        specs.add(new PlanSpec("JUN-13", "부대토목·우오수·상하수도", "6월 부대토목 공사", "LANDSCAPE", "단지 외부", "유토목", "010-9999-0000", "(주)대지건설", 15, "2026-06-01", "2026-06-30"));

        return specs;
    }

    private record Project(long id, String location) {
    }

    private record TradeProcess(long id, String processName, LocalDate plannedStart, LocalDate plannedEnd) {
    }

    private record DateRange(LocalDate start, LocalDate end) {
    }

    private static class PlanSpec {
        final String key;
        final String processName;
        final String name;
        final String trade;
        final String location;
        final String manager;
        final String contact;
        final String partner;
        final int requiredCount;
        final LocalDate startDate;
        final LocalDate endDate;

        PlanSpec(
                String key,
                String processName,
                String name,
                String trade,
                String location,
                String manager,
                String contact,
                String partner,
                int requiredCount,
                String startDate,
                String endDate
        ) {
            this.key = key;
            this.processName = processName;
            this.name = name;
            this.trade = trade;
            this.location = location;
            this.manager = manager;
            this.contact = contact;
            this.partner = partner;
            this.requiredCount = requiredCount;
            this.startDate = LocalDate.parse(startDate);
            this.endDate = LocalDate.parse(endDate);
        }
    }

    private record WorkTemplate(String equipmentType, int equipmentCount) {
        static WorkTemplate from(String trade) {
            return switch (trade) {
                case "LANDSCAPE" -> new WorkTemplate("DUMP_TRUCK", 2);
                case "PAINT" -> new WorkTemplate("AERIAL_WORK_PLATFORM", 1);
                case "PAVEMENT" -> new WorkTemplate("ASPHALT_PAVER", 1);
                case "FRAME" -> new WorkTemplate("TOWER_CRANE", 1);
                case "ELECTRIC" -> new WorkTemplate("AERIAL_WORK_PLATFORM", 1);
                case "WATERPROOF" -> new WorkTemplate("OTHER", 1);
                default -> new WorkTemplate("OTHER", 1);
            };
        }
    }

    private static class Stats {
        int monthlyPlans;
        int weeklyPlans;
        int workOrders;
        int dailyReports;
    }
}
