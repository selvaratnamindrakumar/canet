package com.canet.udplistener.controller;

import com.canet.udplistener.entity.CapturedMessage;
import com.canet.udplistener.repository.CapturedMessageRepository;
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
 * Validator REST surface.
 *
 * GET  /api/validator/health     — liveness check; open in any browser to confirm the
 *                                  service is reachable before testing other endpoints.
 *
 * POST /api/validator/record     — Generator registers a captured message hash.
 *
 * GET  /api/validator/exists     — Diosma middle tier checks whether a hash has been
 *                                  registered within the configured time window, allowing
 *                                  it to decide whether traffic may cross the high/low boundary.
 *
 * Hash check uses a configurable time window (dedup.window.seconds) so the query is
 * always bounded: SELECT COUNT(*) FROM captured_message WHERE hash_value = ?
 *                  AND received_at > ?
 * rather than scanning all historical records.
 */
@Slf4j
@RestController
@RequestMapping("/api/validator")
@RequiredArgsConstructor
public class ValidatorController {

    private final CapturedMessageRepository repository;

    @Value("${dedup.window.seconds:3600}")
    private long dedupWindowSeconds;

    // ─── Health ──────────────────────────────────────────────────────────────

    /**
     * Open http://host:port/api/validator/health in a browser to confirm the
     * validator is running and reachable.  No parameters required.
     */
    @GetMapping("/health")
    public ResponseEntity<String> health() {
        return ResponseEntity.ok("Validator OK — window=" + dedupWindowSeconds + "s");
    }

    // ─── Register ────────────────────────────────────────────────────────────

    /**
     * Register a captured message with the validator.
     *
     * Request body (JSON):
     * {
     *   "hashValue":      "d41d8cd98f00b204e9800998ecf8427e",  // MD5 hex, 32 chars
     *   "ggasId":         "uuid",
     *   "dstIp":          "10.0.0.2",
     *   "dstPort":        5000,
     *   "sequenceNumber": 42,
     *   "receivedAt":     "2026-07-15T10:00:00Z"
     * }
     *
     * 201 Created  — registered successfully.
     * 409 Conflict — same hash already registered within the time window.
     */
    @PostMapping("/record")
    public ResponseEntity<Map<String, Object>> registerRecord(
            @RequestBody ValidatorRecordRequest request) {

        log.debug("registerRecord hash={} dstPort={}", request.hashValue(), request.dstPort());

        try {
            Instant cutoff = Instant.now().minusSeconds(dedupWindowSeconds);

            if (repository.existsByHashValueAndReceivedAtAfter(request.hashValue(), cutoff)) {
                log.warn("Duplicate within window: hash={}", request.hashValue());
                return ResponseEntity.status(HttpStatus.CONFLICT).body(Map.of(
                        "error", "Hash already registered within the last " + dedupWindowSeconds + "s"
                ));
            }

            Instant rcvAt = request.receivedAt() != null ? request.receivedAt() : Instant.now();
            long seq = request.sequenceNumber() != null ? request.sequenceNumber() : 0L;
            String id = request.ggasId() != null ? request.ggasId() : UUID.randomUUID().toString();

            CapturedMessage saved = repository.save(CapturedMessage.builder()
                    .sequenceNumber(seq)
                    .receivedAt(rcvAt)
                    .hashValue(request.hashValue())
                    .ggasId(id)
                    .name(id)
                    .dstIp(request.dstIp())
                    .dstPort(request.dstPort())
                    .build());

            return ResponseEntity.status(HttpStatus.CREATED).body(Map.of(
                    "id",             saved.getId(),
                    "sequenceNumber", saved.getSequenceNumber(),
                    "receivedAt",     saved.getReceivedAt().toString()
            ));

        } catch (DataIntegrityViolationException e) {
            log.warn("Constraint violation on registerRecord hash={}", request.hashValue());
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

    // ─── Exists ──────────────────────────────────────────────────────────────

    /**
     * Check whether a hash has been registered within the configured time window.
     *
     * Used by the Diosma middle tier to decide whether traffic may pass from
     * high to low (or low to high).
     *
     * Query parameters:
     *   hash — MD5 hex string (required)
     *
     * Response (200 OK):
     *   { "exists": false }
     *   { "exists": true, "sequenceNumber": 42, "receivedAt": "...", "ggasId": "..." }
     *
     * The sequenceNumber and receivedAt in the response reflect the values stamped at
     * packet capture time, so Diosma can detect out-of-order delivery if needed.
     *
     * Window is controlled by dedup.window.seconds (default 3600 = 1 hour).
     * To test in a browser: http://host:port/api/validator/exists?hash=d41d8cd98f00b204e9800998ecf8427e
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

    // ─── DTO ─────────────────────────────────────────────────────────────────

    public record ValidatorRecordRequest(
            String hashValue,
            String ggasId,
            String dstIp,
            Integer dstPort,
            Long sequenceNumber,
            Instant receivedAt
    ) {}
}
