package com.canet.diosma.controller;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.*;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.client.RestTemplate;

import java.util.Map;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Diosma simulator.
 *
 * POST /api/diosma/notify
 *   Receives validator notifications: { fileHash, uuid, arrivalTime }
 *   Immediately calls back to POST /api/validator/racs with hash.value header.
 *   Logs whether the hash was confirmed present (200) or not found (204).
 *   Always returns 200 to the validator so it does not retry.
 *
 * GET /api/diosma/health
 *   Liveness check.
 *
 * GET /api/diosma/stats
 *   Running totals: received, confirmed, notFound, errors.
 */
@Slf4j
@RestController
@RequestMapping("/api/diosma")
@RequiredArgsConstructor
public class DiosmaController {

    private final RestTemplate restTemplate;

    @Value("${validator.base-url:https://localhost:8443}")
    private String validatorBaseUrl;

    // ── Counters ─────────────────────────────────────────────────────
    private final AtomicLong received  = new AtomicLong();
    private final AtomicLong confirmed = new AtomicLong();
    private final AtomicLong notFound  = new AtomicLong();
    private final AtomicLong errors    = new AtomicLong();

    // ── Health ───────────────────────────────────────────────────────

    @GetMapping("/health")
    public ResponseEntity<String> health() {
        return ResponseEntity.ok("Diosma simulator OK");
    }

    // ── Stats ────────────────────────────────────────────────────────

    @GetMapping("/stats")
    public ResponseEntity<Map<String, Object>> stats() {
        return ResponseEntity.ok(Map.of(
                "received",  received.get(),
                "confirmed", confirmed.get(),
                "notFound",  notFound.get(),
                "errors",    errors.get()
        ));
    }

    // ── Notify ───────────────────────────────────────────────────────

    /**
     * Receives a notification from the validator after every successful DB insert.
     *
     * Expected JSON body from validator:
     *   { "fileHash": "...", "uuid": "...", "arrivalTime": "..." }
     *
     * Calls back to POST /api/validator/racs with header hash.value to confirm.
     */
    @PostMapping("/notify")
    public ResponseEntity<Map<String, Object>> notify(
            @RequestBody(required = false) Map<String, Object> body,
            @RequestHeader Map<String, String> incomingHeaders) {

        received.incrementAndGet();

        // Log all incoming headers for diagnostics
        log.info("── Diosma notify received ──────────────────────────────");
        incomingHeaders.forEach((k, v) -> log.info("  Header: {}={}", k, v));

        if (body == null || body.isEmpty()) {
            log.warn("  Body is empty — nothing to verify");
            errors.incrementAndGet();
            return ResponseEntity.ok(Map.of("status", "error", "reason", "empty body"));
        }

        String fileHash    = String.valueOf(body.getOrDefault("fileHash",    ""));
        String uuid        = String.valueOf(body.getOrDefault("uuid",        ""));
        String arrivalTime = String.valueOf(body.getOrDefault("arrivalTime", ""));

        log.info("  fileHash={}  uuid={}  arrivalTime={}", fileHash, uuid, arrivalTime);

        if (fileHash.isBlank()) {
            log.warn("  fileHash is blank — cannot call /racs");
            errors.incrementAndGet();
            return ResponseEntity.ok(Map.of("status", "error", "reason", "fileHash missing"));
        }

        // Call back to validator /racs to confirm hash is persisted
        String racsResult = callRacs(fileHash);
        return ResponseEntity.ok(Map.of(
                "status",      racsResult,
                "fileHash",    fileHash,
                "uuid",        uuid,
                "arrivalTime", arrivalTime
        ));
    }

    // ── Internal ─────────────────────────────────────────────────────

    private String callRacs(String hash) {
        String url = validatorBaseUrl + "/api/validator/racs";
        try {
            HttpHeaders headers = new HttpHeaders();
            headers.set("hash.value", hash);
            HttpEntity<Void> request = new HttpEntity<>(headers);

            ResponseEntity<Map> response = restTemplate.exchange(
                    url, HttpMethod.POST, request, Map.class);

            if (response.getStatusCode() == HttpStatus.OK) {
                confirmed.incrementAndGet();
                log.info("  /racs → 200 CONFIRMED  hash={}", hash);
                log.info("  /racs response: {}", response.getBody());
                return "confirmed";
            } else {
                notFound.incrementAndGet();
                log.warn("  /racs → {} NOT FOUND  hash={}", response.getStatusCode(), hash);
                return "not-found";
            }

        } catch (org.springframework.web.client.HttpClientErrorException e) {
            // 204 No Content — RestTemplate throws for non-2xx but 204 is 2xx;
            // handle any unexpected client error
            if (e.getStatusCode() == HttpStatus.NO_CONTENT) {
                notFound.incrementAndGet();
                log.warn("  /racs → 204 NOT FOUND  hash={}", hash);
                return "not-found";
            }
            errors.incrementAndGet();
            log.error("  /racs client error status={} hash={}: {}", e.getStatusCode(), hash, e.getMessage());
            return "error";
        } catch (Exception e) {
            errors.incrementAndGet();
            log.error("  /racs call failed hash={} url={}: {}", hash, url, e.getMessage());
            return "error";
        }
    }
}
