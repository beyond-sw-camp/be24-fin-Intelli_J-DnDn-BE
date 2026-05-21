package org.example.dndndocumentmanagement.loadtest;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.sql.Date;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.core.annotation.Order;
import org.springframework.jdbc.core.BatchPreparedStatementSetter;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

@Slf4j
@Component
@Order(50)
@RequiredArgsConstructor
@ConditionalOnProperty(prefix = "document.seed.load-test", name = "enabled", havingValue = "true")
public class DocumentLoadTestDataInitializer implements ApplicationRunner {

    private static final String SOURCE_MASTER = "MASTER";
    private static final String SOURCE_WORK_ORDER = "WORK_ORDER";
    private static final String SOURCE_DAILY_REPORT = "DAILY_REPORT";
    private static final String SITE_CODE_PREFIX = "LT";
    private static final LocalDate PROJECT_START = LocalDate.of(2024, 3, 1);
    private static final LocalDate PROJECT_END = LocalDate.of(2026, 11, 30);

    private static final List<TradeSeed> TRADES = List.of(
            new TradeSeed("Earthwork", "Earthwork and retaining wall", "Site and basement", 8.0),
            new TradeSeed("Frame", "Reinforced concrete frame", "Above-ground frame", 24.0),
            new TradeSeed("Rebar", "Rebar placement", "Typical floor", 10.0),
            new TradeSeed("Form", "Formwork and shoring", "Typical floor", 9.0),
            new TradeSeed("Waterproof", "Basement, roof, and bathroom waterproofing", "Basement and roof", 6.0),
            new TradeSeed("Electric", "Electrical, communication, and fire alarm", "All floors", 11.0),
            new TradeSeed("Facility", "Mechanical, plumbing, and ventilation", "All floors", 12.0),
            new TradeSeed("Masonry", "Masonry and ALC wall", "Units and common area", 5.0),
            new TradeSeed("Tile", "Tile and stone finish", "Bathrooms and common area", 7.0),
            new TradeSeed("Paint", "Painting and final finish", "Units and common area", 8.0)
    );

    private static final List<String> STATUS_CODES = List.of("DRAFT", "APPROVED", "IN_PROGRESS", "COMPLETED");
    private static final List<String> WORK_TIMES = List.of("08:00~17:00", "07:30~16:30", "08:00~18:00");

    private static final String UPSERT_DOCUMENT_SQL = """
            insert into document_index
            (id, project_id, source_type, source_id, doc_code, doc_type_code, file_name, file_ext, file_url,
             origin, partner_name, upload_date, doc_date, uploader, version, file_size, status_code, trade_name,
             downloadable, content_text, created_at, updated_at)
            values (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
            on duplicate key update
             project_id = values(project_id),
             source_type = values(source_type),
             source_id = values(source_id),
             doc_code = values(doc_code),
             doc_type_code = values(doc_type_code),
             file_name = values(file_name),
             file_ext = values(file_ext),
             file_url = values(file_url),
             origin = values(origin),
             partner_name = values(partner_name),
             upload_date = values(upload_date),
             doc_date = values(doc_date),
             uploader = values(uploader),
             version = values(version),
             file_size = values(file_size),
             status_code = values(status_code),
             trade_name = values(trade_name),
             downloadable = values(downloadable),
             content_text = values(content_text),
             updated_at = values(updated_at)
            """;

    private static final String UPSERT_PREVIEW_SQL = """
            insert into document_preview_payload
            (document_id, source_type, payload_json, created_at, updated_at)
            values (?, ?, ?, ?, ?)
            on duplicate key update
             source_type = values(source_type),
             payload_json = values(payload_json),
             updated_at = values(updated_at)
            """;

    private final JdbcTemplate jdbcTemplate;
    private final ObjectMapper objectMapper;
    private final PlatformTransactionManager transactionManager;

    @Value("${document.seed.load-test.site-count:20}")
    private int siteCount;

    @Value("${document.seed.load-test.daily-reports-per-site:2500}")
    private int dailyReportsPerSite;

    @Value("${document.seed.load-test.work-orders-per-site:2500}")
    private int workOrdersPerSite;

    @Value("${document.seed.load-test.batch-size:1000}")
    private int batchSize;

    @Value("${document.seed.load-test.project-id-offset:0}")
    private long projectIdOffset;

    @Value("${document.seed.load-test.master-source-id-offset:0}")
    private long masterSourceIdOffset;

