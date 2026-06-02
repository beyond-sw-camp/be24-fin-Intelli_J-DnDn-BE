package org.example.dndncore.document_management;

import lombok.RequiredArgsConstructor;
import org.example.dndncore.auth.security.AuthAccessService;
import org.example.dndncore.common.exception.BaseException;
import org.example.dndncore.common.model.BaseResponseStatus;
import org.example.dndncore.document_event.DocumentEventProducer;
import org.example.dndncore.document_management.model.DocumentManagementDto;
import org.example.dndncore.project.model.entity.MasterSchedule;
import org.example.dndncore.project.model.entity.Project;
import org.example.dndncore.project.model.enums.DocType;
import org.example.dndncore.project.repository.ProjectRepository;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.sql.Date;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

@RequiredArgsConstructor
@Service
public class DocumentManagementService {
    private final DocumentManagementRepository documentManagementRepository;
    private final StorageService storageService;
    private final ProjectRepository projectRepository;
    private final NamedParameterJdbcTemplate jdbcTemplate;
    private final AuthAccessService authAccessService;
    private final DocumentEventProducer documentEventProducer;

    private static final Set<DocType> UNIQUE_DOC_TYPES = EnumSet.of(
            DocType.MASTER, DocType.MILESTONE, DocType.WEIGHT
    );

    public DocumentManagementDto.PageRes read(Long projectId, DocType docType, Pageable pageable) {
        authAccessService.assertProjectAccess(projectId);

        Page<MasterSchedule> page;

        if (docType != null) {
            page = documentManagementRepository.findAllByProjectIdxAndDocType(projectId, docType, pageable);
        } else {
            page = documentManagementRepository.findAllByProjectIdx(projectId, pageable);
        }

        return DocumentManagementDto.PageRes.from(page);
    }

    @Transactional(readOnly = true)
    public DocumentManagementDto.UploadedDocumentPageRes readUploadedDocuments(
            Long projectId,
            String docType,
            String keyword,
            java.time.LocalDate startDate,
            java.time.LocalDate endDate,
            String partnerName,
            String sortField,
            String sortDir,
            Pageable pageable
    ) {
        authAccessService.assertProjectAccess(projectId);

        int pageNumber = Math.max(0, pageable.getPageNumber());
        int pageSize = Math.min(Math.max(1, pageable.getPageSize()), 100);
        String normalizedDocType = normalizeDocType(docType);
        String whereSql = uploadedDocumentsWhereSql(normalizedDocType, keyword, startDate, endDate, partnerName);

        MapSqlParameterSource params = new MapSqlParameterSource()
                .addValue("projectId", projectId)
                .addValue("docType", normalizedDocType)
                .addValue("keyword", blankToNull(keyword))
                .addValue("keywordLike", likePattern(keyword))
                .addValue("startDate", startDate)
                .addValue("endDate", endDate)
                .addValue("partnerName", blankToNull(partnerName))
                .addValue("limit", pageSize)
                .addValue("offset", (long) pageNumber * pageSize);

        String fromSql = " FROM (" + uploadedDocumentsUnionSql() + ") d " + whereSql;
        Long total = jdbcTemplate.queryForObject("SELECT COUNT(*)" + fromSql, params, Long.class);
        long totalElements = total == null ? 0L : total;
        int totalPages = totalElements == 0 ? 1 : (int) Math.ceil((double) totalElements / pageSize);

        String dataSql = "SELECT *" + fromSql
                + " ORDER BY " + uploadedDocumentSortColumn(sortField) + " " + sortDirection(sortDir)
                + ", d.source_id DESC"
                + " LIMIT :limit OFFSET :offset";

        List<DocumentManagementDto.UploadedDocumentRes> content = jdbcTemplate.query(
                dataSql,
                params,
                (rs, rowNum) -> {
                    String sourceType = rs.getString("source_type");
                    Long sourceId = longValue(rs.getObject("source_id"));
                    Map<String, Object> raw = rawPayload(sourceType, rs);

                    return DocumentManagementDto.UploadedDocumentRes.builder()
                            .id(rs.getString("id"))
                            .sourceType(sourceType)
                            .sourceId(sourceId)
                            .docCode(rs.getString("doc_code"))
                            .docTypeCode(rs.getString("doc_type_code"))
                            .fileName(rs.getString("file_name"))
                            .fileExt(rs.getString("file_ext"))
                            .fileUrl(rs.getString("file_url"))
                            .origin(rs.getString("origin"))
                            .partnerName(rs.getString("partner_name"))
                            .uploadDate(localDate(rs.getDate("upload_date")))
                            .docDate(localDate(rs.getDate("doc_date")))
                            .uploader(rs.getString("uploader"))
                            .version(rs.getString("version"))
                            .fileSize(rs.getString("file_size"))
                            .statusCode(rs.getString("status_code"))
                            .tradeName(rs.getString("trade_name"))
                            .downloadable(rs.getBoolean("downloadable"))
                            .raw(raw)
                            .build();
                });

        return DocumentManagementDto.UploadedDocumentPageRes.builder()
                .content(content)
                .currentPage(pageNumber)
                .totalPages(totalPages)
                .totalElements(totalElements)
                .size(pageSize)
                .isFirst(pageNumber == 0)
                .isLast(pageNumber >= totalPages - 1)
                .build();
    }

