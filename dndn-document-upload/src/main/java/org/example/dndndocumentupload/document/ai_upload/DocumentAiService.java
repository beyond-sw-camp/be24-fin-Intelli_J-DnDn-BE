package org.example.dndndocumentupload.document.ai_upload;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.example.dndndocumentupload.document.model.dto.DocumentAiDto;
import org.example.dndndocumentupload.document.model.dto.KafkaMessage;
import org.example.dndndocumentupload.document.model.dto.TradeProcessDto;
import org.example.dndndocumentupload.document.model.entity.MasterSchedule;
import org.example.dndndocumentupload.document.model.entity.ScheduleAiAnalysis;
import org.example.dndndocumentupload.document.upload.MasterScheduleRepository;
import org.example.dndndocumentupload.document.upload.StorageService;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;
import java.util.Optional;

@Slf4j
@RequiredArgsConstructor
@Service
public class DocumentAiService {

    private final StorageService storageService;
    private final MasterScheduleRepository masterScheduleRepository;
    private final OpenAiScheduleExtractor openAiScheduleExtractor;
    private final KafkaTemplate<String, KafkaMessage<?>> kafkaTemplate;
    private final ScheduleAiAnalysisRepository scheduleAiAnalysisRepository;

    /**
     * 공정표 파일 업로드 처리.
     *
     * 흐름:
     * 1) 같은 현장(projectIdx) + 같은 문서종류(docType) + 같은 파일명 으로 이미 업로드된 적이 있는지 확인.
     * 2) 있으면 → 그 MasterSchedule 에 연결된 AI 분석 JSON 이 있는지 확인.
     *      2-1) JSON 이 있으면  : AI 재호출하지 않고 기존 JSON 을 파싱해서 Kafka 로 전송.
     *      2-2) JSON 이 없으면  : 같은 MasterSchedule 에 대해 AI 호출 → 결과를 Kafka 로 전송.
     *                          (파일은 이미 저장돼 있으니 S3 재업로드 안 함)
     * 3) 처음 보는 파일이면 → S3 업로드 + MasterSchedule 저장 + AI 호출 → 결과를 Kafka 로 전송.
     */
    public void uploadAndExtract(DocumentAiDto.UploadReq dto) {
        validateFile(dto.getFile());

        String originalFileName = dto.getFile().getOriginalFilename();

        Optional<MasterSchedule> existingOpt = masterScheduleRepository
                .findFirstByProjectIdxAndDocTypeAndFileNameOrderByCreatedAtDesc(
                        dto.getProjectIdx(), dto.getDocType(), originalFileName);

        if (existingOpt.isPresent()) {
            // 같은 현장에 같은 파일이 이미 업로드된 적 있음 → 기존 데이터 재사용 시도
            handleExisting(dto, existingOpt.get());
        } else {
            // 처음 보는 파일 → 새로 저장하고 AI 분석
            handleNew(dto);
        }
    }

    /**
     * 케이스 A: 같은 파일이 이미 업로드돼있을 때.
     *  - AI 분석 결과(JSON) 가 DB 에 있으면  → 그걸 그대로 사용 (AI 재호출 X)
     *  - 없으면                              → AI 호출하고 결과 전송
     * 어느 쪽이든 S3 재업로드는 하지 않는다.
     */
    private void handleExisting(DocumentAiDto.UploadReq dto, MasterSchedule existing) {
        log.info("이미 존재하는 파일입니다. masterScheduleIdx={}", existing.getIdx());

        ScheduleAiAnalysis cached = scheduleAiAnalysisRepository
                .findByMasterScheduleIdxOrderByCreatedAt(existing.getIdx());

        List<TradeProcessDto.Req> schedules;
        Long masterScheduleIdx = existing.getIdx();

        if (cached != null && "SUCCESS".equals(cached.getStatus())) {
            // 캐시 hit: 기존 JSON 재사용
            log.info("기존 AI 분석 JSON 재사용. analysisIdx={}", cached.getIdx());
            schedules = openAiScheduleExtractor.dataParser(cached.getRawJson(), existing);
        } else {
            // 캐시 miss 또는 직전 분석이 FAILED 였음 → AI 호출
            log.warn("AI 분석 결과 없음 또는 실패. 새로 AI 호출. masterScheduleIdx={}", existing.getIdx());
            schedules = openAiScheduleExtractor.extractSchedule(dto.getFile(), existing.getIdx());
        }

        sendMessage(dto, masterScheduleIdx, schedules);
    }

    /**
     * 케이스 B: 처음 보는 파일.
     *  - S3 업로드 + MasterSchedule 저장
     *  - AI 호출
     *  - 결과를 Kafka 로 전송
     */
    private void handleNew(DocumentAiDto.UploadReq dto) {
        log.info("새로운 파일입니다. 저장 시작.");
        MasterSchedule saved = saveFile(dto);

        List<TradeProcessDto.Req> schedules =
                openAiScheduleExtractor.extractSchedule(dto.getFile(), saved.getIdx());

        sendMessage(dto, saved.getIdx(), schedules);
    }

    private MasterSchedule saveFile(DocumentAiDto.UploadReq dto) {
        // 저장소(S3 or 로컬)에 파일 업로드 후 key 받기
        String fileKey = storageService.store(dto.getFile(), dto.getProjectIdx(), dto.getDocType());

        // DB 에 문서 정보 저장
        MasterSchedule saveEntity = dto.toEntity(fileKey);
        return masterScheduleRepository.save(saveEntity);
    }

    /**
     * Kafka 전송 — 캐시 hit / miss 와 무관하게 항상 같은 포맷으로 발행.
     */
    public void sendMessage(DocumentAiDto.UploadReq dto,
                            Long masterScheduleIdx,
                            List<TradeProcessDto.Req> parsingData) {

        DocumentAiDto.KafkaProducerSend payload = DocumentAiDto.KafkaProducerSend.builder()
                .uploaderIdx(dto.getUploaderIdx())
                .projectIdx(dto.getProjectIdx())
                .masterScheduleIdx(masterScheduleIdx)
                .schedules(parsingData)
                .build();

        final String key = "user_" + dto.getUploaderIdx();
        final KafkaMessage<String> message = new KafkaMessage<>(key, payload);

        kafkaTemplate.send("document.uploaded.v3", key, message)
                .whenComplete((result, ex) -> {
                    if (ex == null) handleSuccess(message);
                    else handleFailure(message, ex);
                });
    }

    private void handleSuccess(KafkaMessage<?> message) {
        log.debug("Message was successfully sent / uploaderIdx : {}", message.uploaderIdx());
    }

    private void handleFailure(KafkaMessage<?> message, Throwable throwable) {
        log.debug("Failed to send message / uploaderIdx : {}", message.uploaderIdx());
        log.debug("Fail log : {}", throwable.getMessage());
    }

    private void validateFile(MultipartFile file) {
        if (file == null || file.isEmpty()) {
            throw new RuntimeException("업로드된 파일이 없습니다.");
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