    @Value("${document.seed.load-test.daily-report-source-id-offset:0}")
    private long dailyReportSourceIdOffset;

    @Value("${document.seed.load-test.work-order-source-id-offset:0}")
    private long workOrderSourceIdOffset;

    @Value("${document.seed.load-test.refresh-existing:false}")
    private boolean refreshExisting;

    @Override
    public void run(ApplicationArguments args) {
        int effectiveBatchSize = Math.max(1, batchSize);
        TransactionTemplate tx = new TransactionTemplate(transactionManager);

        log.info(
                "[DocumentLoadTestDataInitializer] seed start: sites={}, dailyReports/site={}, workOrders/site={}, batchSize={}",
                siteCount,
                dailyReportsPerSite,
                workOrdersPerSite,
                effectiveBatchSize
        );

        for (int siteNo = 1; siteNo <= siteCount; siteNo++) {
            int currentSiteNo = siteNo;
            tx.executeWithoutResult(status -> seedSite(currentSiteNo, effectiveBatchSize));
        }

        log.info("[DocumentLoadTestDataInitializer] seed completed");
    }

    private void seedSite(int siteNo, int effectiveBatchSize) {
        SiteSeed site = siteSeed(siteNo);
        long projectId = projectId(siteNo);

        if (!refreshExisting && siteIsComplete(siteNo, projectId)) {
            log.info("[DocumentLoadTestDataInitializer] skip site: code={}, projectId={}", site.code(), projectId);
            return;
        }

        LocalDateTime now = LocalDateTime.now();
        List<DocumentSeed> documents = new ArrayList<>(1 + dailyReportsPerSite + workOrdersPerSite);
        List<PreviewSeed> previews = new ArrayList<>(dailyReportsPerSite + workOrdersPerSite);

        documents.add(masterScheduleDocument(siteNo, site, projectId, now));
        addDailyReportDocuments(siteNo, site, projectId, now, documents, previews);
        addWorkOrderDocuments(siteNo, site, projectId, now, documents, previews);

        upsertDocuments(documents, effectiveBatchSize);
        upsertPreviews(previews, effectiveBatchSize);

        log.info(
                "[DocumentLoadTestDataInitializer] seeded site: code={}, projectId={}, documents={}, previews={}",
                site.code(),
                projectId,
                documents.size(),
                previews.size()
        );
    }

    private DocumentSeed masterScheduleDocument(int siteNo, SiteSeed site, long projectId, LocalDateTime now) {
        long sourceId = masterSourceId(siteNo);
        String documentId = documentId(SOURCE_MASTER, sourceId);
        LocalDate uploadDate = now.toLocalDate();
        String fileName = site.code() + "_master_schedule.xlsx";

        return new DocumentSeed(
                documentId,
                projectId,
                SOURCE_MASTER,
                sourceId,
                "MS-" + sourceId,
                SOURCE_MASTER,
                fileName,
                "xlsx",
                "load-test://master-schedule/" + site.code(),
                "hq",
                null,
                uploadDate,
                uploadDate,
                "load-test-seed",
                "v1.0",
                "",
                "APPROVED",
                null,
                true,
                site.projectName() + " master schedule"
        );
    }

