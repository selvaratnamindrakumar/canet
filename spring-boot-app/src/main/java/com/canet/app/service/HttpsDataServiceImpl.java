package com.canet.app.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
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

    public HttpsDataServiceImpl(RestTemplateBuilder builder) {
        this.restTemplate = builder
                .setConnectTimeout(Duration.ofSeconds(5))
                .setReadTimeout(Duration.ofSeconds(10))
                .build();
    }

    @Override
    public List<Map<String, Object>> fetchAll(String url) {
        try {
            log.info("Fetching all records from {}", url);
            ResponseEntity<List<Map<String, Object>>> response =
                    restTemplate.exchange(url, HttpMethod.GET, null, LIST_OF_MAPS);
            List<Map<String, Object>> body = response.getBody();
            return body != null ? body : Collections.emptyList();
        } catch (RestClientException e) {
            log.error("Failed to fetch data from {}: {}", url, e.getMessage());
            return Collections.emptyList();
        }
    }

    @Override
    @SuppressWarnings("unchecked")
    public Map<String, Object> fetchById(String url, String id) {
        String fullUrl = url + "/" + id;
        try {
            log.info("Fetching record {} from {}", id, fullUrl);
            return restTemplate.getForObject(fullUrl, Map.class);
        } catch (RestClientException e) {
            log.error("Failed to fetch record {} from {}: {}", id, fullUrl, e.getMessage());
            return null;
        }
    }

    @Override
    @SuppressWarnings("unchecked")
    public TacSearchResult fetchByTacs(String url, List<String> tacs) {
        String fullUrl = url + "?tac=" + String.join(",", tacs);
        try {
            log.info("Multi-TAC lookup ({} TACs) from {}", tacs.size(), fullUrl);
            Map<String, Object> envelope = restTemplate.getForObject(fullUrl, Map.class);
            if (envelope == null) return new TacSearchResult(List.of(), tacs);
            List<Map<String, Object>> results =
                    (List<Map<String, Object>>) envelope.getOrDefault("results", List.of());
            List<String> notFound =
                    (List<String>) envelope.getOrDefault("notFound", List.of());
            return new TacSearchResult(results, notFound);
        } catch (RestClientException e) {
            log.error("Multi-TAC lookup failed for {}: {}", fullUrl, e.getMessage());
            return new TacSearchResult(List.of(), tacs);
        }
    }
}
