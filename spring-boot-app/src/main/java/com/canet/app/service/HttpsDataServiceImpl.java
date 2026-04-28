package com.canet.app.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.web.client.RestTemplateBuilder;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.HttpMethod;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestTemplate;

import java.time.Duration;
import java.util.Collections;
import java.util.List;
import java.util.Map;

@Service
public class HttpsDataServiceImpl implements HttpsDataService {

    private static final Logger log = LoggerFactory.getLogger(HttpsDataServiceImpl.class);

    private static final ParameterizedTypeReference<List<Map<String, Object>>> LIST_OF_MAPS =
            new ParameterizedTypeReference<>() {};

    private final RestTemplate restTemplate;

    @Value("${external.api.base-url}")
    private String baseUrl;

    /**
     * Accepting RestTemplateBuilder (rather than RestTemplate) lets
     * @RestClientTest inject a mock-backed builder without extra @Import wiring,
     * and lets production code apply timeouts via the builder API.
     */
    public HttpsDataServiceImpl(RestTemplateBuilder builder) {
        this.restTemplate = builder
                .setConnectTimeout(Duration.ofSeconds(5))
                .setReadTimeout(Duration.ofSeconds(10))
                .build();
    }

    @Override
    public List<Map<String, Object>> fetchAll() {
        try {
            log.info("Fetching all records from {}", baseUrl);
            ResponseEntity<List<Map<String, Object>>> response =
                    restTemplate.exchange(baseUrl, HttpMethod.GET, null, LIST_OF_MAPS);
            List<Map<String, Object>> body = response.getBody();
            return body != null ? body : Collections.emptyList();
        } catch (RestClientException e) {
            log.error("Failed to fetch data from {}: {}", baseUrl, e.getMessage());
            return Collections.emptyList();
        }
    }

    @Override
    @SuppressWarnings("unchecked")
    public Map<String, Object> fetchById(String id) {
        String url = baseUrl + "/" + id;
        try {
            log.info("Fetching record {} from {}", id, url);
            // Jackson deserialises a JSON object into a LinkedHashMap, preserving field order
            return restTemplate.getForObject(url, Map.class);
        } catch (RestClientException e) {
            log.error("Failed to fetch record {} from {}: {}", id, url, e.getMessage());
            return null;
        }
    }
}
