package com.canet.migration.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Mirrors Accumulo's 5-part key: (row, colFamily, colQualifier, colVisibility, timestamp).
 * Used as a translation reference when mapping legacy Accumulo data to DynamoDB.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AccumuloKey {
    private String row;
    private String columnFamily;
    private String columnQualifier;
    private String columnVisibility;
    private long timestamp;
}
