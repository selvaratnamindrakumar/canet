package com.canet.keypoc.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * A single Accumulo key-value cell.
 * This is the atomic unit of storage in Accumulo.
 *
 * The key observation for the POC:
 *   - Accumulo returns only the cells matching the scanner's Range + column filter.
 *   - DynamoDB (PK+SK model) returns individual cells when SK is Family#Qualifier#Timestamp.
 *   - Aurora (cgi, family, qualifier, ts, value) returns individual cells via WHERE clause.
 *   - A flattened JSON document returns ALL cells and filters in Java — that is the anti-pattern.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AccumuloCell {
    private AccumuloKey key;
    private String value;

    public static AccumuloCell of(String rowId, String family, String qualifier,
                                   String visibility, long timestamp, String value) {
        return AccumuloCell.builder()
                .key(AccumuloKey.builder()
                        .rowId(rowId)
                        .columnFamily(family)
                        .columnQualifier(qualifier)
                        .columnVisibility(visibility)
                        .timestamp(timestamp)
                        .build())
                .value(value)
                .build();
    }
}
