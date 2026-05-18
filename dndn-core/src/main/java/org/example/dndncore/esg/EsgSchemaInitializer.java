package org.example.dndncore.esg;

import lombok.RequiredArgsConstructor;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class EsgSchemaInitializer implements ApplicationRunner {

    private final JdbcTemplate jdbcTemplate;

    @Override
    public void run(ApplicationArguments args) {
        createDailySnapshotTable();
        createZoneDailySnapshotTable();
        createMetricInputTable();
        addDailySnapshotColumns();
        addZoneDailySnapshotColumns();
        addMetricInputColumns();
        createIndexes();
    }

    private void createDailySnapshotTable() {
        jdbcTemplate.execute("""
                CREATE TABLE IF NOT EXISTS esg_daily_snapshot (
                    idx BIGINT NOT NULL AUTO_INCREMENT,
                    project_idx BIGINT NOT NULL,
                    report_date DATE NOT NULL,
                    environment_score DOUBLE DEFAULT 0,
                    social_score DOUBLE DEFAULT 0,
                    governance_score DOUBLE DEFAULT 0,
                    total_score DOUBLE DEFAULT 0,
                    level_value INT DEFAULT 0,
                    carbon_kg DOUBLE DEFAULT 0,
                    power_saving_kwh DOUBLE DEFAULT 0,
                    risk_count INT DEFAULT 0,
                    mission_rate INT DEFAULT 0,
                    safety_days INT DEFAULT 0,
                    zone_count INT DEFAULT 0,
                    snapshot_json LONGTEXT,
                    created_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
                    updated_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) ON UPDATE CURRENT_TIMESTAMP(6),
                    PRIMARY KEY (idx),
                    CONSTRAINT uk_esg_daily_snapshot_project_date UNIQUE (project_idx, report_date)
                ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci
                """);
    }

    private void createZoneDailySnapshotTable() {
        jdbcTemplate.execute("""
                CREATE TABLE IF NOT EXISTS esg_zone_daily_snapshot (
                    idx BIGINT NOT NULL AUTO_INCREMENT,
                    project_idx BIGINT NOT NULL,
                    report_date DATE NOT NULL,
                    zone_name VARCHAR(100) NOT NULL,
                    zone_type VARCHAR(30),
                    environment_score DOUBLE DEFAULT 0,
                    social_score DOUBLE DEFAULT 0,
                    governance_score DOUBLE DEFAULT 0,
                    total_score DOUBLE DEFAULT 0,
                    level_value INT DEFAULT 0,
                    carbon_kg DOUBLE DEFAULT 0,
                    power_saving_kwh DOUBLE DEFAULT 0,
                    risk_count INT DEFAULT 0,
                    mission_rate INT DEFAULT 0,
                    equipment_count INT DEFAULT 0,
                    high_risk_equipment_count INT DEFAULT 0,
                    contribution_weight DOUBLE DEFAULT 0,
                    contribution_score DOUBLE DEFAULT 0,
                    snapshot_json LONGTEXT,
                    created_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
                    updated_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) ON UPDATE CURRENT_TIMESTAMP(6),
                    PRIMARY KEY (idx),
                    CONSTRAINT uk_esg_zone_daily_snapshot_project_date_zone UNIQUE (project_idx, report_date, zone_name)
                ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci
                """);
    }

    private void createMetricInputTable() {
        jdbcTemplate.execute("""
                CREATE TABLE IF NOT EXISTS esg_metric_input (
                    idx BIGINT NOT NULL AUTO_INCREMENT,
                    project_idx BIGINT NOT NULL,
                    report_date DATE NOT NULL,
                    zone_name VARCHAR(100) NOT NULL,
                    carbon_kg DOUBLE DEFAULT 0,
                    power_usage_kwh DOUBLE DEFAULT 0,
                    power_saving_kwh DOUBLE DEFAULT 0,
                    wash_water_liters DOUBLE DEFAULT 0,
                    wastewater_liters DOUBLE DEFAULT 0,
                    wastewater_recovery_rate DOUBLE DEFAULT 0,
                    fine_dust_value DOUBLE DEFAULT 0,
                    noise_db DOUBLE DEFAULT 0,
                    complaint_count INT DEFAULT 0,
                    complaint_resolved_count INT DEFAULT 0,
                    safety_education_rate DOUBLE DEFAULT 0,
                    staffing_rate DOUBLE DEFAULT 0,
                    report_rate DOUBLE DEFAULT 0,
                    action_tracking_rate DOUBLE DEFAULT 0,
                    data_link_rate DOUBLE DEFAULT 0,
                    memo VARCHAR(500),
                    created_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
                    updated_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) ON UPDATE CURRENT_TIMESTAMP(6),
                    PRIMARY KEY (idx),
                    CONSTRAINT uk_esg_metric_input_project_date_zone UNIQUE (project_idx, report_date, zone_name)
                ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci
                """);
    }

    private void addDailySnapshotColumns() {
        addColumnIfMissing("esg_daily_snapshot", "project_idx", "BIGINT NOT NULL");
        addColumnIfMissing("esg_daily_snapshot", "report_date", "DATE NOT NULL");
        addColumnIfMissing("esg_daily_snapshot", "environment_score", "DOUBLE DEFAULT 0");
        addColumnIfMissing("esg_daily_snapshot", "social_score", "DOUBLE DEFAULT 0");
        addColumnIfMissing("esg_daily_snapshot", "governance_score", "DOUBLE DEFAULT 0");
        addColumnIfMissing("esg_daily_snapshot", "total_score", "DOUBLE DEFAULT 0");
        addColumnIfMissing("esg_daily_snapshot", "level_value", "INT DEFAULT 0");
        addColumnIfMissing("esg_daily_snapshot", "carbon_kg", "DOUBLE DEFAULT 0");
        addColumnIfMissing("esg_daily_snapshot", "power_saving_kwh", "DOUBLE DEFAULT 0");
        addColumnIfMissing("esg_daily_snapshot", "risk_count", "INT DEFAULT 0");
        addColumnIfMissing("esg_daily_snapshot", "mission_rate", "INT DEFAULT 0");
        addColumnIfMissing("esg_daily_snapshot", "safety_days", "INT DEFAULT 0");
        addColumnIfMissing("esg_daily_snapshot", "zone_count", "INT DEFAULT 0");
        addColumnIfMissing("esg_daily_snapshot", "snapshot_json", "LONGTEXT");
        addColumnIfMissing("esg_daily_snapshot", "created_at", "DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6)");
        addColumnIfMissing("esg_daily_snapshot", "updated_at", "DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) ON UPDATE CURRENT_TIMESTAMP(6)");
    }

    private void addZoneDailySnapshotColumns() {
        addColumnIfMissing("esg_zone_daily_snapshot", "project_idx", "BIGINT NOT NULL");
        addColumnIfMissing("esg_zone_daily_snapshot", "report_date", "DATE NOT NULL");
        addColumnIfMissing("esg_zone_daily_snapshot", "zone_name", "VARCHAR(100) NOT NULL");
        addColumnIfMissing("esg_zone_daily_snapshot", "zone_type", "VARCHAR(30)");
        addColumnIfMissing("esg_zone_daily_snapshot", "environment_score", "DOUBLE DEFAULT 0");
        addColumnIfMissing("esg_zone_daily_snapshot", "social_score", "DOUBLE DEFAULT 0");
        addColumnIfMissing("esg_zone_daily_snapshot", "governance_score", "DOUBLE DEFAULT 0");
        addColumnIfMissing("esg_zone_daily_snapshot", "total_score", "DOUBLE DEFAULT 0");
        addColumnIfMissing("esg_zone_daily_snapshot", "level_value", "INT DEFAULT 0");
        addColumnIfMissing("esg_zone_daily_snapshot", "carbon_kg", "DOUBLE DEFAULT 0");
        addColumnIfMissing("esg_zone_daily_snapshot", "power_saving_kwh", "DOUBLE DEFAULT 0");
        addColumnIfMissing("esg_zone_daily_snapshot", "risk_count", "INT DEFAULT 0");
        addColumnIfMissing("esg_zone_daily_snapshot", "mission_rate", "INT DEFAULT 0");
        addColumnIfMissing("esg_zone_daily_snapshot", "equipment_count", "INT DEFAULT 0");
        addColumnIfMissing("esg_zone_daily_snapshot", "high_risk_equipment_count", "INT DEFAULT 0");
        addColumnIfMissing("esg_zone_daily_snapshot", "contribution_weight", "DOUBLE DEFAULT 0");
        addColumnIfMissing("esg_zone_daily_snapshot", "contribution_score", "DOUBLE DEFAULT 0");
        addColumnIfMissing("esg_zone_daily_snapshot", "snapshot_json", "LONGTEXT");
        addColumnIfMissing("esg_zone_daily_snapshot", "created_at", "DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6)");
        addColumnIfMissing("esg_zone_daily_snapshot", "updated_at", "DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) ON UPDATE CURRENT_TIMESTAMP(6)");
    }

    private void addMetricInputColumns() {
        addColumnIfMissing("esg_metric_input", "project_idx", "BIGINT NOT NULL");
        addColumnIfMissing("esg_metric_input", "report_date", "DATE NOT NULL");
        addColumnIfMissing("esg_metric_input", "zone_name", "VARCHAR(100) NOT NULL");
        addColumnIfMissing("esg_metric_input", "carbon_kg", "DOUBLE DEFAULT 0");
        addColumnIfMissing("esg_metric_input", "power_usage_kwh", "DOUBLE DEFAULT 0");
        addColumnIfMissing("esg_metric_input", "power_saving_kwh", "DOUBLE DEFAULT 0");
        addColumnIfMissing("esg_metric_input", "wash_water_liters", "DOUBLE DEFAULT 0");
        addColumnIfMissing("esg_metric_input", "wastewater_liters", "DOUBLE DEFAULT 0");
        addColumnIfMissing("esg_metric_input", "wastewater_recovery_rate", "DOUBLE DEFAULT 0");
        addColumnIfMissing("esg_metric_input", "fine_dust_value", "DOUBLE DEFAULT 0");
        addColumnIfMissing("esg_metric_input", "noise_db", "DOUBLE DEFAULT 0");
        addColumnIfMissing("esg_metric_input", "complaint_count", "INT DEFAULT 0");
        addColumnIfMissing("esg_metric_input", "complaint_resolved_count", "INT DEFAULT 0");
        addColumnIfMissing("esg_metric_input", "safety_education_rate", "DOUBLE DEFAULT 0");
        addColumnIfMissing("esg_metric_input", "staffing_rate", "DOUBLE DEFAULT 0");
        addColumnIfMissing("esg_metric_input", "report_rate", "DOUBLE DEFAULT 0");
        addColumnIfMissing("esg_metric_input", "action_tracking_rate", "DOUBLE DEFAULT 0");
        addColumnIfMissing("esg_metric_input", "data_link_rate", "DOUBLE DEFAULT 0");
        addColumnIfMissing("esg_metric_input", "memo", "VARCHAR(500)");
        addColumnIfMissing("esg_metric_input", "created_at", "DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6)");
        addColumnIfMissing("esg_metric_input", "updated_at", "DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) ON UPDATE CURRENT_TIMESTAMP(6)");
    }

    private void createIndexes() {
        executeIfPossible("CREATE UNIQUE INDEX IF NOT EXISTS uk_esg_daily_snapshot_project_date ON esg_daily_snapshot (project_idx, report_date)");
        executeIfPossible("CREATE UNIQUE INDEX IF NOT EXISTS uk_esg_zone_daily_snapshot_project_date_zone ON esg_zone_daily_snapshot (project_idx, report_date, zone_name)");
        executeIfPossible("CREATE UNIQUE INDEX IF NOT EXISTS uk_esg_metric_input_project_date_zone ON esg_metric_input (project_idx, report_date, zone_name)");
        executeIfPossible("CREATE INDEX IF NOT EXISTS idx_esg_daily_snapshot_report_date ON esg_daily_snapshot (report_date)");
        executeIfPossible("CREATE INDEX IF NOT EXISTS idx_esg_zone_daily_snapshot_report_date ON esg_zone_daily_snapshot (report_date)");
        executeIfPossible("CREATE INDEX IF NOT EXISTS idx_esg_metric_input_report_date ON esg_metric_input (report_date)");
    }

    private void addColumnIfMissing(String tableName, String columnName, String columnDefinition) {
        jdbcTemplate.execute("ALTER TABLE " + tableName + " ADD COLUMN IF NOT EXISTS " + columnName + " " + columnDefinition);
    }

    private void executeIfPossible(String sql) {
        try {
            jdbcTemplate.execute(sql);
        } catch (RuntimeException ignored) {
            // MariaDB version differences should not block the application after the core tables are created.
        }
    }
}
