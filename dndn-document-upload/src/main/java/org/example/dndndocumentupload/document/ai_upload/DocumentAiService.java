package org.example.dndndocumentupload.document.ai_upload;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.example.dndndocumentupload.document.model.dto.DocumentAiDto;
import org.example.dndndocumentupload.document.upload.MasterScheduleRepository;
import org.example.dndndocumentupload.document.upload.StorageService;
import org.example.dndndocumentupload.document.model.dto.TradeProcessDto;
import org.example.dndndocumentupload.document.model.entity.MasterSchedule;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;

@Slf4j
@RequiredArgsConstructor
@Service
public class DocumentAiService {
    private final StorageService storageService;
    private final MasterScheduleRepository masterScheduleRepository;
    private final OpenAiScheduleExtractor openAiScheduleExtractor;
    private final KafkaTemplate<Long, DocumentAiDto.KafkaMessage<?>> kafkaTemplate;


    public List<TradeProcessDto.Req> uploadAndExtract(DocumentAiDto.UploadReq dto) {
        if (dto.getFile() == null || dto.getFile().isEmpty()) {
            throw new RuntimeException("업로드된 파일이 없습니다.");
        }

//            // 일단 파일 로컬 저장
//            String uploadDir = System.getProperty("user.dir") + "/uploads/master-schedule/";
//            Path uploadPath = Paths.get(uploadDir).toAbsolutePath().normalize();
//
//            Files.createDirectories(uploadPath);
//
//            // 파일 이름 UUID로 변경 후 저장 (동일한 파일명이 겹쳐서 덮어씌워지는 것을 방지하기 위함)
//            String originalFileName = file.getOriginalFilename();
//            String storedFileName = UUID.randomUUID() + "_" + originalFileName;
//
//            Path filePath = uploadPath.resolve(storedFileName).normalize();
//
//            file.transferTo(filePath.toFile());


        // S3에 저장
        // 저장소(S3 or 로컬)에 파일 업로드 후 key 받기
        String fileKey = storageService.store(dto.getFile(), dto.getProjectIdx(), dto.getDocType());

        // DB에 문서 정보 저장
        MasterSchedule entity = dto.toEntity(fileKey);
        masterScheduleRepository.save(entity);
        validateFile(dto.getFile());

        List<TradeProcessDto.Req> result = openAiScheduleExtractor.extractSchedule(dto.getFile(), entity.getIdx());
        sendMessage(dto.getUploaderIdx(), result);

        return result;
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

    private void validateFile(MultipartFile file) {
        if (file == null || file.isEmpty()) {
            throw new RuntimeException("분석할 파일이 존재하지 않습니다.");
        }

        String originalFileName = file.getOriginalFilename();
        if (originalFileName == null) {
            throw new RuntimeException("파일 이름이 유효하지 않습니다.");
        }

        String fileName = originalFileName.toLowerCase();

        boolean supported =
                fileName.endsWith(".xlsx") ||
                        fileName.endsWith(".xls") ||
                        fileName.endsWith(".pdf") ||
                        fileName.endsWith(".png") ||
                        fileName.endsWith(".jpg") ||
                        fileName.endsWith(".jpeg");

        if (!supported) {
            throw new RuntimeException("지원하지 않는 파일 형식입니다. 엑셀, PDF, 이미지만 업로드할 수 있습니다.");
        }
    }
}
