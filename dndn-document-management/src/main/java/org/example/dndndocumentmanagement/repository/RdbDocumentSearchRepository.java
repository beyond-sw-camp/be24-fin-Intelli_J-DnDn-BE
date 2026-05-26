package org.example.dndndocumentmanagement.repository;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.persistence.criteria.Predicate;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.stream.Collectors;
import org.example.dndndocumentmanagement.dto.DocumentPage;
import org.example.dndndocumentmanagement.dto.DocumentSearchCondition;
import org.example.dndndocumentmanagement.dto.DocumentSummary;
import org.example.dndndocumentmanagement.model.DocumentType;
import org.example.dndndocumentmanagement.model.entity.DocumentIndex;
import org.example.dndndocumentmanagement.model.entity.DocumentPreviewPayload;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Component;

@Component
public class RdbDocumentSearchRepository implements DocumentSearchRepository {

    private final DocumentIndexJpaRepository documentIndexJpaRepository;
    private final DocumentPreviewPayloadJpaRepository previewPayloadJpaRepository;
    private final ObjectMapper objectMapper;

    public RdbDocumentSearchRepository(
            DocumentIndexJpaRepository documentIndexJpaRepository,
            DocumentPreviewPayloadJpaRepository previewPayloadJpaRepository,
            ObjectMapper objectMapper
    ) {
        this.documentIndexJpaRepository = documentIndexJpaRepository;
        this.previewPayloadJpaRepository = previewPayloadJpaRepository;
        this.objectMapper = objectMapper;
    }

    @Override
    public DocumentPage search(DocumentSearchCondition condition) {
        PageRequest pageRequest = PageRequest.of(
                condition.page(),
                condition.size(),
                Sort.by(sortDirection(condition.sortDir()), sortProperty(condition.sortField()))
        );
        Page<DocumentIndex> page = documentIndexJpaRepository.findAll(specification(condition), pageRequest);
        Map<String, Map<String, Object>> rawPayloads = rawPayloads(
                page.getContent().stream().map(DocumentIndex::getId).toList()
        );

        return new DocumentPage(
                page.getContent().stream()
                        .map(entity -> toSummary(entity, rawPayloads.getOrDefault(entity.getId(), Map.of())))
                        .toList(),
                page.getNumber(),
                page.getTotalPages() == 0 ? 1 : page.getTotalPages(),
                page.getTotalElements(),
                page.getSize(),
                page.isFirst(),
                page.isLast()
        );
    }

    private Specification<DocumentIndex> specification(DocumentSearchCondition condition) {
        return (root, query, cb) -> {
            List<Predicate> predicates = new ArrayList<>();
            predicates.add(cb.equal(root.get("projectId"), condition.projectId()));

            if (condition.documentType() != DocumentType.ALL) {
                predicates.add(cb.equal(root.get("docTypeCode"), condition.documentType().name()));
            }
            if (condition.keyword() != null) {
                String pattern = "%" + condition.keyword().toLowerCase(Locale.ROOT) + "%";
                predicates.add(cb.or(
                        cb.like(cb.lower(root.get("docCode")), pattern),
                        cb.like(cb.lower(root.get("fileName")), pattern),
                        cb.like(cb.lower(root.get("partnerName")), pattern),
                        cb.like(cb.lower(root.get("uploader")), pattern),
                        cb.like(cb.lower(root.get("tradeName")), pattern),
                        cb.like(cb.lower(root.get("contentText")), pattern)
                ));
            }
            if (condition.startDate() != null) {
                predicates.add(cb.greaterThanOrEqualTo(root.get("docDate"), condition.startDate()));
            }
            if (condition.endDate() != null) {
                predicates.add(cb.lessThanOrEqualTo(root.get("docDate"), condition.endDate()));
            }
            if (condition.partnerName() != null) {
                predicates.add(cb.equal(root.get("partnerName"), condition.partnerName()));
            }
            return cb.and(predicates.toArray(Predicate[]::new));
        };
    }

    private DocumentSummary toSummary(DocumentIndex entity, Map<String, Object> rawPayload) {
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
                rawPayload
        );
    }

    private Map<String, Map<String, Object>> rawPayloads(List<String> documentIds) {
        if (documentIds.isEmpty()) {
            return Map.of();
        }
        return previewPayloadJpaRepository.findAllById(documentIds)
                .stream()
                .collect(Collectors.toMap(
                        DocumentPreviewPayload::getDocumentId,
                        entity -> parseJson(entity.getPayloadJson())
                ));
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
        return switch (value) {
            case "docCode" -> "docCode";
            case "docType" -> "docTypeCode";
            case "fileName" -> "fileName";
            case "origin" -> "origin";
            case "uploader" -> "uploader";
            case "uploadDate" -> "uploadDate";
            default -> "uploadDate";
        };
    }
}
