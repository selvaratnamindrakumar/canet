package com.canet.udplistener.controller;

import com.canet.udplistener.entity.ValidatorRecord;
import com.canet.udplistener.service.ValidatorService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.Instant;
import java.util.Map;
import java.util.Optional;

/**
 * Two-endpoint validator surface.
 *
 * POST /api/validator/record  — Generator calls this to register a captured message.
 * GET  /api/validator/exists  — Diosma middle tier calls this to decide whether to allow
 *                               traffic across the high/low boundary.
 *
 * The EXISTS check is composite (hash + dstIp + dstPort) to close the RACS gap where
 * hash-only checks would pass identical content sent to a different destination.
 */
@Slf4j
@RestController
@RequestMapping("/api/validator")
@RequiredArgsConstructor
public class ValidatorController {

    private final ValidatorService validatorService;

    /**
     * Register a new validated record.
     *
     * Request body (JSON):
     * {
     *   "hashValue":      "abc123...",   // SHA-256 hex, 64 chars
     *   "ggasId":         "uuid",        // GGAS identifier
     *   "srcIp":          "10.0.0.1",
     *   "dstIp":          "10.0.0.2",
     *   "dstPort":        5000,
     *   "sequenceNumber": 42,            // capture-time sequence
     *   "receivedAt":     "2026-07-15T10:00:00Z"  // capture-time instant
     * }
     *
     * Returns 201 Created on success.
     * Returns 409 Conflict if the composite (hash, dstIp, dstPort) already exists.
     */
    @PostMapping("/record")
    public ResponseEntity<Map<String, Object>> registerRecord(
            @RequestBody ValidatorRecordRequest request) {

        log.debug("registerRecord hash={} dstIp={} dstPort={}",
                request.hashValue(), request.dstIp(), request.dstPort());

        try {
            ValidatorRecord saved = validatorService.registerRecord(
                    request.hashValue(),
                    request.ggasId(),
                    request.srcIp(),
                    request.dstIp(),
                    request.dstPort(),
                    request.sequenceNumber(),
                    request.receivedAt() != null ? request.receivedAt() : Instant.now()
            );

            return ResponseEntity.status(HttpStatus.CREATED).body(Map.of(
                    "id",             saved.getId(),
                    "sequenceNumber", saved.getSequenceNumber(),
                    "receivedAt",     saved.getReceivedAt().toString()
            ));

        } catch (DataIntegrityViolationException e) {
            // Unique constraint on (hash_value, dst_ip, dst_port) — already registered.
            log.warn("Duplicate record: hash={} dstIp={} dstPort={}",
                    request.hashValue(), request.dstIp(), request.dstPort());
            return ResponseEntity.status(HttpStatus.CONFLICT).body(Map.of(
                    "error", "Record already exists for this hash+dstIp+dstPort"
            ));
        } catch (Exception e) {
            log.error("registerRecord failed hash={} dstIp={} dstPort={}",
                    request.hashValue(), request.dstIp(), request.dstPort(), e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(Map.of(
                    "error", "Internal error: " + e.getMessage()
            ));
        }
    }

    /**
     * Check whether a validated record exists for the given composite key.
     *
     * Query parameters:
     *   hash    — SHA-256 hex string (required)
     *   dstIp   — destination IP (required)
     *   dstPort — destination port (required)
     *
     * Returns 200 with {"exists": true/false, "sequenceNumber": N, "receivedAt": "..."}
     *
     * When exists=true the sequence number and receivedAt allow Diosma to detect
     * whether messages are being delivered out of order relative to receive order.
     */
    @GetMapping("/exists")
    public ResponseEntity<Map<String, Object>> exists(
            @RequestParam String hash,
            @RequestParam String dstIp,
            @RequestParam int dstPort) {

        log.debug("exists hash={} dstIp={} dstPort={}", hash, dstIp, dstPort);

        // Single composite query: avoids a separate EXISTS round-trip and guarantees
        // the returned sequenceNumber/receivedAt belong to this exact destination.
        Optional<ValidatorRecord> record =
                validatorService.findByCompositeKey(hash, dstIp, dstPort);

        if (record.isEmpty()) {
            return ResponseEntity.ok(Map.of("exists", false));
        }

        ValidatorRecord r = record.get();
        return ResponseEntity.ok(Map.<String, Object>of(
                "exists",         true,
                "sequenceNumber", r.getSequenceNumber(),
                "receivedAt",     r.getReceivedAt().toString(),
                "ggasId",         r.getGgasId() != null ? r.getGgasId() : ""
        ));
    }

    /** Request DTO for the register endpoint. */
    public record ValidatorRecordRequest(
            String hashValue,
            String ggasId,
            String srcIp,
            String dstIp,
            Integer dstPort,
            Long sequenceNumber,
            Instant receivedAt
    ) {}
}
