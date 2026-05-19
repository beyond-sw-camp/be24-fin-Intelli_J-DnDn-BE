package org.example.dndndocumentupload.kafka;

import lombok.RequiredArgsConstructor;
import org.apache.kafka.clients.admin.NewTopic;
import org.springframework.context.annotation.Bean;
import org.springframework.kafka.config.TopicBuilder;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequiredArgsConstructor
public class TestDocumentProducerController {

    private final KafkaTemplate<String, String> kafkaTemplate;

    @Bean
    public NewTopic advancedTopic(){
        return TopicBuilder.name("dndn-advanced-topic")
                .partitions(3)
                .replicas(3)
                .build();
    }

    // ② 메시지 발송 API (Key 추가)
    // 호출 예시: http://localhost:20000/document/advanced-send?refId=1&docType=RESUME
    @GetMapping("/document/advanced-send")
    public String sendAdvancedEvent(@RequestParam Long refId, @RequestParam String docType) {

        String jsonPayload = String.format("{\"ref_id\": %d, \"doc_type\": \"%s\"}", refId, docType);

        /* * [핵심] kafkaTemplate.send(토픽명, 키, 밸류);
         * docType(예: RESUME, REPORT)을 '키(Key)'로 지정합니다.
         * 카프카는 이 키를 해시(Hash)해서 항상 동일한 파티션으로 메시지를 떨어뜨려 줍니다.
         */
        kafkaTemplate.send("dndn-advanced-topic", docType, jsonPayload);

        return String.format("발송 완료! [토픽: dndn-advanced-topic, 키: %s, 내용: %s]", docType, jsonPayload);
    }

    // 문서 MSA 포트가 20000번이라고 가정 시: http://localhost:20000/document/test-send?refId=111&docType=REPORT
    @GetMapping("/document/test-send")
    public String sendDocumentEvent(@RequestParam Long refId, @RequestParam String docType) {

        String jsonPayload = String.format("{\"ref_id\": %d, \"doc_type\": \"%s\"}", refId, docType);

        // "dndn-local-topic" 이라는 이름으로 메시지 전송 (카프카가 없으면 자동으로 토픽을 만듭니다)
        kafkaTemplate.send("dndn-local-topic", jsonPayload);

        return "로컬 카프카로 이벤트 발송 성공!: " + jsonPayload;
    }
}