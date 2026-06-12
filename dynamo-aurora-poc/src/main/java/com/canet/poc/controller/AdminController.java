package com.canet.poc.controller;

import com.canet.poc.model.HandsetRecord;
import com.canet.poc.repository.HandsetRepository;
import com.canet.poc.repository.dynamo.DynamoHandsetRepository;
import com.canet.poc.service.SeedDataService;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/admin")
@RequiredArgsConstructor
public class AdminController {

    private final SeedDataService seedDataService;
    private final DynamoHandsetRepository dynamoRepo;

    @Qualifier("auroraHandsetRepository")
    private final HandsetRepository auroraRepo;

    /**
     * POST /api/admin/init
     * Creates DynamoDB table and Aurora schema (if not exists) and seeds sample data.
     * Run once on first deployment.
     */
    @PostMapping("/init")
    public ResponseEntity<Map<String, Object>> init() {
        dynamoRepo.createTableIfNotExists();
        seedDataService.seedAll();
        return ResponseEntity.ok(Map.of(
                "status", "initialised",
                "dynamoTable", "handset-reference",
                "auroraTable", "handset_reference",
                "recordsSeeded", 5
        ));
    }

    /**
     * GET /api/admin/data?backend=dynamo|aurora
     * List all seeded records from the chosen backend.
     */
    @GetMapping("/data")
    public List<HandsetRecord> listAll(
            @RequestParam(defaultValue = "dynamo") String backend) {
        if ("aurora".equalsIgnoreCase(backend)) {
            return auroraRepo.findAll();
        }
        return dynamoRepo.findAll();
    }

    /**
     * DELETE /api/admin/data?backend=dynamo|aurora
     * Clear all records (for test reset).
     */
    @DeleteMapping("/data")
    public ResponseEntity<Map<String, String>> clearAll(
            @RequestParam(defaultValue = "dynamo") String backend) {
        if ("aurora".equalsIgnoreCase(backend)) {
            auroraRepo.deleteAll();
        } else {
            dynamoRepo.deleteAll();
        }
        return ResponseEntity.ok(Map.of("status", "cleared", "backend", backend));
    }
}