    private void addDailyReportDocuments(
            int siteNo,
            SiteSeed site,
            long projectId,
            LocalDateTime now,
            List<DocumentSeed> documents,
            List<PreviewSeed> previews
    ) {
        int totalDays = (int) ChronoUnit.DAYS.between(PROJECT_START, PROJECT_END) + 1;
        LocalDate uploadDate = now.toLocalDate();

        for (int i = 0; i < dailyReportsPerSite; i++) {
            long sourceId = dailyReportSourceId(siteNo, i);
            TradeSeed trade = TRADES.get(i % TRADES.size());
            LocalDate reportDate = PROJECT_START.plusDays(i % totalDays);
            double actualProgress = round1(Math.min(100.0, 2.0 + i * 98.0 / Math.max(1, dailyReportsPerSite)));
            double todayProgress = round1(Math.min(100.0, 35.0 + (i % 66)));
            int workerCount = 10 + (i % 45);
            String location = site.zoneName(i);
            String issue = issueText(i);
            String todayWork = String.format(
                    "[%s] %s work in progress. Material delivery and field status recorded #%04d",
                    site.code(),
                    trade.processName(),
                    i + 1
            );
            String tomorrowPlan = String.format(
                    "[%s] Continue %s work and safety inspection #%04d",
                    site.code(),
                    trade.tradeName(),
                    i + 1
            );
            String documentId = documentId(SOURCE_DAILY_REPORT, sourceId);
            String fileName = String.format("[%s] %s daily report %04d", trade.tradeName(), reportDate, i + 1);

            documents.add(new DocumentSeed(
                    documentId,
                    projectId,
                    SOURCE_DAILY_REPORT,
                    sourceId,
                    "RP-" + sourceId,
                    SOURCE_DAILY_REPORT,
                    fileName,
                    "",
                    "",
                    "hq",
                    null,
                    uploadDate,
                    reportDate,
                    "site manager",
                    "v1.0",
                    "",
                    "APPROVED",
                    trade.tradeName(),
                    false,
                    joinText(trade.tradeName(), location, issue, todayWork, tomorrowPlan)
            ));

            Map<String, Object> payload = new LinkedHashMap<>();
            payload.put("idx", sourceId);
            payload.put("process", trade.tradeName());
            payload.put("tradeType", trade.tradeName());
            payload.put("reportDate", reportDate);
            payload.put("actualProgress", actualProgress);
            payload.put("todayProgress", todayProgress);
            payload.put("actualWorkerCount", workerCount);
            payload.put("location", location);
            payload.put("issue", issue);
            payload.put("todayWork", todayWork);
            payload.put("tomorrowPlan", tomorrowPlan);
            previews.add(new PreviewSeed(documentId, SOURCE_DAILY_REPORT, toJson(payload), now));
        }
    }

    private void addWorkOrderDocuments(
            int siteNo,
            SiteSeed site,
            long projectId,
            LocalDateTime now,
            List<DocumentSeed> documents,
            List<PreviewSeed> previews
    ) {
        int totalDays = (int) ChronoUnit.DAYS.between(PROJECT_START, PROJECT_END) + 1;
        LocalDate uploadDate = now.toLocalDate();

        for (int i = 0; i < workOrdersPerSite; i++) {
            long sourceId = workOrderSourceId(siteNo, i);
            TradeSeed trade = TRADES.get(i % TRADES.size());
            LocalDate dueDate = PROJECT_START.plusDays((i * 2L) % totalDays);
            String location = site.zoneName(i);
            String title = String.format("[%s] %s work order #%04d", site.code(), trade.tradeName(), i + 1);
            String instructionContent = trade.processName() + " section work instruction and final checklist";
            String workDetail = String.format("%s / %s / work sequence %04d", location, trade.defaultLocation(), i + 1);
            String workTime = WORK_TIMES.get(i % WORK_TIMES.size());
            String safetyContent = safetyText(i);
            String statusCode = STATUS_CODES.get(i % STATUS_CODES.size());
            int workerCount = 8 + (i % 38);
            String documentId = documentId(SOURCE_WORK_ORDER, sourceId);

            documents.add(new DocumentSeed(
                    documentId,
                    projectId,
                    SOURCE_WORK_ORDER,
                    sourceId,
                    "WO-" + sourceId,
                    SOURCE_WORK_ORDER,
                    title,
                    "",
                    "",
                    "hq",
                    null,
                    uploadDate,
                    dueDate,
                    "site manager",
                    "v1.0",
                    "",
                    statusCode,
                    trade.tradeName(),
                    false,
                    joinText(title, trade.tradeName(), workDetail, workTime, safetyContent)
            ));

            Map<String, Object> payload = new LinkedHashMap<>();
            payload.put("idx", sourceId);
            payload.put("tradeType", trade.tradeName());
            payload.put("title", title);
            payload.put("instructionContent", instructionContent);
            payload.put("workDetail", workDetail);
            payload.put("workTime", workTime);
            payload.put("safetyContent", safetyContent);
            payload.put("dueDate", dueDate);
            payload.put("workerCount", workerCount);
            payload.put("statusCode", statusCode);
            payload.put("equipments", List.of());
            previews.add(new PreviewSeed(documentId, SOURCE_WORK_ORDER, toJson(payload), now));
        }
    }

