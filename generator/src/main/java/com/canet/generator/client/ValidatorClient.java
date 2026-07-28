package com.canet.generator.client;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.*;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestTemplate;

import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * HTTP client that calls the validator service.
 *
 *   POST /api/validator/create — register a captured packet hash
 *     Headers: OUTBOUND_FILE_HASH, OUTBOUND_FILE_ID, OUTBOUND_FILE_TIME, OUTBOUND_FILE_NAME
 *     Body:    { sourceIp, sourcePort, payload }
 *
 *   POST /api/validator/racs   — check whether a hash is present
 *     Header:  hash.value
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class ValidatorClient {

    private final RestTemplate restTemplate;

    @Value("${validator.base-url:http://localhost:8080}")
    private String validatorBaseUrl;

    public enum RegistrationResult { CREATED, ERROR }

    /**
     * Register a captured packet with the validator via POST /api/validator/create.
     *
     * Outbound headers carry the identity fields; the JSON body carries
     * network metadata (sourceIp, sourcePort, sanitised payload).
     *
     * @return CREATED — validator accepted the record (HTTP 201)
     *         ERROR   — any other outcome
     */
    public RegistrationResult create(String  hash,
                                     String  uuid,
                                     Instant receivedAt,
                                     String  fileName,
                                     String  srcIp,
                                     int     srcPort,
                                     String  payload) {
        try {
            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);
            headers.set("OUTBOUND_FILE_HASH", hash);
            headers.set("OUTBOUND_FILE_ID",   uuid);
            headers.set("OUTBOUND_FILE_TIME", receivedAt.toString());
            if (fileName != null && !fileName.isBlank()) {
                headers.set("OUTBOUND_FILE_NAME", fileName);
            }

            Map<String, Object> body = new LinkedHashMap<>();
            body.put("sourceIp",   srcIp != null ? srcIp : "");
            body.put("sourcePort", srcPort);
            body.put("payload",    payload != null ? payload : "");

            HttpEntity<Map<String, Object>> request = new HttpEntity<>(body, headers);
            ResponseEntity<Map> response = restTemplate.postForEntity(
                    validatorBaseUrl + "/api/validator/create",
                    request,
                    Map.class
            );

            return response.getStatusCode() == HttpStatus.CREATED
                    ? RegistrationResult.CREATED
                    : RegistrationResult.ERROR;

        } catch (Exception e) {
            log.error("Validator /create failed hash={} src={}:{}: {}", hash, srcIp, srcPort, e.getMessage());
            return RegistrationResult.ERROR;
        }
    }

    /**
     * Check whether a hash exists in the validator via POST /api/validator/racs.
     * hash.value is sent as an HTTP header.
     *
     * @return true if the validator returns 200 (found); false for 204 or any error
     */
    public boolean racs(String hash) {
        try {
            HttpHeaders headers = new HttpHeaders();
            headers.set("hash.value", hash);
            HttpEntity<Void> request = new HttpEntity<>(headers);
            ResponseEntity<Map> response = restTemplate.exchange(
                    validatorBaseUrl + "/api/validator/racs",
                    HttpMethod.POST,
                    request,
                    Map.class
            );
            return response.getStatusCode() == HttpStatus.OK;
        } catch (Exception e) {
            log.error("Validator /racs failed hash={}: {}", hash, e.getMessage());
            return false;
        }
    }
}
