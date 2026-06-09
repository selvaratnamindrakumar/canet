package com.canet.migration.controller;

import com.canet.migration.service.DataMigrationService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

@RestController
@RequestMapping("/api/admin")
@RequiredArgsConstructor
public class AdminController {

    private final DataMigrationService service;

    /**
     * POST /api/admin/table/init
     * Creates the DynamoDB table if it does not exist.
     * Call once after deployment or on first run against a fresh AWS account.
     */
    @PostMapping("/table/init")
    public ResponseEntity<Map<String, String>> initTable() {
        service.ensureTableExists();
        return ResponseEntity.ok(Map.of("status", "Table ready"));
    }
}