    private String uploadedDocumentsUnionSql() {
        return """
                SELECT
                    'TRADE_PLAN' AS source_type,
                    ms.idx AS source_id,
                    CONCAT('MS-', ms.idx) AS id,
                    CONCAT('TP-', LPAD(ms.idx, 4, '0')) AS doc_code,
                    'TRADE_PLAN' AS doc_type_code,
                    ms.file_name AS file_name,
                    LOWER(SUBSTRING_INDEX(ms.file_name, '.', -1)) AS file_ext,
                    ms.file_url AS file_url,
                    CASE WHEN ms.is_partner = TRUE THEN 'partner' ELSE 'hq' END AS origin,
                    CASE WHEN ms.is_partner = TRUE THEN ms.affiliation_name ELSE NULL END AS partner_name,
                    DATE(ms.created_at) AS upload_date,
                    DATE(ms.created_at) AS doc_date,
                    COALESCE(ms.name, '') AS uploader,
                    'v1.0' AS version,
                    '' AS file_size,
                    'APPROVED' AS status_code,
                    NULL AS trade_name,
                    TRUE AS downloadable,
                    NULL AS title,
                    NULL AS instruction_content,
                    NULL AS work_detail,
                    NULL AS work_time,
                    NULL AS safety_content,
                    NULL AS due_date,
                    NULL AS worker_count,
                    NULL AS report_date,
                    NULL AS actual_progress,
                    NULL AS today_progress,
                    NULL AS actual_worker_count,
                    NULL AS location,
                    NULL AS issue,
                    NULL AS today_work,
                    NULL AS tomorrow_plan
                FROM master_schedule ms
                WHERE ms.project_id = :projectId
                  AND ms.doc_type = 'TRADE_PLAN'

                UNION ALL

                SELECT
                    'WORK_ORDER' AS source_type,
                    wo.idx AS source_id,
                    CONCAT('WO-', wo.idx) AS id,
                    CONCAT('WO-', LPAD(wo.idx, 4, '0')) AS doc_code,
                    'WORK_ORDER' AS doc_type_code,
                    COALESCE(wo.title, CONCAT('[', COALESCE(tp.trade_name, wo.trade_type, 'WORK'), '] work order')) AS file_name,
                    '' AS file_ext,
                    '' AS file_url,
                    'hq' AS origin,
                    NULL AS partner_name,
                    DATE(wo.created_at) AS upload_date,
                    COALESCE(wo.due_date, DATE(wo.created_at)) AS doc_date,
                    'site manager' AS uploader,
                    'v1.0' AS version,
                    '' AS file_size,
                    COALESCE(wo.status_code, 'OPEN') AS status_code,
                    COALESCE(tp.trade_name, wo.trade_type) AS trade_name,
                    FALSE AS downloadable,
                    wo.title AS title,
                    wo.instruction_content AS instruction_content,
                    wo.work_detail AS work_detail,
                    wo.work_time AS work_time,
                    wo.safety_content AS safety_content,
                    wo.due_date AS due_date,
                    wo.worker_count AS worker_count,
                    NULL AS report_date,
                    NULL AS actual_progress,
                    NULL AS today_progress,
                    NULL AS actual_worker_count,
                    COALESCE(wp.location, '') AS location,
                    NULL AS issue,
                    NULL AS today_work,
                    NULL AS tomorrow_plan
                FROM work_order wo
                LEFT JOIN work_plan wp ON wp.idx = wo.work_plan_id
                LEFT JOIN work_plan pwp ON pwp.idx = wp.parent_work_plan_id
                LEFT JOIN trade_process tp ON tp.idx = COALESCE(wp.trade_process_id, pwp.trade_process_id)
                LEFT JOIN master_schedule ms ON ms.idx = tp.master_schedule_id
                WHERE (wo.is_deleted = FALSE OR wo.is_deleted IS NULL)
                  AND wo.status_code = 'APPROVED'
                  AND (ms.project_id = :projectId OR wo.site_idx = :projectId)

                UNION ALL

                SELECT
                    'DAILY_REPORT' AS source_type,
                    dr.idx AS source_id,
                    CONCAT('RP-', dr.idx) AS id,
                    CONCAT('RP-', LPAD(dr.idx, 4, '0')) AS doc_code,
                    'DAILY_REPORT' AS doc_type_code,
                    CONCAT('[', COALESCE(tp.trade_name, wp.trade, 'WORK'), '] ', dr.report_date, ' daily report') AS file_name,
                    '' AS file_ext,
                    '' AS file_url,
                    'hq' AS origin,
                    NULL AS partner_name,
                    DATE(dr.created_at) AS upload_date,
                    dr.report_date AS doc_date,
                    'site manager' AS uploader,
                    'v1.0' AS version,
                    '' AS file_size,
                    'APPROVED' AS status_code,
                    COALESCE(tp.trade_name, wp.trade) AS trade_name,
                    FALSE AS downloadable,
                    NULL AS title,
                    NULL AS instruction_content,
                    NULL AS work_detail,
                    NULL AS work_time,
                    NULL AS safety_content,
                    NULL AS due_date,
                    NULL AS worker_count,
                    dr.report_date AS report_date,
                    dr.actual_progress AS actual_progress,
                    dr.today_progress AS today_progress,
                    dr.actual_worker_count AS actual_worker_count,
                    COALESCE(dr.location, wp.location, '') AS location,
                    dr.issue AS issue,
                    dr.today_work AS today_work,
                    dr.tomorrow_plan AS tomorrow_plan
                FROM daily_report dr
                JOIN work_plan wp ON wp.idx = dr.work_plan_idx
                LEFT JOIN work_plan pwp ON pwp.idx = wp.parent_work_plan_id
                LEFT JOIN trade_process tp ON tp.idx = COALESCE(wp.trade_process_id, pwp.trade_process_id)
                JOIN master_schedule ms ON ms.idx = tp.master_schedule_id
                WHERE ms.project_id = :projectId
                """;
    }

