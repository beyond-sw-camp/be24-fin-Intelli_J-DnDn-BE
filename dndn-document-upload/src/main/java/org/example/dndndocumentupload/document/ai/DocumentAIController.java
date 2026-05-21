package org.example.dndndocumentupload.document.ai;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.poi.ss.formula.functions.T;
import org.example.dndndocumentupload.common.model.BaseResponse;
import org.example.dndndocumentupload.document.model.dto.DocumentAiDto;
import org.example.dndndocumentupload.document.model.dto.TradeProcessDto;
import org.springframework.http.ResponseEntity;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@Slf4j
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
        List<TradeProcessDto.Req> result =  documentAiService.uploadAndExtract(dto);
        sendMessage(dto.getUploaderIdx(), result);
        return ResponseEntity.ok(BaseResponse.success(result));
    }

    public void sendMessage(Long uploaderIdx, List<TradeProcessDto.Req> message) {
        final DocumentAiDto.KafkaMessage<String> testMessage = new DocumentAiDto.KafkaMessage<>(uploaderIdx, message);
        kafkaTemplate.send("document.uploaded.v3", uploaderIdx, testMessage)
                .whenComplete((result, ex) -> {
                    if(ex == null) handleSuccess(testMessage);
                    else handleFailure(testMessage, ex);
                });
    }

    private void handleSuccess(DocumentAiDto.KafkaMessage<?> testMessage) {
        log.debug("Message was successfully sent / TestMessage id : {}", testMessage.id());
    }

    private void handleFailure(DocumentAiDto.KafkaMessage<?> testMessage, Throwable throwable) {
        log.debug("Failed to send message / TestMessage id : {}", testMessage.id());
        log.debug("Fail log : {}", throwable.getMessage());
    }
}
