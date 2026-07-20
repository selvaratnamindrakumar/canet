package com.canet.validator.client;

import com.canet.validator.config.DiosmaProperties;
import com.canet.validator.entity.CapturedMessage;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.web.client.RestTemplateBuilder;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestTemplate;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Asynchronous client that notifies the Diosma endpoint after every
 * successful record insertion.
 *
 * The method is @Async so it runs on a separate thread and does not
 * block the 201 response back to the generator.  All outcomes — success,
 * HTTP errors, network failures — are written to the log.  Diosma can
 * then use the hash value to call back to the validator's exists endpoint
 * for verification.
 *
 * Disable by leaving diosma.post-url blank in application.properties.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class DiosmaClient {

    private final DiosmaProperties props;
    private final RestTemplateBuilder restTemplateBuilder;

    /**
     * POST the saved record to Diosma.  Runs asynchronously — never
     * throws; all errors are captured in the log.
     *
     * Body sent:
     * {
     *   "fileHash":    "d41d8cd98f00b204e9800998ecf8427e",
     *   "uuid":        "550e8400-...",
     *   "arrivalTime": "2026-07-17T10:00:00Z"
     * }
     */
    @Async
    public void notify(CapturedMessage record) {
        String url = props.getPostUrl();
        if (url == null || url.isBlank()) {
            log.debug("Diosma notification disabled — diosma.post-url not set");
            return;
        }

        try {
            HttpHeaders headers = buildHeaders();

            Map<String, Object> body = new LinkedHashMap<>();
            body.put("fileHash",    record.getFileHash());
            body.put("uuid",        record.getUuid() != null ? record.getUuid() : "");
            body.put("arrivalTime", record.getArrivalTime().toString());

            HttpEntity<Map<String, Object>> request = new HttpEntity<>(body, headers);

            log.debug("Diosma POST hash={} url={}", record.getFileHash(), url);

            RestTemplate restTemplate = restTemplateBuilder.build();
            ResponseEntity<String> response = restTemplate.postForEntity(url, request, String.class);

            log.info("Diosma notified hash={} status={} body={}",
                    record.getFileHash(),
                    response.getStatusCode().value(),
                    response.getBody());

        } catch (Exception e) {
            log.error("Diosma notification failed hash={} url={} error={}",
                    record.getFileHash(), url, e.getMessage());
        }
    }

    private HttpHeaders buildHeaders() {
        HttpHeaders headers = new HttpHeaders();

        // Apply all configured headers first
        props.getHeaders().forEach((name, value) -> headers.set(name, value));

        // Default Content-Type to application/json when not explicitly configured
        if (!headers.containsKey(HttpHeaders.CONTENT_TYPE)) {
            headers.setContentType(MediaType.APPLICATION_JSON);
        }

        return headers;
    }
}
