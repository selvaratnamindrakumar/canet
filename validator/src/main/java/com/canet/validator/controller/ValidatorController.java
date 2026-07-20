package com.canet.validator.controller;

import com.canet.validator.client.DiosmaClient;
import com.canet.validator.entity.CapturedMessage;
import com.canet.validator.repository.CapturedMessageRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
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
 * POST /api/validator/record
 *   Generator registers every captured UDP packet.
 *   Duplicate hashes are accepted — different payloads can produce the
 *   same MD5 hash.  Each call inserts a new row; MySQL assigns the id.
 *   The arrival time stored is the capture-time instant from the generator,
 *   not the current server time.
 *   Returns 201 Created on success.
 *
 * GET /api/validator/exists?hash={md5}
 *   Middle tier checks whether a hash is present in the database.
 *   200 — hash found       (body contains uuid and arrivalTime)
 *   204 — hash not found   (no body)
 *   404 — hash param missing or blank
 *   500 — unexpected error
 *
 * GET /api/validator/health
 *   Plain-text liveness check — open in any browser.
 */
@Slf4j
@RestController
@RequestMapping("/api/validator")
@RequiredArgsConstructor
public class ValidatorController {

    private final CapturedMessageRepository repository;
    private final DiosmaClient diosmaClient;

    // ─── Health ──────────────────────────────────────────────────────

    @GetMapping("/health")
    public ResponseEntity<String> health() {
        return ResponseEntity.ok("Validator OK");
    }

    // ─── Register ────────────────────────────────────────────────────

    /**
     * Persist a captured packet hash.  Duplicates are allowed.
     *
     * Minimal required field: hashValue.
     * ggasId defaults to a fresh UUID when absent.
     * receivedAt defaults to the current instant when absent (generator
     * should always supply it so the stored time reflects true arrival).
     *
     * 201 Created — row inserted; body: { id, uuid, arrivalTime }
     * 500         — unexpected error
     */
    @PostMapping("/record")
    public ResponseEntity<Map<String, Object>> registerRecord(
            @RequestBody RecordRequest request) {

        log.debug("record hash={} uuid={}", request.hashValue(), request.ggasId());

        try {
            Instant arrivalTime = request.receivedAt() != null
                    ? request.receivedAt()
                    : Instant.now();

            String uuid = (request.ggasId() != null && !request.ggasId().isBlank())
                    ? request.ggasId()
                    : UUID.randomUUID().toString();

            CapturedMessage saved = repository.save(CapturedMessage.builder()
                    .fileHash(request.hashValue())
                    .uuid(uuid)
                    .arrivalTime(arrivalTime)
                    .build());

            // Notify Diosma asynchronously — does not block this response.
            diosmaClient.notify(saved);

            return ResponseEntity.status(HttpStatus.CREATED).body(Map.of(
                    "id",          saved.getId(),
                    "uuid",        saved.getUuid(),
                    "arrivalTime", saved.getArrivalTime().toString()
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
     * Check whether a file hash is present in the database.
     *
     * GET /api/validator/exists?hash={md5}
     *
     * 200 — found:     { "hash": "...", "uuid": "...", "arrivalTime": "..." }
     * 204 — not found: (no body)
     * 404 — hash parameter missing or blank
     * 500 — unexpected error
     *
     * Browser test:
     *   http://host:8080/api/validator/exists?hash=d41d8cd98f00b204e9800998ecf8427e
     */
    @GetMapping("/exists")
    public ResponseEntity<Map<String, Object>> exists(
            @RequestParam(required = false) String hash) {

        if (hash == null || hash.isBlank()) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body(Map.of(
                    "error", "hash parameter is required"
            ));
        }

        log.debug("exists hash={}", hash);

        try {
            Optional<CapturedMessage> record =
                    repository.findFirstByFileHashOrderByIdDesc(hash);

            if (record.isEmpty()) {
                // 204 No Content — hash not in database
                return ResponseEntity.noContent().build();
            }

            CapturedMessage r = record.get();
            return ResponseEntity.ok(Map.of(
                    "hash",        r.getFileHash(),
                    "uuid",        r.getUuid() != null ? r.getUuid() : "",
                    "arrivalTime", r.getArrivalTime().toString()
            ));

        } catch (Exception e) {
            log.error("exists check failed hash={}", hash, e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(Map.of(
                    "error", "Internal error: " + e.getMessage()
            ));
        }
    }

    // ─── DTO ─────────────────────────────────────────────────────────

    /**
     * All fields are optional at the HTTP level — only hashValue is meaningful.
     * Extra fields sent by the generator (srcIp, dstPort, payloadHex, etc.)
     * are accepted and silently ignored so the generator does not need to be
     * changed if the schema evolves.
     */
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
