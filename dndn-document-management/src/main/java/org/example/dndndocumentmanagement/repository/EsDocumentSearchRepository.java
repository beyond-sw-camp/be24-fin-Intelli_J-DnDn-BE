package org.example.dndndocumentmanagement.repository;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import org.example.dndndocumentmanagement.dto.DocumentPage;
import org.example.dndndocumentmanagement.dto.DocumentSearchCondition;
import org.example.dndndocumentmanagement.dto.DocumentSummary;
import org.example.dndndocumentmanagement.model.DocumentType;
import org.example.dndndocumentmanagement.model.entity.DocumentPreviewPayload;
import org.example.dndndocumentmanagement.model.entity.EsDocumentIndex;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Primary;
import org.springframework.context.annotation.Profile;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.data.elasticsearch.core.ElasticsearchOperations;
import org.springframework.data.elasticsearch.core.SearchHit;
import org.springframework.data.elasticsearch.core.SearchHits;
import org.springframework.data.elasticsearch.core.query.StringQuery;
import org.springframework.stereotype.Component;

@Component
@Primary
@Profile("elastic")
public class EsDocumentSearchRepository implements DocumentSearchRepository {

    private static final Logger log = LoggerFactory.getLogger(EsDocumentSearchRepository.class);

    private final ElasticsearchOperations elasticsearchOperations;
    private final DocumentPreviewPayloadJpaRepository previewPayloadJpaRepository;
    private final ObjectMapper objectMapper;
    private final RdbDocumentSearchRepository rdbDocumentSearchRepository;

    public EsDocumentSearchRepository(
            ElasticsearchOperations elasticsearchOperations,
            DocumentPreviewPayloadJpaRepository previewPayloadJpaRepository,
            ObjectMapper objectMapper,
            RdbDocumentSearchRepository rdbDocumentSearchRepository
    ) {
        this.elasticsearchOperations = elasticsearchOperations;
        this.previewPayloadJpaRepository = previewPayloadJpaRepository;
        this.objectMapper = objectMapper;
        this.rdbDocumentSearchRepository = rdbDocumentSearchRepository;
    }

    @Override
    public DocumentPage search(DocumentSearchCondition condition) {
        if (!hasKeyword(condition)) {
            return rdbDocumentSearchRepository.search(condition);
        }

        try {
            return searchByElasticsearch(condition);
        } catch (RuntimeException e) {
            log.warn(
                    "Elasticsearch document search failed. Falling back to RDB. projectId={}, documentType={}, keyword={}, reason={}",
                    condition.projectId(),
                    condition.documentType(),
                    condition.keyword(),
                    e.getMessage()
            );
            return rdbDocumentSearchRepository.search(condition);
        }
    }

    private boolean hasKeyword(DocumentSearchCondition condition) {
        return condition.keyword() != null && !condition.keyword().isBlank();
    }

    private DocumentPage searchByElasticsearch(DocumentSearchCondition condition) {
        StringBuilder queryBuilder = new StringBuilder();
        queryBuilder.append("{ \"bool\": { \"must\": [ ");

        queryBuilder.append("{ \"term\": { \"project_id\": ").append(condition.projectId()).append(" } }");

        if (condition.documentType() != DocumentType.ALL) {
            queryBuilder.append(", { \"match\": { \"document_type\": \"")
                    .append(condition.documentType().name())
                    .append("\" } }");
        }

        if (condition.keyword() != null && !condition.keyword().isBlank()) {
            queryBuilder.append(", { \"multi_match\": { ")
                    .append("\"query\": \"").append(condition.keyword()).append("\", ")
                    .append("\"fields\": [\"document_code\", \"file_name\", \"partnername\", \"uploader\", \"tradename\", \"content_text\"], ")
                    .append("\"type\": \"best_fields\", ")
                    .append("\"lenient\": true")
                    .append(" } }");
        }

        if (condition.partnerName() != null && !condition.partnerName().isBlank()) {
            queryBuilder.append(", { \"term\": { \"partnername.keyword\": \"")
                    .append(condition.partnerName())
                    .append("\" } }");
        }

        queryBuilder.append(" ]");

        if (condition.startDate() != null || condition.endDate() != null) {
            queryBuilder.append(", \"filter\": [ { \"range\": { \"docDate\": { ");
            boolean hasDate = false;
            if (condition.startDate() != null) {
                queryBuilder.append("\"gte\": \"").append(condition.startDate()).append("\"");
                hasDate = true;
            }
            if (condition.endDate() != null) {
                if (hasDate) {
                    queryBuilder.append(", ");
                }
                queryBuilder.append("\"lte\": \"").append(condition.endDate()).append("\"");
            }
            queryBuilder.append(" } } } ]");
        }

        queryBuilder.append(" } }");

        PageRequest pageRequest = PageRequest.of(
                condition.page(),
                condition.size(),
                Sort.by(sortDirection(condition.sortDir()), sortProperty(condition.sortField()))
        );

        StringQuery query = new StringQuery(queryBuilder.toString(), pageRequest);
        SearchHits<EsDocumentIndex> searchHits = elasticsearchOperations.search(query, EsDocumentIndex.class);

        List<DocumentSummary> summaries = searchHits.getSearchHits().stream()
                .map(SearchHit::getContent)
                .map(this::toSummary)
                .collect(Collectors.toList());

        long totalElements = searchHits.getTotalHits();
        int totalPages = condition.size() > 0 ? (int) Math.ceil((double) totalElements / condition.size()) : 1;
        boolean isFirst = condition.page() == 0;
        boolean isLast = condition.page() >= totalPages - 1;

        return new DocumentPage(
                summaries,
                condition.page(),
                totalPages == 0 ? 1 : totalPages,
                totalElements,
                condition.size(),
                isFirst,
                isLast
        );
    }

    private DocumentSummary toSummary(EsDocumentIndex entity) {
        return new DocumentSummary(
                entity.getId(),
                DocumentType.fromCode(entity.getSourceType()),
                entity.getSourceId(),
                entity.getDocCode(),
                entity.getDocTypeCode(),
                entity.getFileName(),
                entity.getFileExt(),
                entity.getFileUrl(),
                entity.getOrigin(),
                entity.getPartnerName(),
                entity.getUploadDate(),
                entity.getDocDate(),
                entity.getUploader(),
                entity.getVersion(),
                entity.getFileSize(),
                entity.getStatusCode(),
                entity.getTradeName(),
                entity.isDownloadable(),
                rawPayload(entity.getId())
        );
    }

    private Map<String, Object> rawPayload(String documentId) {
        return previewPayloadJpaRepository.findById(documentId)
                .map(DocumentPreviewPayload::getPayloadJson)
                .map(this::parseJson)
                .orElse(Map.of());
    }

    private Map<String, Object> parseJson(String value) {
        if (value == null || value.isBlank()) {
            return Map.of();
        }
        try {
            return objectMapper.readValue(value, new TypeReference<>() {
            });
        } catch (Exception ignored) {
            return Map.of();
        }
    }

    private Sort.Direction sortDirection(String value) {
        return "asc".equalsIgnoreCase(value) ? Sort.Direction.ASC : Sort.Direction.DESC;
    }

    private String sortProperty(String value) {
        return switch (value != null ? value : "") {
            case "docCode" -> "document_code";
            case "docType" -> "document_type";
            case "fileName" -> "file_name";
            case "origin" -> "origin";
            case "uploader" -> "uploader";
            case "uploadDate" -> "uploaddate";
            default -> "uploaddate";
        };
    }
}