    private String uploadedDocumentsWhereSql(String docType, String keyword,
                                             java.time.LocalDate startDate,
                                             java.time.LocalDate endDate,
                                             String partnerName) {
        List<String> conditions = new ArrayList<>();
        if (!"ALL".equals(docType)) {
            conditions.add("d.doc_type_code = :docType");
        }
        if (blankToNull(keyword) != null) {
            conditions.add("""
                    LOWER(CONCAT_WS(' ',
                        d.doc_code,
                        d.file_name,
                        d.partner_name,
                        d.uploader,
                        d.trade_name,
                        d.doc_type_code
                    )) LIKE :keywordLike
                    """);
        }
        if (startDate != null) {
            conditions.add("d.doc_date >= :startDate");
        }
        if (endDate != null) {
            conditions.add("d.doc_date <= :endDate");
        }
        if (blankToNull(partnerName) != null) {
            conditions.add("d.partner_name = :partnerName");
        }
        return conditions.isEmpty() ? "" : " WHERE " + String.join(" AND ", conditions);
    }

    private String normalizeDocType(String value) {
        String normalized = blankToNull(value);
        if (normalized == null) return "ALL";
        normalized = normalized.trim().toUpperCase(Locale.ROOT).replace('-', '_');
        return switch (normalized) {
            case "WORK_INSTRUCTION", "WORK_ORDER" -> "WORK_ORDER";
            case "DAILY_REPORT", "REPORT" -> "DAILY_REPORT";
            case "CONSTRUCTION_PLAN", "TRADE_PLAN" -> "TRADE_PLAN";
            default -> "ALL";
        };
    }

    private String uploadedDocumentSortColumn(String sortField) {
        String normalized = blankToNull(sortField);
        if (normalized == null) return "d.upload_date";
        return switch (normalized) {
            case "docCode" -> "d.doc_code";
            case "docType" -> "d.doc_type_code";
            case "fileName" -> "d.file_name";
            case "origin" -> "d.origin";
            case "uploader" -> "d.uploader";
            case "uploadDate" -> "d.upload_date";
            default -> "d.upload_date";
        };
    }

    private String sortDirection(String value) {
        return "asc".equalsIgnoreCase(value) ? "ASC" : "DESC";
    }

    private String blankToNull(String value) {
        if (value == null) return null;
        String trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }

