package com.canet.diosma.controller;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.*;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.client.RestTemplate;

import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.Map;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Diosma simulator.
 *
 * POST /api/diosma/receive
 *   Receives payload from the generator after a successful validator /create.
 *   Body: { payload (hex), sourceIp, sourcePort, uuid, arrivalTime }
 *   Headers: OUTBOUND_FILE_NAME, X-Application-Id, X-Source
 *   Recalculates MD5 hash from the hex payload independently.
 *   Calls POST /api/validator/racs with hash.value header to confirm persistence.
 *   Returns { status, hash, uuid, racsResult } to the generator.
 *
 * GET /api/diosma/health  — liveness check
 * GET /api/diosma/stats   — running totals
 */
@Slf4j
@RestController
@RequestMapping("/api/diosma")
@RequiredArgsConstructor
public class DiosmaController {

    private final RestTemplate restTemplate;

    @Value("${validator.base-url:http://localhost:8080}")
    private String validatorBaseUrl;

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

    // ── Receive from generator ────────────────────────────────────────

    /**
     * Receives a POST from the generator after it has successfully registered
     * the hash with the validator.
     *
     * 1. Extracts the hex payload from the body.
     * 2. Decodes hex → bytes → recalculates MD5 independently.
     * 3. POSTs to /api/validator/racs with hash.value header.
     * 4. Returns the verification result to the generator.
     */
    @PostMapping("/receive")
    public ResponseEntity<Map<String, Object>> receive(
            @RequestHeader(value = "OUTBOUND_FILE_NAME", required = false) String fileName,
            @RequestHeader(value = "X-Application-Id",  required = false) String appId,
            @RequestHeader(value = "X-Source",           required = false) String source,
            @RequestBody(required = false) Map<String, Object> body) {

        received.incrementAndGet();

        log.info("── Diosma receive ──────────────────────────────────────");
        log.info("  OUTBOUND_FILE_NAME={} X-Application-Id={} X-Source={}", fileName, appId, source);

        if (body == null || body.isEmpty()) {
            log.warn("  Body is empty — nothing to verify");
            errors.incrementAndGet();
            return ResponseEntity.badRequest().body(Map.of("status", "error", "reason", "empty body"));
        }

        String payloadHex  = String.valueOf(body.getOrDefault("payload",     ""));
        String uuid        = String.valueOf(body.getOrDefault("uuid",        ""));
        String srcIp       = String.valueOf(body.getOrDefault("sourceIp",    ""));
        Object srcPortObj  = body.get("sourcePort");
        String arrivalTime = String.valueOf(body.getOrDefault("arrivalTime", ""));

        log.info("  uuid={} srcIp={} srcPort={} arrivalTime={}", uuid, srcIp, srcPortObj, arrivalTime);
        log.info("  payload length (hex chars)={}", payloadHex.length());

        if (payloadHex.isBlank()) {
            log.warn("  payload is blank — cannot compute hash");
            errors.incrementAndGet();
            return ResponseEntity.badRequest().body(Map.of("status", "error", "reason", "payload missing"));
        }

        // Recalculate MD5 — try hex first, fall back to Base64
        String computedHash;
        try {
            byte[] payloadBytes = decodePayload(payloadHex);
            computedHash = computeMd5(payloadBytes);
            log.info("  Computed hash={}", computedHash);
        } catch (Exception e) {
            log.error("  Failed to decode/hash payload: {}", e.getMessage());
            errors.incrementAndGet();
            return ResponseEntity.badRequest().body(Map.of("status", "error", "reason", "payload decode failed: " + e.getMessage()));
        }

        // Call validator /racs to confirm the hash is in the database
        String racsStatus = callRacs(computedHash);

        return ResponseEntity.ok(Map.of(
                "status",      racsStatus,
                "hash",        computedHash,
                "uuid",        uuid,
                "arrivalTime", arrivalTime,
                "racsResult",  racsStatus
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
                log.info("  /racs response body={}", response.getBody());
                return "confirmed";
            } else {
                notFound.incrementAndGet();
                log.warn("  /racs → {} NOT FOUND  hash={}", response.getStatusCode(), hash);
                return "not-found";
            }

        } catch (org.springframework.web.client.HttpClientErrorException e) {
            errors.incrementAndGet();
            log.error("  /racs error status={} hash={}: {}", e.getStatusCode(), hash, e.getMessage());
            return "error";
        } catch (Exception e) {
            errors.incrementAndGet();
            log.error("  /racs call failed hash={} url={}: {}", hash, url, e.getMessage());
            return "error";
        }
    }

    /**
     * Decodes a payload string to raw bytes.
     * Tries hex first; if the string contains non-hex characters falls back to Base64.
     */
    private byte[] decodePayload(String payload) {
        try {
            byte[] bytes = HexFormat.of().parseHex(payload);
            log.debug("  payload decoded as hex ({} bytes)", bytes.length);
            return bytes;
        } catch (IllegalArgumentException e) {
            log.debug("  hex decode failed ({}), retrying as Base64", e.getMessage());
            byte[] bytes = java.util.Base64.getDecoder().decode(payload);
            log.debug("  payload decoded as Base64 ({} bytes)", bytes.length);
            return bytes;
        }
    }

    private String computeMd5(byte[] data) {
        try {
            MessageDigest digest = MessageDigest.getInstance("MD5");
            return HexFormat.of().formatHex(digest.digest(data));
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("MD5 not available", e);
        }
    }
}