    private boolean siteIsComplete(int siteNo, long projectId) {
        long masterCount = countDocuments(projectId, SOURCE_MASTER, masterSourceId(siteNo), masterSourceId(siteNo));
        long dailyReportCount = countDocuments(
                projectId,
                SOURCE_DAILY_REPORT,
                dailyReportSourceId(siteNo, 0),
                dailyReportSourceId(siteNo, dailyReportsPerSite - 1)
        );
        long workOrderCount = countDocuments(
                projectId,
                SOURCE_WORK_ORDER,
                workOrderSourceId(siteNo, 0),
                workOrderSourceId(siteNo, workOrdersPerSite - 1)
        );
        long dailyReportPreviewCount = countPreviews(
                projectId,
                SOURCE_DAILY_REPORT,
                dailyReportSourceId(siteNo, 0),
                dailyReportSourceId(siteNo, dailyReportsPerSite - 1)
        );
        long workOrderPreviewCount = countPreviews(
                projectId,
                SOURCE_WORK_ORDER,
                workOrderSourceId(siteNo, 0),
                workOrderSourceId(siteNo, workOrdersPerSite - 1)
        );

        return masterCount >= 1
                && dailyReportCount >= dailyReportsPerSite
                && workOrderCount >= workOrdersPerSite
                && dailyReportPreviewCount >= dailyReportsPerSite
                && workOrderPreviewCount >= workOrdersPerSite;
    }

    private long countDocuments(long projectId, String sourceType, long firstSourceId, long lastSourceId) {
        if (lastSourceId < firstSourceId) {
            return 0;
        }
        Long count = jdbcTemplate.queryForObject(
                """
                        select count(*)
                        from document_index
                        where project_id = ?
                          and source_type = ?
                          and source_id between ? and ?
                        """,
                Long.class,
                projectId,
                sourceType,
                firstSourceId,
                lastSourceId
        );
        return count == null ? 0 : count;
    }

    private long countPreviews(long projectId, String sourceType, long firstSourceId, long lastSourceId) {
        if (lastSourceId < firstSourceId) {
            return 0;
        }
        Long count = jdbcTemplate.queryForObject(
                """
                        select count(*)
                        from document_preview_payload pp
                        join document_index di on di.id = pp.document_id
                        where di.project_id = ?
                          and di.source_type = ?
                          and di.source_id between ? and ?
                        """,
                Long.class,
                projectId,
                sourceType,
                firstSourceId,
                lastSourceId
        );
        return count == null ? 0 : count;
    }

    private void upsertDocuments(List<DocumentSeed> documents, int effectiveBatchSize) {
        for (int start = 0; start < documents.size(); start += effectiveBatchSize) {
            List<DocumentSeed> chunk = documents.subList(start, Math.min(start + effectiveBatchSize, documents.size()));
            jdbcTemplate.batchUpdate(UPSERT_DOCUMENT_SQL, new BatchPreparedStatementSetter() {
                @Override
                public void setValues(PreparedStatement ps, int i) throws SQLException {
                    bindDocument(ps, chunk.get(i));
                }

                @Override
                public int getBatchSize() {
                    return chunk.size();
                }
            });
        }
    }

    private void upsertPreviews(List<PreviewSeed> previews, int effectiveBatchSize) {
        for (int start = 0; start < previews.size(); start += effectiveBatchSize) {
            List<PreviewSeed> chunk = previews.subList(start, Math.min(start + effectiveBatchSize, previews.size()));
            jdbcTemplate.batchUpdate(UPSERT_PREVIEW_SQL, new BatchPreparedStatementSetter() {
                @Override
                public void setValues(PreparedStatement ps, int i) throws SQLException {
                    bindPreview(ps, chunk.get(i));
                }

                @Override
                public int getBatchSize() {
                    return chunk.size();
                }
            });
        }
    }

    private void bindDocument(PreparedStatement ps, DocumentSeed document) throws SQLException {
        ps.setString(1, document.id());
        ps.setLong(2, document.projectId());
        ps.setString(3, document.sourceType());
        ps.setLong(4, document.sourceId());
        ps.setString(5, document.docCode());
        ps.setString(6, document.docTypeCode());
        ps.setString(7, document.fileName());
        ps.setString(8, document.fileExt());
        ps.setString(9, document.fileUrl());
        ps.setString(10, document.origin());
        ps.setString(11, document.partnerName());
        ps.setDate(12, Date.valueOf(document.uploadDate()));
        ps.setDate(13, Date.valueOf(document.docDate()));
        ps.setString(14, document.uploader());
        ps.setString(15, document.version());
        ps.setString(16, document.fileSize());
        ps.setString(17, document.statusCode());
        ps.setString(18, document.tradeName());
        ps.setBoolean(19, document.downloadable());
        ps.setString(20, document.contentText());
        ps.setTimestamp(21, Timestamp.valueOf(LocalDateTime.now()));
        ps.setTimestamp(22, Timestamp.valueOf(LocalDateTime.now()));
    }

