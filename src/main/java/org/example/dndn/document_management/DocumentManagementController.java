package org.example.dndn.document_management;

import lombok.RequiredArgsConstructor;
import org.example.dndn.common.model.BaseResponse;
import org.example.dndn.document_management.model.DocumentManagementDto;
import org.example.dndn.project.model.enums.DocType;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;

@RequestMapping("/document-management")
@RequiredArgsConstructor
@RestController
public class DocumentManagementController {
    private final DocumentManagementService documentManagementService;

    @GetMapping("/{project_id}")
    public BaseResponse read(@PathVariable(value = "project_id") Long project_id){
        List<DocumentManagementDto.ReadRes> res = documentManagementService.read(project_id);
        return BaseResponse.success(res);
    }

    @PostMapping("/upload")
    public BaseResponse upload(
            @RequestParam("file") MultipartFile file,
            @RequestParam("docType") DocType docType,
            @RequestParam("docDate") String docDate,
            @RequestParam("origin") String origin,
            @RequestParam(value = "partnerName", required = false) String partnerName,
            @RequestParam(value = "version", required = false) String version,
            @RequestParam(value = "memo", required = false) String memo
    ){
        return BaseResponse.success("성공");
    }
}
