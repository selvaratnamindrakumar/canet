package com.canet.generator.client;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.context.properties.ConfigurationProperties;
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
 * All diosma.headers.* properties are sent as HTTP headers automatically.
 * Add any header in application.properties without touching this class:
 *
 *   diosma.headers.X-Application-Id=canet-generator
 *   diosma.headers.X-Source=canet
 *   diosma.headers.http.headers.application=<value>
 *   diosma.headers.http.headers.content.filename=<value>
 *   diosma.headers.http.headers.qualification=<value>
 *   diosma.headers.http.headers.subject.dn=<value>
 */
@Slf4j
@Component
@ConfigurationProperties(prefix = "diosma")
public class DiosmaClient {

    private final RestTemplate restTemplate;

    @Value("${diosma.base-url:}")
    private String diosmaBaseUrl;

    @Value("${diosma.notify-path:/api/diosma/receive}")
    private String notifyPath;

    // All diosma.headers.* entries are collected here automatically
    private final Map<String, String> headers = new LinkedHashMap<>();

    public DiosmaClient(RestTemplate restTemplate) {
        this.restTemplate = restTemplate;
    }

    // Called by Spring to populate diosma.headers.*
    public Map<String, String> getHeaders() {
        return headers;
    }

    public String postPayload(String  payload,
                              String  uuid,
                              String  srcIp,
                              int     srcPort,
                              Instant arrivalTime) {

        if (diosmaBaseUrl == null || diosmaBaseUrl.isBlank()) {
            log.debug("Diosma disabled — diosma.base-url not set");
            return null;
        }

        String url = diosmaBaseUrl + notifyPath;
        try {
            HttpHeaders httpHeaders = new HttpHeaders();
            httpHeaders.setContentType(MediaType.APPLICATION_JSON);

            // Forward every diosma.headers.* property as an HTTP header
            headers.forEach((name, value) -> {
                if (value != null && !value.isBlank()) {
                    httpHeaders.set(name, value);
                }
            });

            // content.filename is dynamic — set per packet using the UUID
            httpHeaders.set("http.headers.content.filename", uuid);

            Map<String, Object> body = new LinkedHashMap<>();
            body.put("payload",     payload);
            body.put("sourceIp",    srcIp != null ? srcIp : "");
            body.put("sourcePort",  srcPort);
            body.put("uuid",        uuid);
            body.put("arrivalTime", arrivalTime.toString());

            HttpEntity<Map<String, Object>> request = new HttpEntity<>(body, httpHeaders);

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