    private String likePattern(String value) {
        String trimmed = blankToNull(value);
        return trimmed == null ? null : "%" + trimmed.toLowerCase(Locale.ROOT) + "%";
    }

    private java.time.LocalDate localDate(Date date) {
        return date == null ? null : date.toLocalDate();
    }

    private Long longValue(Object value) {
        if (value instanceof Number number) return number.longValue();
        if (value == null) return null;
        return Long.valueOf(value.toString());
    }

    private Map<String, Object> rawPayload(String sourceType, ResultSet rs) throws SQLException {
        Map<String, Object> raw = new LinkedHashMap<>();
        raw.put("idx", longValue(rs.getObject("source_id")));

        if ("WORK_ORDER".equals(sourceType)) {
            raw.put("tradeType", rs.getString("trade_name"));
            raw.put("title", rs.getString("title"));
            raw.put("instructionContent", rs.getString("instruction_content"));
            raw.put("workDetail", rs.getString("work_detail"));
            raw.put("workTime", rs.getString("work_time"));
            raw.put("safetyContent", rs.getString("safety_content"));
            raw.put("dueDate", localDate(rs.getDate("due_date")));
            raw.put("workerCount", rs.getObject("worker_count"));
            raw.put("statusCode", rs.getString("status_code"));
            raw.put("equipments", List.of());
            return raw;
        }

        if ("DAILY_REPORT".equals(sourceType)) {
            raw.put("process", rs.getString("trade_name"));
            raw.put("tradeType", rs.getString("trade_name"));
            raw.put("reportDate", localDate(rs.getDate("report_date")));
            raw.put("actualProgress", rs.getObject("actual_progress"));
            raw.put("todayProgress", rs.getObject("today_progress"));
            raw.put("actualWorkerCount", rs.getObject("actual_worker_count"));
            raw.put("location", rs.getString("location"));
            raw.put("issue", rs.getString("issue"));
            raw.put("todayWork", rs.getString("today_work"));
            raw.put("tomorrowPlan", rs.getString("tomorrow_plan"));
        }

        return raw;
    }

    public List<DocumentManagementDto.ReadRes> readPinnedSchedules(Long projectId) {
        authAccessService.assertProjectAccess(projectId);

        DocType[] pinnedTypes = { DocType.MASTER, DocType.MILESTONE, DocType.WEIGHT };

        List<DocumentManagementDto.ReadRes> result = new ArrayList<>();
        for (DocType type : pinnedTypes) {
            documentManagementRepository
                    .findFirstByProjectIdxAndDocTypeOrderByCreatedAtDesc(projectId, type)
                    .ifPresent(entity -> result.add(DocumentManagementDto.ReadRes.from(entity)));
        }
        return result;
    }

    @Transactional
    public void upload(DocumentManagementDto.UploadReq dto) {
        authAccessService.assertProjectWriteAccess(dto.getProjectId());

        Project project = projectRepository.findById(dto.getProjectId())
                .orElseThrow(() -> BaseException.from(BaseResponseStatus.DOCUMENT_PROJECT_NOT_FOUND));

        if (UNIQUE_DOC_TYPES.contains(dto.getDocType())) {
            boolean exists = documentManagementRepository
                    .existsByProjectIdxAndDocType(dto.getProjectId(), dto.getDocType());
            if (exists) {
                throw BaseException.from(getDuplicateStatus(dto.getDocType()));
            }
        }

        String fileKey = storageService.store(dto.getFile(), dto.getProjectId(), dto.getDocType());

        MasterSchedule entity = documentManagementRepository.save(dto.toEntity(project, fileKey));
        documentEventProducer.publishDocumentUploaded(entity);
    }

    public String download(Long idx, boolean isPreview) {
        MasterSchedule entity = documentManagementRepository.findById(idx)
                .orElseThrow(() -> BaseException.from(BaseResponseStatus.DOCUMENT_NOT_FOUND));
        Long projectId = entity.getProject() != null ? entity.getProject().getIdx() : null;
        authAccessService.assertProjectAccess(projectId);

        return storageService.getDownloadUrl(entity.getFileUrl(), entity.getFileName(), isPreview);
    }

    private BaseResponseStatus getDuplicateStatus(DocType docType) {
        return switch (docType) {
            case MASTER -> BaseResponseStatus.DOCUMENT_DUPLICATE_MASTER;
            case MILESTONE -> BaseResponseStatus.DOCUMENT_DUPLICATE_MILESTONE;
            case WEIGHT -> BaseResponseStatus.DOCUMENT_DUPLICATE_WEIGHT;
            default -> BaseResponseStatus.FAIL;
        };
    }
}
