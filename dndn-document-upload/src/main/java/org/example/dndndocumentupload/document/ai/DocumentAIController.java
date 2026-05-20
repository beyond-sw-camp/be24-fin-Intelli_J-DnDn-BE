package org.example.dndndocumentupload.document.ai;

import lombok.RequiredArgsConstructor;
import org.example.dndndocumentupload.common.model.BaseResponse;
import org.example.dndndocumentupload.document.model.dto.DocumentAiDto;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/master-schedule")
@RequiredArgsConstructor
public class DocumentAIController {
    private final DocumentAiService documentAiService;

    // 공정표 파일 업로드
    @PostMapping("/upload")
    public ResponseEntity<?> uploadAndExtract(
            @RequestBody DocumentAiDto.UploadReq dto
    ) {
        return ResponseEntity.ok(BaseResponse.success(
                documentAiService.uploadAndExtract(dto)
        ));
    }
}
