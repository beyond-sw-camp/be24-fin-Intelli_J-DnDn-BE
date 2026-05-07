package org.example.dndn.document_management;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.example.dndn.common.model.BaseResponse;
import org.example.dndn.document_management.model.DocumentManagementDto;
import org.example.dndn.project.model.enums.DocType;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;

@Slf4j
@RequestMapping("/document-management")
@RequiredArgsConstructor
@RestController
public class DocumentManagementController {
    private final DocumentManagementService documentManagementService;

    @GetMapping("/{project_id}")
    public BaseResponse read(
            @PathVariable(value = "project_id") Long project_id,
            @PageableDefault(size = 10, sort = "createdAt", direction = Sort.Direction.DESC) Pageable pageable
    ) {
        DocumentManagementDto.PageRes res = documentManagementService.read(project_id, pageable);
        return BaseResponse.success(res);
    }

    @GetMapping("/{project_id}/pinned")
    public BaseResponse readPinned(@PathVariable(value = "project_id") Long project_id) {
        List<DocumentManagementDto.ReadRes> res = documentManagementService.readPinnedSchedules(project_id);
        return BaseResponse.success(res);
    }

    @PostMapping(value = "/upload", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public BaseResponse upload(@ModelAttribute DocumentManagementDto.UploadReq dto) {
        documentManagementService.upload(dto);
        return BaseResponse.success("성공");
    }
}
