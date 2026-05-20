package org.example.dndndocumentmanagement;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.kafka.annotation.EnableKafka;

@EnableKafka
@SpringBootApplication
public class DndnDocumentManagementApplication {

    public static void main(String[] args) {
        SpringApplication.run(DndnDocumentManagementApplication.class, args);
    }
}
