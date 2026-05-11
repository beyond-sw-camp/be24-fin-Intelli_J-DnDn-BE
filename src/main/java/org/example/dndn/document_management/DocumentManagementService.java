package org.example.dndn.document_management;

import lombok.RequiredArgsConstructor;
import org.example.dndn.common.exception.BaseException;
import org.example.dndn.common.model.BaseResponseStatus;
import org.example.dndn.document_management.model.DocumentManagementDto;
import org.example.dndn.project.model.entity.MasterSchedule;
import org.example.dndn.project.model.entity.Project;
import org.example.dndn.project.model.enums.DocType;
import org.example.dndn.project.repository.ProjectRepository;
import org.springframework.core.io.Resource;
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
    private final FileStorageService fileStorageService;
    private final ProjectRepository projectRepository;


    // 중복 체크 대상 (프로젝트당 1개만 허용되는 문서 종류)
    private static final Set<DocType> UNIQUE_DOC_TYPES = EnumSet.of(
            DocType.MASTER, DocType.MILESTONE, DocType.WEIGHT
    );

    // ★ 변경: docType 필터 파라미터 추가
    public DocumentManagementDto.PageRes read(Long projectId, DocType docType, Pageable pageable) {
        Page<MasterSchedule> page;

        if (docType != null) {
            // 특정 docType만 조회
            page = documentManagementRepository.findAllByProjectIdxAndDocType(projectId, docType, pageable);
        } else {
            // 전체 조회 (기존 동작)
            page = documentManagementRepository.findAllByProjectIdx(projectId, pageable);
        }

        return DocumentManagementDto.PageRes.from(page);
    }

    // 공정표 현황 - 고정 영역 (MASTER, MILESTONE, WEIGHT 각 최신 1건)
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
        // 1. 프로젝트 존재 확인
        Project project = projectRepository.findById(dto.getProjectId())
                .orElseThrow(() -> BaseException.from(BaseResponseStatus.DOCUMENT_PROJECT_NOT_FOUND));

        // 2. 중복 체크: MASTER/MILESTONE/WEIGHT는 프로젝트당 1개만 허용
        if (UNIQUE_DOC_TYPES.contains(dto.getDocType())) {
            boolean exists = documentManagementRepository
                    .existsByProjectIdxAndDocType(dto.getProjectId(), dto.getDocType());
            if (exists) {
                throw BaseException.from(getDuplicateStatus(dto.getDocType()));
            }
        }

        // 3. 파일을 로컬에 저장하고 경로 받기
        String fileUrl = fileStorageService.store(dto.getFile(), dto.getProjectId());

        // 4. 엔티티 생성 후 DB 저장
        MasterSchedule entity = dto.toEntity(project, fileUrl);
        documentManagementRepository.save(entity);
    }

    private BaseResponseStatus getDuplicateStatus(DocType docType) {
        return switch (docType) {
            case MASTER -> BaseResponseStatus.DOCUMENT_DUPLICATE_MASTER;
            case MILESTONE -> BaseResponseStatus.DOCUMENT_DUPLICATE_MILESTONE;
            case WEIGHT -> BaseResponseStatus.DOCUMENT_DUPLICATE_WEIGHT;
            default -> BaseResponseStatus.FAIL;
        };
    }

    public DocumentManagementDto.DownloadRes download(Long idx) {
        MasterSchedule entity = documentManagementRepository.findById(idx)
                .orElseThrow(() -> BaseException.from(BaseResponseStatus.DOCUMENT_NOT_FOUND));

        Resource resource = fileStorageService.loadAsResource(entity.getFileUrl());

        return DocumentManagementDto.DownloadRes.builder()
                .resource(resource)
                .fileName(entity.getFileName())
                .build();
    }
}