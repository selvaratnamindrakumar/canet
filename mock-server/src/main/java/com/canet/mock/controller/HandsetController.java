package com.canet.mock.controller;

import com.canet.mock.data.HandsetRepository;
import com.canet.mock.model.HandsetRecord;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * Simulates a real handset-details API.
 *
 * Endpoints
 * ─────────
 *   GET /handsets                          – all records
 *   GET /handsets?manufacturer=Samsung     – filter by manufacturer (case-insensitive)
 *   GET /handsets/{tac}                    – single record by TAC
 *
 * Point the main canet-app at this server via application-dev.yml:
 *   external.api.base-url: http://localhost:8081/handsets
 */
@RestController
@RequestMapping("/handsets")
public class HandsetController {

    private final HandsetRepository repository;

    public HandsetController(HandsetRepository repository) {
        this.repository = repository;
    }

    @GetMapping
    public ResponseEntity<List<HandsetRecord>> list(
            @RequestParam(required = false) String manufacturer) {

        List<HandsetRecord> result = (manufacturer == null || manufacturer.isBlank())
                ? repository.findAll()
                : repository.findByManufacturer(manufacturer);

        return ResponseEntity.ok(result);
    }

    @GetMapping("/{tac}")
    public ResponseEntity<HandsetRecord> byTac(@PathVariable String tac) {
        return repository.findByTac(tac)
                .map(ResponseEntity::ok)
                .orElse(ResponseEntity.notFound().build());
    }
}
