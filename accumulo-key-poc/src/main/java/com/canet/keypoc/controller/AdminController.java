package com.canet.keypoc.controller;

import com.canet.keypoc.repository.dynamo.DynamoCellStoreRepository;
import com.canet.keypoc.seed.SeedDataService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

import java.util.Map;

@RestController
@RequestMapping("/api/admin")
@RequiredArgsConstructor
public class AdminController {

    private final DynamoCellStoreRepository dynamoRepo;
    private final SeedDataService seedService;

    /**
     * POST /api/admin/init
     * Creates DynamoDB table, runs Flyway (Aurora schema), seeds 5 sample CGIs.
     */
    @PostMapping("/init")
    public ResponseEntity<Map<String, Object>> init() {
        dynamoRepo.createTableIfNotExists();
        seedService.seedAll();
        return ResponseEntity.ok(Map.of(
                "status", "ready",
                "dynamo", "handset-cells (PK=rowId, SK=family#qualifier#timestamp)",
                "aurora", "handset_cells (cgi, family, qualifier, ts, value)",
                "cgisSeeded", List.of("CGI12345", "CGI67890", "CGI11111", "CGI22222", "CGI33333"),
                "families", List.of("DEVICE", "LOCATION", "ENRICHMENT"),
                "hint", "Try GET /api/report for the full evaluation"
        ));
    }

    @DeleteMapping("/data")
    public ResponseEntity<Map<String, String>> clear() {
        seedService.clearAll();
        return ResponseEntity.ok(Map.of("status", "cleared"));
    }

    @PostMapping("/reseed")
    public ResponseEntity<Map<String, String>> reseed() {
        seedService.clearAll();
        seedService.seedAll();
        return ResponseEntity.ok(Map.of("status", "reseeded"));
    }

}
