package com.canet.migration.model;

import lombok.Data;

@Data
public class ScanRequest {
    private String partitionKey;
    private String sortKeyPrefix;
    private String columnFamily;
    private int limit = 100;
}
