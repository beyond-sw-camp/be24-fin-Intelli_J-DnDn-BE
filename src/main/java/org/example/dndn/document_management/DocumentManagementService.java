package org.example.dndn.document_management;

import lombok.RequiredArgsConstructor;
import org.example.dndn.document_management.model.DocumentManagementDto;
import org.example.dndn.project.model.entity.MasterSchedule;
import org.springframework.stereotype.Service;

import java.util.List;

@RequiredArgsConstructor
@Service
public class DocumentManagementService {
    private final DocumentManagementRepository documentManagementRepository;
    public List<DocumentManagementDto.ReadRes> read(Long project_id) {
        List<MasterSchedule> entity = documentManagementRepository.findAllByProjectIdx(project_id);
        return entity.stream().map(DocumentManagementDto.ReadRes::from).toList();
    }
}
