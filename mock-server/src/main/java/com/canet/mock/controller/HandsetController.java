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
 * Both /handsets and /handsetdetails path prefixes are supported so the
 * main canet-app can be pointed at either URL pattern.
 *
 * Endpoints
 * ─────────
 *   GET /handsetdetails                        – all records
 *   GET /handsetdetails?manufacturer=Samsung   – case-insensitive filter
 *   GET /handsetdetails/{tac}                  – single record; 404 if not found
 *
 *   GET /handsets                              – same data, alternate path
 *   GET /handsets?manufacturer=Samsung
 *   GET /handsets/{tac}
 *
 * Dev profile usage (main canet-app application.yml):
 *   external.api.base-url: http://localhost:8081/handsetdetails
 */
@RestController
public class HandsetController {

    private final HandsetRepository repository;

    public HandsetController(HandsetRepository repository) {
        this.repository = repository;
    }

    // ── /handsetdetails ───────────────────────────────────────────────────────

    @GetMapping("/handsetdetails")
    public ResponseEntity<List<HandsetRecord>> listByPath(
            @RequestParam(required = false) String manufacturer) {
        return buildList(manufacturer);
    }

    @GetMapping("/handsetdetails/{tac}")
    public ResponseEntity<HandsetRecord> byTacDetailPath(@PathVariable String tac) {
        return byTac(tac);
    }

    // ── /handsets (alternate path kept for compatibility) ─────────────────────

    @GetMapping("/handsets")
    public ResponseEntity<List<HandsetRecord>> list(
            @RequestParam(required = false) String manufacturer) {
        return buildList(manufacturer);
    }

    @GetMapping("/handsets/{tac}")
    public ResponseEntity<HandsetRecord> byTacHandsetsPath(@PathVariable String tac) {
        return byTac(tac);
    }

    // ── shared logic ──────────────────────────────────────────────────────────

    private ResponseEntity<List<HandsetRecord>> buildList(String manufacturer) {
        List<HandsetRecord> result = (manufacturer == null || manufacturer.isBlank())
                ? repository.findAll()
                : repository.findByManufacturer(manufacturer);
        return ResponseEntity.ok(result);
    }

    private ResponseEntity<HandsetRecord> byTac(String tac) {
        return repository.findByTac(tac)
                .map(ResponseEntity::ok)
                .orElse(ResponseEntity.notFound().build());
    }
}
