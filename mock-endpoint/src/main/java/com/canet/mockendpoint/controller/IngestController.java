package com.canet.mockendpoint.controller;

import java.time.Instant;
import java.util.Collections;
import java.util.Enumeration;
import java.util.LinkedHashMap;
import java.util.Map;

import jakarta.servlet.http.HttpServletRequest;
import lombok.extern.slf4j.Slf4j;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RestController;

/**
 * Simulated ingest endpoint.
 *
 * POST /ingest  – accepts any payload, logs X-Feed / X-Environment and
 *                 the raw byte count, returns a JSON acknowledgement.
 * GET  /ping    – lightweight liveness check (in addition to Actuator).
 */
@Slf4j
@RestController
public class IngestController {

    @PostMapping("/ingest")
    public ResponseEntity<Map<String, Object>> ingest(
            @RequestHeader(value = "X-Feed",        required = false) String feed,
            @RequestHeader(value = "X-Environment", required = false) String environment,
            @RequestBody(required = false) byte[] body,
            HttpServletRequest request) {

        int bytes = body != null ? body.length : 0;

        // Log every request header for test visibility
        log.info("=== INGEST REQUEST ===");
        log.info("  Remote addr  : {}", request.getRemoteAddr());
        log.info("  X-Feed       : {}", feed);
        log.info("  X-Environment: {}", environment);
        log.info("  Content-Type : {}", request.getContentType());
        log.info("  Payload size : {} bytes", bytes);

        if (log.isDebugEnabled()) {
            Enumeration<String> names = request.getHeaderNames();
            while (names.hasMoreElements()) {
                String name = names.nextElement();
                log.debug("  Header  {} = {}", name,
                        String.join(", ", Collections.list(request.getHeaders(name))));
            }
            if (bytes > 0 && bytes <= 4096) {
                log.debug("  Body (text): {}", new String(body).trim());
            }
        }

        Map<String, Object> response = new LinkedHashMap<>();
        response.put("status",        "accepted");
        response.put("feed",          feed);
        response.put("environment",   environment);
        response.put("bytesReceived", bytes);
        response.put("timestamp",     Instant.now().toString());

        return ResponseEntity.ok(response);
    }

    @GetMapping("/ping")
    public ResponseEntity<Map<String, String>> ping() {
        return ResponseEntity.ok(Map.of(
                "status",    "UP",
                "timestamp", Instant.now().toString()));
    }
}
