package org.example.dndncore.document_management;

import lombok.RequiredArgsConstructor;
import org.example.dndncore.common.exception.BaseException;
import org.example.dndncore.common.model.BaseResponseStatus;
import org.example.dndncore.document_management.model.DocumentManagementDto;
import org.example.dndncore.project.model.entity.MasterSchedule;
import org.example.dndncore.project.model.entity.Project;
import org.example.dndncore.project.model.enums.DocType;
import org.example.dndncore.project.repository.ProjectRepository;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;
import java.util.Set;

@RequiredArgsConstructor
@Service
public class DocumentManagementService {
    private final DocumentManagementRepository documentManagementRepository;
    private final StorageService storageService;   // ← 인터페이스 타입으로 변경
    private final ProjectRepository projectRepository;

    private static final Set<DocType> UNIQUE_DOC_TYPES = EnumSet.of(
            DocType.MASTER, DocType.MILESTONE, DocType.WEIGHT
    );

    public DocumentManagementDto.PageRes read(Long projectId, DocType docType, Pageable pageable) {
        Page<MasterSchedule> page;

        if (docType != null) {
            page = documentManagementRepository.findAllByProjectIdxAndDocType(projectId, docType, pageable);
        } else {
            page = documentManagementRepository.findAllByProjectIdx(projectId, pageable);
        }

        return DocumentManagementDto.PageRes.from(page);
    }

    public List<DocumentManagementDto.ReadRes> readPinnedSchedules(Long projectId) {
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
        Project project = projectRepository.findById(dto.getProjectId())
                .orElseThrow(() -> BaseException.from(BaseResponseStatus.DOCUMENT_PROJECT_NOT_FOUND));

        if (UNIQUE_DOC_TYPES.contains(dto.getDocType())) {
            boolean exists = documentManagementRepository
                    .existsByProjectIdxAndDocType(dto.getProjectId(), dto.getDocType());
            if (exists) {
                throw BaseException.from(getDuplicateStatus(dto.getDocType()));
            }
        }

        // 저장소(S3 or 로컬)에 파일 업로드 후 key 받기
        String fileKey = storageService.store(dto.getFile(), dto.getProjectId(), dto.getDocType());

        MasterSchedule entity = dto.toEntity(project, fileKey);
        documentManagementRepository.save(entity);
    }

    public String download(Long idx, boolean isPreview) {
        MasterSchedule entity = documentManagementRepository.findById(idx)
                .orElseThrow(() -> BaseException.from(BaseResponseStatus.DOCUMENT_NOT_FOUND));

        // 저장소(S3 or 로컬)에서 다운로드 URL 받기
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