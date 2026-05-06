package org.example.dndn.document_management;

import lombok.RequiredArgsConstructor;
import org.example.dndn.document_management.model.DocumentManagementDto;
import org.example.dndn.project.model.entity.MasterSchedule;
import org.springframework.stereotype.Service;

@RequiredArgsConstructor
@Service
public class DocumentManagementService {
    private final DocumentManagementRepository documentManagementRepository;
    public DocumentManagementDto.ReadRes read(Long project_id) {
        MasterSchedule entity = documentManagementRepository.findByProjectIdx(project_id);
        return DocumentManagementDto.ReadRes.from(entity);
    }
}
