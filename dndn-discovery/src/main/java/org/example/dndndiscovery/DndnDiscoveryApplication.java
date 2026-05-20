package org.example.dndndiscovery;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.cloud.netflix.eureka.server.EnableEurekaServer;

@EnableEurekaServer
@SpringBootApplication
public class DndnDiscoveryApplication {

    public static void main(String[] args) {
        SpringApplication.run(DndnDiscoveryApplication.class, args);
    }
}
