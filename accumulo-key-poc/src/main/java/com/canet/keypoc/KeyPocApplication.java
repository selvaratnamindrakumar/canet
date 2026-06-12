package com.canet.keypoc;

import com.canet.keypoc.repository.dynamo.DynamoCellStoreRepository;
import com.canet.keypoc.seed.SeedDataService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.CommandLineRunner;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Profile;

@Slf4j
@SpringBootApplication
public class KeyPocApplication {

    public static void main(String[] args) {
        SpringApplication.run(KeyPocApplication.class, args);
    }

    @Bean
    @Profile("local")
    public CommandLineRunner localInit(DynamoCellStoreRepository dynamo, SeedDataService seed) {
        return args -> {
            log.info("=== Accumulo Key Model POC — local profile ===");
            log.info("DynamoDB table design: PK=rowId (CGI)  SK=family#qualifier#timestamp");
            log.info("Aurora schema:         (cgi, family, qualifier, ts, value)");
            dynamo.createTableIfNotExists();
            seed.seedAll();
            log.info("=== Ready. Key endpoints:");
            log.info("  GET  /api/report                                        — full evaluation");
            log.info("  GET  /api/dynamo-easy-vs-hard                           — Catherine's classification");
            log.info("  GET  /api/poc/row/CGI12345?backend=dynamo               — ROW_SCAN");
            log.info("  GET  /api/poc/row/CGI12345/family/DEVICE?backend=dynamo — FAMILY_SCAN");
            log.info("  GET  /api/poc/row/CGI12345/family/DEVICE/qualifier/MODEL?backend=aurora — CELL_LOOKUP");
            log.info("  GET  /api/patterns                                      — all 8 NEO patterns");
        };
    }
}
