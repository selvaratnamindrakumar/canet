package com.canet.validator.controller;

import com.canet.validator.entity.CapturedMessage;
import com.canet.validator.repository.CapturedMessageRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.Instant;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

/**
 * Validator REST surface — two functional endpoints plus a health check.
 *
 * POST /api/validator/record
 *   Generator calls this for every captured UDP packet.
 *   The validator applies time-windowed duplicate detection before persisting.
 *
 * GET /api/validator/exists?hash={md5}
 *   Diosma middle tier calls this to decide whether traffic may cross the
 *   high/low boundary.  Returns sequenceNumber and receivedAt so Diosma
 *   can detect out-of-order delivery if required.
 *
 * GET /api/validator/health
 *   Plain-text liveness check — open directly in any browser.
 */
@Slf4j
@RestController
@RequestMapping("/api/validator")
@RequiredArgsConstructor
public class ValidatorController {

    private final CapturedMessageRepository repository;

    /**
     * How far back to look when checking for a duplicate hash.
     * Keeps DB queries bounded regardless of historical data volume.
     * Examples: 60=1min, 3600=1hr (default), 86400=24hr, 604800=7days.
     */
    @Value("${dedup.window.seconds:3600}")
    private long dedupWindowSeconds;

    // ─── Health ──────────────────────────────────────────────────────

    /** Open http://host:8080/api/validator/health in any browser to confirm the service is up. */
    @GetMapping("/health")
    public ResponseEntity<String> health() {
        return ResponseEntity.ok("Validator OK — dedup window=" + dedupWindowSeconds + "s");
    }

    // ─── Register ────────────────────────────────────────────────────

    /**
     * Register a hash sent by the generator.
     *
     * Request body (JSON):
     * {
     *   "hashValue":      "d41d8cd98f00b204e9800998ecf8427e",
     *   "ggasId":         "550e8400-e29b-41d4-a716-446655440000",
     *   "dstIp":          "10.0.0.2",
     *   "dstPort":        5000,
     *   "sequenceNumber": 42,
     *   "receivedAt":     "2026-07-17T10:00:00Z"
     * }
     *
     * 201 Created  — accepted and persisted.
     * 409 Conflict — duplicate hash found within the configured time window.
     * 500           — unexpected server error.
     */
    @PostMapping("/record")
    public ResponseEntity<Map<String, Object>> registerRecord(
            @RequestBody RecordRequest request) {

        log.debug("register hash={} src={}:{} dst={}:{} base64={}",
                request.hashValue(),
                request.srcIp(), request.srcPort(),
                request.dstIp(), request.dstPort(),
                request.payloadBase64() != null);

        try {
            Instant cutoff = Instant.now().minusSeconds(dedupWindowSeconds);

            if (repository.existsByHashValueAndReceivedAtAfter(request.hashValue(), cutoff)) {
                log.debug("Duplicate within {}s window: hash={}", dedupWindowSeconds, request.hashValue());
                return ResponseEntity.status(HttpStatus.CONFLICT).body(Map.of(
                        "error", "Duplicate hash within last " + dedupWindowSeconds + "s"
                ));
            }

            Instant rcvAt = request.receivedAt() != null ? request.receivedAt() : Instant.now();
            long    seq   = request.sequenceNumber() != null ? request.sequenceNumber() : 0L;
            String  id    = request.ggasId() != null ? request.ggasId() : UUID.randomUUID().toString();

            CapturedMessage saved = repository.save(CapturedMessage.builder()
                    .sequenceNumber(seq)
                    .receivedAt(rcvAt)
                    .hashValue(request.hashValue())
                    .ggasId(id)
                    .name(id)
                    .srcIp(request.srcIp())
                    .srcPort(request.srcPort())
                    .dstIp(request.dstIp())
                    .dstPort(request.dstPort())
                    .payloadHex(request.payloadHex())
                    .payloadBase64(request.payloadBase64())
                    .build());

            return ResponseEntity.status(HttpStatus.CREATED).body(Map.of(
                    "id",             saved.getId(),
                    "sequenceNumber", saved.getSequenceNumber(),
                    "receivedAt",     saved.getReceivedAt().toString()
            ));

        } catch (DataIntegrityViolationException e) {
            log.warn("Constraint violation hash={}", request.hashValue());
            return ResponseEntity.status(HttpStatus.CONFLICT).body(Map.of(
                    "error", "Record already exists"
            ));
        } catch (Exception e) {
            log.error("registerRecord failed hash={}", request.hashValue(), e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(Map.of(
                    "error", "Internal error: " + e.getMessage()
            ));
        }
    }

    // ─── Exists ──────────────────────────────────────────────────────

    /**
     * Check whether a hash has been registered within the configured time window.
     *
     * Called by the Diosma middle tier to decide whether to allow traffic
     * across the high/low boundary.
     *
     * Query parameter:
     *   hash — MD5 hex string (required)
     *
     * Response (200 OK):
     *   { "exists": false }
     *   { "exists": true, "sequenceNumber": 42, "receivedAt": "...", "ggasId": "..." }
     *
     * Browser test: http://host:8080/api/validator/exists?hash=d41d8cd98f00b204e9800998ecf8427e
     */
    @GetMapping("/exists")
    public ResponseEntity<Map<String, Object>> exists(@RequestParam String hash) {

        log.debug("exists hash={}", hash);

        Instant cutoff = Instant.now().minusSeconds(dedupWindowSeconds);
        Optional<CapturedMessage> record =
                repository.findFirstByHashValueAndReceivedAtAfter(hash, cutoff);

        if (record.isEmpty()) {
            return ResponseEntity.ok(Map.of("exists", false));
        }

        CapturedMessage r = record.get();
        return ResponseEntity.ok(Map.<String, Object>of(
                "exists",         true,
                "sequenceNumber", r.getSequenceNumber(),
                "receivedAt",     r.getReceivedAt().toString(),
                "ggasId",         r.getGgasId() != null ? r.getGgasId() : ""
        ));
    }

    // ─── DTO ─────────────────────────────────────────────────────────

    public record RecordRequest(
            String  hashValue,
            String  ggasId,
            String  srcIp,
            Integer srcPort,
            String  dstIp,
            Integer dstPort,
            String  payloadHex,
            String  payloadBase64,
            Long    sequenceNumber,
            Instant receivedAt
    ) {}
}
