package com.canet.generator.client;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Component;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.RestTemplate;

import java.time.Instant;
import java.util.Map;

/**
 * HTTP client that calls the validator service.
 *
 * The generator has no direct database access — all persistence and
 * duplicate detection is delegated to the validator via these endpoints:
 *
 *   POST /api/validator/record  — register a captured packet hash
 *   GET  /api/validator/exists  — check whether a hash exists (optional use)
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class ValidatorClient {

    private final RestTemplate restTemplate;

    @Value("${validator.base-url:http://localhost:8080}")
    private String validatorBaseUrl;

    public enum RegistrationResult { CREATED, DUPLICATE, ERROR }

    /**
     * Register a captured packet with the validator.
     *
     * @return CREATED   if the validator accepted the record (HTTP 201)
     *         DUPLICATE if the validator rejected it as a duplicate (HTTP 409)
     *         ERROR     for any other outcome
     */
    public RegistrationResult register(String hash,
                                       String ggasId,
                                       String dstIp,
                                       int dstPort,
                                       long sequenceNumber,
                                       Instant receivedAt) {
        try {
            Map<String, Object> body = Map.of(
                    "hashValue",      hash,
                    "ggasId",         ggasId,
                    "dstIp",          dstIp != null ? dstIp : "",
                    "dstPort",        dstPort,
                    "sequenceNumber", sequenceNumber,
                    "receivedAt",     receivedAt.toString()
            );

            ResponseEntity<Map> response = restTemplate.postForEntity(
                    validatorBaseUrl + "/api/validator/record",
                    body,
                    Map.class
            );

            return response.getStatusCode() == HttpStatus.CREATED
                    ? RegistrationResult.CREATED
                    : RegistrationResult.ERROR;

        } catch (HttpClientErrorException.Conflict e) {
            // 409 — validator's time-window dedup rejected the record
            return RegistrationResult.DUPLICATE;

        } catch (Exception e) {
            log.error("Validator registration failed hash={} dstIp={} dstPort={}: {}",
                    hash, dstIp, dstPort, e.getMessage());
            return RegistrationResult.ERROR;
        }
    }

    /**
     * Check whether a hash exists within the validator's time window.
     * Used for optional pre-flight checks; dedup is enforced server-side.
     */
    public boolean exists(String hash) {
        try {
            String url = validatorBaseUrl + "/api/validator/exists?hash=" + hash;
            ResponseEntity<Map> response = restTemplate.getForEntity(url, Map.class);
            if (response.getBody() == null) return false;
            return Boolean.TRUE.equals(response.getBody().get("exists"));
        } catch (Exception e) {
            log.error("Validator exists check failed hash={}: {}", hash, e.getMessage());
            return false;
        }
    }
}
