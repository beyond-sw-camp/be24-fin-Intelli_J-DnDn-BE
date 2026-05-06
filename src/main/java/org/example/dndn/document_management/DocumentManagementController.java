package org.example.dndn.document_management;

import lombok.RequiredArgsConstructor;
import org.example.dndn.common.model.BaseResponse;
import org.example.dndn.document_management.model.DocumentManagementDto;
import org.springframework.web.bind.annotation.*;

@RequestMapping("/document-management")
@RequiredArgsConstructor
@RestController
public class DocumentManagementController {
    private final DocumentManagementService documentManagementService;

    @GetMapping("/{project_id}")
    public BaseResponse read(@PathVariable(value = "project_id") Long project_id){
        DocumentManagementDto.ReadRes res = documentManagementService.read(project_id);
        return BaseResponse.success(res);
    }
}
