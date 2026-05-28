package org.example.dndn.batch;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.autoconfigure.domain.EntityScan;
import org.springframework.data.jpa.repository.config.EnableJpaAuditing;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;

@SpringBootApplication(scanBasePackages = "org.example.dndn")
@EnableJpaAuditing
@EntityScan(basePackages = "org.example.dndn")
@EnableJpaRepositories(basePackages = "org.example.dndn")
public class BatchApplication {

    public static void main(String[] args) {
        // Job 완료 후 JVM 정상 종료 → K8s Pod Completed 상태
        System.exit(SpringApplication.exit(SpringApplication.run(BatchApplication.class, args)));
    }
}
