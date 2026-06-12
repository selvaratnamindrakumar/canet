package com.canet.poc;

import com.canet.poc.repository.dynamo.DynamoHandsetRepository;
import com.canet.poc.service.SeedDataService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.CommandLineRunner;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Profile;

@Slf4j
@SpringBootApplication
public class PocApplication {

    public static void main(String[] args) {
        SpringApplication.run(PocApplication.class, args);
    }

    @Bean
    @Profile("local")
    public CommandLineRunner localInit(DynamoHandsetRepository dynamo, SeedDataService seed) {
        return args -> {
            log.info("=== POC local profile: initialising DynamoDB table and seeding data ===");
            dynamo.createTableIfNotExists();
            seed.seedAll();
            log.info("=== Ready. Try: GET http://localhost:8080/api/compare/234-10-1234-56789 ===");
        };
    }
}
