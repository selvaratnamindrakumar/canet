package com.canet.generator.client;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.*;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestTemplate;

import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Posts captured packet data to the Diosma endpoint after the validator
 * has successfully stored the hash (HTTP 201).
 *
 * Diosma receives the payload and independently recalculates the MD5 hash
 * to verify it matches what the validator stored.
 *
 * Disable by leaving diosma.base-url blank in application.properties.
 */
@Slf4j
@Component
public class DiosmaClient {

    private final RestTemplate restTemplate;

    @Value("${diosma.base-url:}")
    private String diosmaBaseUrl;

    @Value("${diosma.notify-path:/api/diosma/receive}")
    private String notifyPath;

    // Configurable headers sent with every Diosma POST
    @Value("${diosma.headers.X-Application-Id:canet-generator}")
    private String appId;

    @Value("${diosma.headers.X-Source:canet}")
    private String source;

    public DiosmaClient(RestTemplate restTemplate) {
        this.restTemplate = restTemplate;
    }

    /**
     * POST the captured payload to Diosma.
     *
     * JSON body:
     * {
     *   "payload":     "<hex-encoded bytes>",
     *   "sourceIp":    "192.168.1.10",
     *   "sourcePort":  5000,
     *   "uuid":        "550e8400-...",
     *   "arrivalTime": "2026-07-28T10:00:00Z"
     * }
     *
     * Headers:
     *   Content-Type:     application/json
     *   OUTBOUND_FILE_NAME: <uuid>
     *   X-Application-Id:   canet-generator
     *   X-Source:           canet
     *
     * @return the Diosma response body as a string (logged by caller)
     */
    public String postPayload(String payload,
                              String uuid,
                              String srcIp,
                              int    srcPort,
                              Instant arrivalTime) {
        if (diosmaBaseUrl == null || diosmaBaseUrl.isBlank()) {
            log.debug("Diosma disabled — diosma.base-url not set");
            return null;
        }

        String url = diosmaBaseUrl + notifyPath;
        try {
            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);
            headers.set("OUTBOUND_FILE_NAME",  uuid);
            headers.set("X-Application-Id",    appId);
            headers.set("X-Source",             source);

            Map<String, Object> body = new LinkedHashMap<>();
            body.put("payload",     payload);
            body.put("sourceIp",    srcIp != null ? srcIp : "");
            body.put("sourcePort",  srcPort);
            body.put("uuid",        uuid);
            body.put("arrivalTime", arrivalTime.toString());

            HttpEntity<Map<String, Object>> request = new HttpEntity<>(body, headers);

            ResponseEntity<String> response = restTemplate.postForEntity(url, request, String.class);

            log.info("Diosma POST uuid={} status={} response={}",
                    uuid, response.getStatusCode().value(), response.getBody());

            return response.getBody();

        } catch (Exception e) {
            log.error("Diosma POST failed uuid={} url={}: {}", uuid, url, e.getMessage());
            return null;
        }
    }
}
