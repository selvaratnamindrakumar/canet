package com.canet.validator.controller;

import com.canet.validator.client.DiosmaClient;
import com.canet.validator.db.DatabaseGateway;
import com.canet.validator.entity.CapturedMessage;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.Instant;
import java.time.format.DateTimeParseException;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

/**
 * Validator REST surface.
 *
 * POST /api/validator/create
 *   Generator registers every captured UDP packet.
 *   Required headers: OUTBOUND_FILE_HASH, OUTBOUND_FILE_ID
 *   Optional headers: OUTBOUND_FILE_TIME, OUTBOUND_FILE_NAME
 *   JSON body (logged, not persisted): sourceIp, sourcePort, payload
 *   Returns 201 Created on success.
 *
 * POST /api/validator/racs
 *   Middle tier checks whether a hash is present in the database.
 *   Required header: hash.value
 *   200 — hash found       (body: hash, uuid, arrivalTime)
 *   204 — hash not found   (no body)
 *   400 — hash.value header missing or blank
 *   500 — unexpected error
 *
 * GET /api/validator/health
 *   Plain-text liveness check.
 */
@Slf4j
@RestController
@RequestMapping("/api/validator")
@RequiredArgsConstructor
public class ValidatorController {

    private final DatabaseGateway database;
    private final DiosmaClient diosmaClient;

    // ─── Health ──────────────────────────────────────────────────────

    @GetMapping("/health")
    public ResponseEntity<String> health() {
        return ResponseEntity.ok("Validator OK");
    }

    // ─── Create ──────────────────────────────────────────────────────

    /**
     * Persist a captured packet hash.
     *
     * Headers read:
     *   OUTBOUND_FILE_HASH  — MD5 hex of the payload (required)
     *   OUTBOUND_FILE_ID    — UUID assigned by the generator (optional; generated if absent)
     *   OUTBOUND_FILE_TIME  — ISO-8601 capture instant (optional; defaults to now)
     *   OUTBOUND_FILE_NAME  — original filename / label (optional; logged only)
     *
     * JSON body fields (optional; logged but not stored in DB):
     *   sourceIp, sourcePort, payload (sanitised by generator)
     *
     * 201 Created — row inserted; body: { id, uuid, arrivalTime }
     * 400         — OUTBOUND_FILE_HASH header missing or blank
     * 500         — unexpected error
     */
    @PostMapping("/create")
    public ResponseEntity<Map<String, Object>> create(
            @RequestHeader(value = "OUTBOUND_FILE_HASH",  required = false) String fileHash,
            @RequestHeader(value = "OUTBOUND_FILE_ID",    required = false) String fileId,
            @RequestHeader(value = "OUTBOUND_FILE_TIME",  required = false) String fileTime,
            @RequestHeader(value = "OUTBOUND_FILE_NAME",  required = false) String fileName,
            @RequestBody(required = false) CreateRequest body) {

        if (fileHash == null || fileHash.isBlank()) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(Map.of(
                    "error", "OUTBOUND_FILE_HASH header is required"));
        }

        log.debug("create hash={} id={} time={} name={}", fileHash, fileId, fileTime, fileName);
        if (body != null) {
            log.debug("create body srcIp={} srcPort={} payloadLength={}",
                    body.sourceIp(), body.sourcePort(),
                    body.payload() != null ? body.payload().length() : 0);
        }

        try {
            Instant arrivalTime = parseInstant(fileTime);
            String uuid = (fileId != null && !fileId.isBlank()) ? fileId : UUID.randomUUID().toString();

            CapturedMessage saved = database.save(CapturedMessage.builder()
                    .fileHash(fileHash)
                    .uuid(uuid)
                    .arrivalTime(arrivalTime)
                    .build());

            diosmaClient.notify(saved);

            return ResponseEntity.status(HttpStatus.CREATED).body(Map.of(
                    "id",          saved.getId(),
                    "uuid",        saved.getUuid(),
                    "arrivalTime", saved.getArrivalTime().toString()
            ));

        } catch (Exception e) {
            log.error("create failed hash={}", fileHash, e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(Map.of(
                    "error", "Internal error: " + e.getMessage()));
        }
    }

    // ─── RACS ────────────────────────────────────────────────────────

    /**
     * Check whether a file hash is present in the database.
     *
     * POST /api/validator/racs
     * Required header: hash.value
     *
     * 200 — found:     { "hash": "...", "uuid": "...", "arrivalTime": "..." }
     * 204 — not found: (no body)
     * 400 — hash.value header missing or blank
     * 500 — unexpected error
     */
    @PostMapping("/racs")
    public ResponseEntity<Map<String, Object>> racs(
            @RequestHeader(value = "hash.value", required = false) String hashValue) {

        if (hashValue == null || hashValue.isBlank()) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(Map.of(
                    "error", "hash.value header is required"));
        }

        log.debug("racs hash={}", hashValue);

        try {
            Optional<CapturedMessage> record = database.findLatestByHash(hashValue);

            if (record.isEmpty()) {
                return ResponseEntity.noContent().build(); // 204
            }

            CapturedMessage r = record.get();
            return ResponseEntity.ok(Map.of(
                    "hash",        r.getFileHash(),
                    "uuid",        r.getUuid() != null ? r.getUuid() : "",
                    "arrivalTime", r.getArrivalTime().toString()
            ));

        } catch (Exception e) {
            log.error("racs check failed hash={}", hashValue, e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(Map.of(
                    "error", "Internal error: " + e.getMessage()));
        }
    }

    // ─── Helpers ─────────────────────────────────────────────────────

    private Instant parseInstant(String value) {
        if (value == null || value.isBlank()) return Instant.now();
        try {
            return Instant.parse(value);
        } catch (DateTimeParseException e) {
            log.warn("OUTBOUND_FILE_TIME '{}' is not a valid ISO-8601 instant — using now", value);
            return Instant.now();
        }
    }

    // ─── DTOs ────────────────────────────────────────────────────────

    /**
     * Optional JSON body for /create.
     * Fields are logged for diagnostics; none are stored in the database.
     * Unknown extra fields are ignored.
     */
    public record CreateRequest(
            String  sourceIp,
            Integer sourcePort,
            String  payload
    ) {}
}
