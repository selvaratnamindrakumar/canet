package com.canet.app.service;

import com.canet.app.model.DataItem;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestTemplate;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;

@Service
public class HttpsDataServiceImpl implements HttpsDataService {

    private static final Logger log = LoggerFactory.getLogger(HttpsDataServiceImpl.class);

    private final RestTemplate restTemplate;

    @Value("${external.api.base-url}")
    private String baseUrl;

    public HttpsDataServiceImpl(RestTemplate restTemplate) {
        this.restTemplate = restTemplate;
    }

    @Override
    public List<DataItem> fetchAll() {
        try {
            log.info("Fetching all items from {}", baseUrl);
            ResponseEntity<DataItem[]> response = restTemplate.getForEntity(baseUrl, DataItem[].class);
            DataItem[] body = response.getBody();
            return body != null ? Arrays.asList(body) : Collections.emptyList();
        } catch (RestClientException e) {
            log.error("Failed to fetch data from {}: {}", baseUrl, e.getMessage());
            return Collections.emptyList();
        }
    }

    @Override
    public DataItem fetchById(int id) {
        String url = baseUrl + "/" + id;
        try {
            log.info("Fetching item {} from {}", id, url);
            return restTemplate.getForObject(url, DataItem.class);
        } catch (RestClientException e) {
            log.error("Failed to fetch item {} from {}: {}", id, url, e.getMessage());
            return null;
        }
    }
}