    private void bindPreview(PreparedStatement ps, PreviewSeed preview) throws SQLException {
        ps.setString(1, preview.documentId());
        ps.setString(2, preview.sourceType());
        ps.setString(3, preview.payloadJson());
        ps.setTimestamp(4, Timestamp.valueOf(preview.now()));
        ps.setTimestamp(5, Timestamp.valueOf(preview.now()));
    }

    private long projectId(int siteNo) {
        return projectIdOffset + siteNo;
    }

    private long masterSourceId(int siteNo) {
        return masterSourceIdOffset + siteNo;
    }

    private long dailyReportSourceId(int siteNo, int index) {
        return dailyReportSourceIdOffset + sequence(siteNo, dailyReportsPerSite, index);
    }

    private long workOrderSourceId(int siteNo, int index) {
        return workOrderSourceIdOffset + sequence(siteNo, workOrdersPerSite, index);
    }

    private long sequence(int siteNo, int perSite, int index) {
        return (long) (siteNo - 1) * perSite + index + 1L;
    }

    private String documentId(String sourceType, long sourceId) {
        return switch (sourceType) {
            case SOURCE_WORK_ORDER -> "WO-" + sourceId;
            case SOURCE_DAILY_REPORT -> "RP-" + sourceId;
            case "TRADE_PLAN" -> "MS-" + sourceId;
            default -> sourceType + "-" + sourceId;
        };
    }

    private SiteSeed siteSeed(int siteNo) {
        String code = SITE_CODE_PREFIX + "-" + String.format("%02d", siteNo);
        return new SiteSeed(
                code,
                "[" + code + "] Load Test Site " + String.format("%02d", siteNo),
                "Seoul Load Test District " + siteNo
        );
    }

    private String issueText(int index) {
        return switch (index % 8) {
            case 0 -> "No special issue";
            case 1 -> "Material delivery time adjusted";
            case 2 -> "Rain preparation and curing reinforced";
            case 3 -> "Partner work sequence adjusted";
            case 4 -> "Inspection waiting";
            case 5 -> "Equipment route control required";
            case 6 -> "Safety training supplemented";
            default -> "Final checklist supplemented";
        };
    }

    private String safetyText(int index) {
        return switch (index % 6) {
            case 0 -> "Repeat TBM before work and check fall prevention guardrails";
            case 1 -> "Place heavy equipment guide and check reverse alarm";
            case 2 -> "Check electrical tool breakers and wear protective gear";
            case 3 -> "Confirm hot work permission and place fire extinguisher";
            case 4 -> "Check openings and line marking";
            default -> "Install work-area controls and separate walking routes";
        };
    }

    private double round1(double value) {
        return Math.round(value * 10.0) / 10.0;
    }

    private String joinText(String... values) {
        List<String> parts = new ArrayList<>();
        for (String value : values) {
            if (value != null && !value.isBlank()) {
                parts.add(value.trim());
            }
        }
        return String.join(" ", parts);
    }

    private String toJson(Map<String, Object> payload) {
        try {
            return objectMapper.writeValueAsString(payload);
        } catch (JsonProcessingException e) {
            throw new IllegalArgumentException("Cannot serialize load-test document preview payload.", e);
        }
    }

    private record SiteSeed(String code, String projectName, String location) {
        private String zoneName(int index) {
            return switch (index % 5) {
                case 0 -> "Basement/B";
                case 1 -> "Ground/A";
                case 2 -> "Typical floor/C";
                case 3 -> "Common area/D";
                default -> "Unit/block";
            };
        }
    }

    private record TradeSeed(
            String tradeName,
            String processName,
            String defaultLocation,
            double weightPct
    ) {
    }

    private record DocumentSeed(
            String id,
            long projectId,
            String sourceType,
            long sourceId,
            String docCode,
            String docTypeCode,
            String fileName,
            String fileExt,
            String fileUrl,
            String origin,
            String partnerName,
            LocalDate uploadDate,
            LocalDate docDate,
            String uploader,
            String version,
            String fileSize,
            String statusCode,
            String tradeName,
            boolean downloadable,
            String contentText
    ) {
    }

    private record PreviewSeed(
            String documentId,
            String sourceType,
            String payloadJson,
            LocalDateTime now
    ) {
    }
}
