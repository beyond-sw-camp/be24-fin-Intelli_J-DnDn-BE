package org.example.dndndocumentupload.document.ai_upload;

import lombok.RequiredArgsConstructor;
import org.example.dndndocumentupload.common.model.BaseResponse;
import org.example.dndndocumentupload.document.model.dto.DocumentAiDto;
import org.springframework.http.ResponseEntity;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/master-schedule")
@RequiredArgsConstructor
public class DocumentAIController {
    private final DocumentAiService documentAiService;
    private final KafkaTemplate<Long, DocumentAiDto.KafkaMessage<?>> kafkaTemplate;

    // 공정표 파일 업로드
    @PostMapping("/upload")
    public ResponseEntity<?> uploadAndExtract(
            @ModelAttribute DocumentAiDto.UploadReq dto
    ) {
        return ResponseEntity.ok(BaseResponse.success(documentAiService.uploadAndExtract(dto)));
    }
}
