package org.example.dndncore.kafka;

import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.example.dndncore.kafka.dto.KafkaConsumerDto;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Service;

@Service
public class TestDocumentConsumerService {
//    @KafkaListener(topics = "document.uploaded.v3", groupId = "first-document-upload", concurrency = "3")
    public void consume(KafkaConsumerDto message){
        System.out.println("key : " + message.getUploaderIdx() + " body : " + message.getReqList());
    }

    /*
    * [핵심]
    * groupId = "dndn-advanced-group:으로 그룹을 명확히 지정합니다.
    * concurrency = "3"으로 설정하면, 이 컨슈머 그룹 안에 일꾼 스레드가 3개 동시에 뜹니다.
    * 파티션도 3개, 일꾼도 3개 이므로 완벽하게 1:1로 매핑되어 분산 처리가 시작됩니다!
    * */

    @KafkaListener(topics = "dndn-local-topic", groupId = "dndn-local-group")
    public void consumeDocumentEvent(String message) {
        System.out.println("\n==================================================");
        System.out.println("[로컬 온프레미스 테스트] 문서 MSA가 보낸 이벤트를 정상 수신했습니다!");
        System.out.println("수신된 내용: " + message);
        System.out.println("==================================================\n");
    }

    @KafkaListener(topics = "dndn-advanced-topic", groupId = "dndn-advanced-group", concurrency = "3")
    public void consumerAdvancedEvent(ConsumerRecord<String, String> record){
        System.out.println("\n==================================================");
        System.out.println("[알림] 이벤트를 낚아챘습니다.");
        // 어떤 일꾼 스레드가 처리하고 있는지 확인
        System.out.println("작업 스레드 명 : "+Thread.currentThread().getName());
        // 어느 파티션에 저장되어 있던 데이터인지 확인
        System.out.println("도착한 파티션 번호 : "+record.partition());
        // 메시지 키(Key) 확인
        System.out.println("매핑된 메시지 키(Key) : "+ record.key());
        System.out.println("수신된 내용 : "+ record.value());
        System.out.println("==================================================\n");
    }
}