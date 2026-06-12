package com.canet.poc.repository.postgres;

import lombok.AllArgsConstructor;
import lombok.Data;

/**
 * Lightweight SQL projection — only the fields the NEO API needs.
 * Aurora reads only these columns from disk; DynamoDB always reads the full item.
 */
@Data
@AllArgsConstructor
public class HandsetProjection {
    private String cgi;
    private String manufacturer;
    private String model;
    private String technology;
    private String region;
    private String country;
    private String dataSource;
}
