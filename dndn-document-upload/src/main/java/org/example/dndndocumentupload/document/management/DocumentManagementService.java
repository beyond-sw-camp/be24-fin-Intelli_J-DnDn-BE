package org.example.dndndocumentupload.document.management;

import lombok.RequiredArgsConstructor;
import org.example.dndndocumentupload.common.exception.BaseException;
import org.example.dndndocumentupload.common.model.BaseResponseStatus;
import org.example.dndndocumentupload.document.model.dto.DocumentManagementDto;
import org.example.dndndocumentupload.document.model.entity.MasterSchedule;
import org.example.dndndocumentupload.document.model.DocType;
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
    private final MasterScheduleRepository masterScheduleRepository;
    private final StorageService storageService;


    private static final Set<DocType> UNIQUE_DOC_TYPES = EnumSet.of(
            DocType.MASTER, DocType.MILESTONE, DocType.WEIGHT
    );

    public DocumentManagementDto.PageRes read(Long projectId, DocType docType, Pageable pageable) {
        Page<MasterSchedule> page;

        if (docType != null) {
            page = masterScheduleRepository.findAllByProjectIdxAndDocType(projectId, docType, pageable);
        } else {
            page = masterScheduleRepository.findAllByProjectIdx(projectId, pageable);
        }

        return DocumentManagementDto.PageRes.from(page);
    }

    public List<DocumentManagementDto.ReadRes> readPinnedSchedules(Long projectId) {
        DocType[] pinnedTypes = { DocType.MASTER, DocType.MILESTONE, DocType.WEIGHT };

        List<DocumentManagementDto.ReadRes> result = new ArrayList<>();
        for (DocType type : pinnedTypes) {
            masterScheduleRepository
                    .findFirstByProjectIdxAndDocTypeOrderByCreatedAtDesc(projectId, type)
                    .ifPresent(entity -> result.add(DocumentManagementDto.ReadRes.from(entity)));


        }
        return result;
    }

    @Transactional
    public void upload(DocumentManagementDto.UploadReq dto) {
//        // 1. 프로젝트 ID가 실제로 존재하는지 여부만 확인 (Long 타입 활용) (카프카 활용)
//        boolean projectExists = projectRepository.existsById(dto.getProjectId());
//        if (!projectExists) {
//            throw BaseException.from(BaseResponseStatus.DOCUMENT_PROJECT_NOT_FOUND);
//        }

        // 2. 고유 문서 타입 중복 체크 (기존 로직 유지)
        if (UNIQUE_DOC_TYPES.contains(dto.getDocType())) {
            boolean exists = masterScheduleRepository
                    .existsByProjectIdxAndDocType(dto.getProjectIdx(), dto.getDocType());
            if (exists) {
                throw BaseException.from(getDuplicateStatus(dto.getDocType()));
            }
        }

        // 저장소(S3 or 로컬)에 파일 업로드 후 key 받기
        String fileKey = storageService.store(dto.getFile(), dto.getProjectIdx(), dto.getDocType());

       MasterSchedule entity = dto.toEntity(fileKey);
       masterScheduleRepository.save(entity);
    }

    public String download(Long idx, boolean isPreview) {
        MasterSchedule entity = masterScheduleRepository.findById(idx)
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
