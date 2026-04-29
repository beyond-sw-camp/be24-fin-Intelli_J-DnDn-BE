package org.example.dndn;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

@EnableScheduling
@SpringBootApplication
public class DnDnApplication {
	// cicd 4
	public static void main(String[] args) {
		SpringApplication.run(DnDnApplication.class, args);
	}

}
