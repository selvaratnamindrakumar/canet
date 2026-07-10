package com.canet.poc.controller;

import com.canet.poc.model.HandsetRecord;
import com.canet.poc.repository.HandsetRepository;
import com.canet.poc.repository.dynamo.DynamoHandsetRepository;
import com.canet.poc.service.DataGeneratorService;
import com.canet.poc.service.SeedDataService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.*;

@Slf4j
@RestController
@RequestMapping("/api/admin")
public class AdminController {

    private final SeedDataService seedDataService;
    private final DataGeneratorService dataGeneratorService;
    private final DynamoHandsetRepository dynamoRepo;
    private final HandsetRepository auroraRepo;
    private final Optional<HandsetRepository> oracleRepo;

    public AdminController(
            SeedDataService seedDataService,
            DataGeneratorService dataGeneratorService,
            DynamoHandsetRepository dynamoRepo,
            @Qualifier("auroraHandsetRepository")  HandsetRepository auroraRepo,
            @Qualifier("oracleHandsetRepository")  Optional<HandsetRepository> oracleRepo) {
        this.seedDataService       = seedDataService;
        this.dataGeneratorService  = dataGeneratorService;
        this.dynamoRepo            = dynamoRepo;
        this.auroraRepo            = auroraRepo;
        this.oracleRepo            = oracleRepo;
    }

    /**
     * POST /api/admin/init
     * Creates DynamoDB table, runs Flyway (Aurora/Oracle schema), seeds 5 sample records
     * into every enabled backend.
     */
    @PostMapping("/init")
    public ResponseEntity<Map<String, Object>> init() {
        dynamoRepo.createTableIfNotExists();
        seedDataService.seedAll();

        Map<String, Object> response = new LinkedHashMap<>();
        response.put("status", "initialised");
        response.put("dynamoTable", "handset-reference");
        response.put("auroraTable", "handset_reference");
        response.put("oracleEnabled", oracleRepo.isPresent());
        if (oracleRepo.isPresent()) response.put("oracleTable", "handset_reference");
        response.put("recordsSeeded", 5);
        response.put("backends", oracleRepo.isPresent()
                ? List.of("dynamo", "aurora", "oracle")
                : List.of("dynamo", "aurora"));
        return ResponseEntity.ok(response);
    }

    /**
     * POST /api/admin/generate?count=1000&batchSize=100&clearFirst=true
     *
     * Generates synthetic records for load/benchmark testing.
     * Runs entirely against local Docker (DynamoDB Local + PostgreSQL) — zero AWS charges.
     * Recommended counts: 100 (quick), 1000 (standard), 5000 (stress).
     *
     * Start Docker first: docker-compose up -d
     * Then run app with local profile: mvn spring-boot:run -Dspring-boot.run.profiles=local
     */
    @PostMapping("/generate")
    public ResponseEntity<DataGeneratorService.GenerationSummary> generate(
            @RequestParam(defaultValue = "1000")  int count,
            @RequestParam(defaultValue = "100")   int batchSize,
            @RequestParam(defaultValue = "true")  boolean clearFirst) {
        DataGeneratorService.GenerationSummary summary =
                dataGeneratorService.generate(count, batchSize, clearFirst);
        return ResponseEntity.ok(summary);
    }

    /**
     * GET /api/admin/data?backend=dynamo|aurora|oracle
     */
    @GetMapping("/data")
    public List<HandsetRecord> listAll(@RequestParam(defaultValue = "dynamo") String backend) {
        return switch (backend.toLowerCase()) {
            case "aurora", "postgres"   -> auroraRepo.findAll();
            case "oracle", "rds-oracle" -> oracleRepo.map(HandsetRepository::findAll)
                    .orElseThrow(() -> new IllegalStateException("Oracle not enabled"));
            default                     -> dynamoRepo.findAll();
        };
    }

    /**
     * DELETE /api/admin/data?backend=dynamo|aurora|oracle|all
     */
    @DeleteMapping("/data")
    public ResponseEntity<Map<String, String>> clearAll(
            @RequestParam(defaultValue = "dynamo") String backend) {
        if ("all".equalsIgnoreCase(backend)) {
            dynamoRepo.deleteAll();
            auroraRepo.deleteAll();
            oracleRepo.ifPresent(HandsetRepository::deleteAll);
            return ResponseEntity.ok(Map.of("status", "cleared", "backend", "all"));
        }
        switch (backend.toLowerCase()) {
            case "aurora", "postgres"   -> auroraRepo.deleteAll();
            case "oracle", "rds-oracle" -> oracleRepo.orElseThrow().deleteAll();
            default                     -> dynamoRepo.deleteAll();
        }
        return ResponseEntity.ok(Map.of("status", "cleared", "backend", backend));
    }
}
