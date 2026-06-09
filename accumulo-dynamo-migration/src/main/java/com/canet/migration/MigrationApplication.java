package com.canet.migration;

import com.canet.migration.service.DataMigrationService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.CommandLineRunner;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Profile;

@Slf4j
@SpringBootApplication
public class MigrationApplication {

    public static void main(String[] args) {
        SpringApplication.run(MigrationApplication.class, args);
    }

    /**
     * On 'local' profile: auto-create the table and seed sample data so you can
     * immediately test the endpoints against DynamoDB Local without manual setup.
     */
    @Bean
    @Profile("local")
    public CommandLineRunner seedLocalData(DataMigrationService service) {
        return args -> {
            log.info("Local profile detected — initialising table and seeding demo data");
            service.ensureTableExists();

            service.writeAccumuloEntry("user:alice",   "profile",  "name",       "PUBLIC", "Alice Smith");
            service.writeAccumuloEntry("user:alice",   "profile",  "email",      "PUBLIC", "alice@example.com");
            service.writeAccumuloEntry("user:alice",   "activity", "lastLogin",  "ADMIN",  "2024-01-15T10:30:00Z");

            service.writeAccumuloEntry("user:bob",     "profile",  "name",       "PUBLIC", "Bob Jones");
            service.writeAccumuloEntry("user:bob",     "profile",  "email",      "PUBLIC", "bob@example.com");

            service.writeAccumuloEntry("sensor:001",   "reading",  "temperature","PUBLIC", "22.5");
            service.writeAccumuloEntry("sensor:001",   "reading",  "humidity",   "PUBLIC", "65.3");
            service.writeAccumuloEntry("sensor:001",   "metadata", "location",   "PUBLIC", "Building-A Floor-2");

            log.info("Demo data seeded. Try: GET http://localhost:8080/api/data/row/user:alice");
        };
    }
}
