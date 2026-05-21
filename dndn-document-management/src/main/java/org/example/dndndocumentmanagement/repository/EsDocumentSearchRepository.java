package org.example.dndndocumentmanagement.repository;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.example.dndndocumentmanagement.dto.DocumentPage;
import org.example.dndndocumentmanagement.dto.DocumentSearchCondition;
import org.example.dndndocumentmanagement.dto.DocumentSummary;
import org.example.dndndocumentmanagement.model.DocumentType;
import org.example.dndndocumentmanagement.model.entity.DocumentPreviewPayload;
import org.example.dndndocumentmanagement.model.entity.EsDocumentIndex;
import org.springframework.context.annotation.Profile;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.data.elasticsearch.core.ElasticsearchOperations;
import org.springframework.data.elasticsearch.core.SearchHit;
import org.springframework.data.elasticsearch.core.SearchHits;
import org.springframework.data.elasticsearch.core.query.StringQuery;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Component
@Profile("elastic")
public class EsDocumentSearchRepository implements DocumentSearchRepository {

    private final ElasticsearchOperations elasticsearchOperations; // feat : ES 통신 템플릿
    private final DocumentPreviewPayloadJpaRepository previewPayloadJpaRepository; // feat : 프리뷰 데이터 조회
    private final ObjectMapper objectMapper; // feat : JSON 파싱

    public EsDocumentSearchRepository(
            ElasticsearchOperations elasticsearchOperations,
            DocumentPreviewPayloadJpaRepository previewPayloadJpaRepository,
            ObjectMapper objectMapper
    ) {
        this.elasticsearchOperations = elasticsearchOperations;
        this.previewPayloadJpaRepository = previewPayloadJpaRepository;
        this.objectMapper = objectMapper;
    }

    @Override
    public DocumentPage search(DocumentSearchCondition condition) {
        StringBuilder queryBuilder = new StringBuilder();
        queryBuilder.append("{ \"bool\": { \"must\": [ ");

        // feat : 명시적 매핑 필드 (project_id)
        queryBuilder.append("{ \"term\": { \"project_id\": ").append(condition.projectId()).append(" } }");

        if (condition.documentType() != DocumentType.ALL) {
            // feat : 명시적 매핑 필드 (document_type)
            queryBuilder.append(", { \"match\": { \"document_type\": \"").append(condition.documentType().name()).append("\" } }");
        }

        if (condition.keyword() != null && !condition.keyword().isBlank()) {
            // feat : 명시적 매핑 필드와 암시적 매핑 필드(camelCase) 혼용
            queryBuilder.append(", { \"multi_match\": { ")
                    .append("\"query\": \"").append(condition.keyword()).append("\", ")
                    .append("\"fields\": [\"document_code\", \"file_name\", \"partnerName\", \"uploader\", \"tradeName\", \"content_text\"], ")
                    .append("\"type\": \"phrase\"")
                    .append(" } }");
        }

        if (condition.partnerName() != null && !condition.partnerName().isBlank()) {
            // feat : 암시적 매핑 필드 (partnerName)
            queryBuilder.append(", { \"term\": { \"partnerName\": \"").append(condition.partnerName()).append("\" } }");
        }

        queryBuilder.append(" ]");

        if (condition.startDate() != null || condition.endDate() != null) {
            // feat : 암시적 매핑 필드 (docDate)
            queryBuilder.append(", \"filter\": [ { \"range\": { \"docDate\": { ");
            boolean hasDate = false;
            if (condition.startDate() != null) {
                queryBuilder.append("\"gte\": \"").append(condition.startDate()).append("\"");
                hasDate = true;
            }
            if (condition.endDate() != null) {
                if (hasDate) queryBuilder.append(", ");
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
            return objectMapper.readValue(value, new TypeReference<>() {});
        } catch (Exception ignored) {
            return Map.of();
        }
    }

    private Sort.Direction sortDirection(String value) {
        return "asc".equalsIgnoreCase(value) ? Sort.Direction.ASC : Sort.Direction.DESC;
    }

    private String sortProperty(String value) {
        // feat : 엔티티 필드 매핑 규칙에 따라 명시적 필드와 암시적 필드 구분 반환
        return switch (value != null ? value : "") {
            case "docCode" -> "document_code";
            case "docType" -> "document_type";
            case "fileName" -> "file_name";
            case "origin" -> "origin";
            case "uploader" -> "uploader";
            case "uploadDate" -> "uploadDate";
            default -> "uploadDate";
        };
    }
}