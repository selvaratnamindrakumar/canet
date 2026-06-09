package com.canet.migration.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import software.amazon.awssdk.enhanced.dynamodb.mapper.annotations.DynamoDbBean;
import software.amazon.awssdk.enhanced.dynamodb.mapper.annotations.DynamoDbPartitionKey;
import software.amazon.awssdk.enhanced.dynamodb.mapper.annotations.DynamoDbSortKey;

/**
 * DynamoDB item that represents a migrated Accumulo key/value pair.
 *
 * Accumulo → DynamoDB mapping:
 *   row               → partitionKey  (PK)
 *   columnFamily#colQualifier → sortKey (SK)
 *   columnVisibility  → attribute
 *   timestamp         → attribute
 *   value             → attribute
 *
 * This composite sort key preserves Accumulo's column family + qualifier structure
 * while fitting DynamoDB's two-key model.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@DynamoDbBean
public class DataEntry {

    /** Accumulo row → DynamoDB Partition Key */
    private String partitionKey;

    /** Accumulo colFamily#colQualifier → DynamoDB Sort Key */
    private String sortKey;

    /** Original Accumulo column family (extracted from sortKey for convenience) */
    private String columnFamily;

    /** Original Accumulo column qualifier */
    private String columnQualifier;

    /** Accumulo cell visibility label (e.g. "PUBLIC", "ADMIN&FINANCE") */
    private String columnVisibility;

    /** Accumulo cell timestamp (epoch millis) */
    private Long timestamp;

    /** The actual cell value */
    private String value;

    @DynamoDbPartitionKey
    public String getPartitionKey() { return partitionKey; }

    @DynamoDbSortKey
    public String getSortKey() { return sortKey; }

    /**
     * Build a DataEntry from an Accumulo-style key/value pair.
     */
    public static DataEntry fromAccumulo(AccumuloKey key, String value) {
        return DataEntry.builder()
                .partitionKey(key.getRow())
                .sortKey(key.getColumnFamily() + "#" + key.getColumnQualifier())
                .columnFamily(key.getColumnFamily())
                .columnQualifier(key.getColumnQualifier())
                .columnVisibility(key.getColumnVisibility())
                .timestamp(key.getTimestamp())
                .value(value)
                .build();
    }

    /**
     * Reconstruct an AccumuloKey from this DynamoDB item (useful for audit/comparison).
     */
    public AccumuloKey toAccumuloKey() {
        return AccumuloKey.builder()
                .row(partitionKey)
                .columnFamily(columnFamily)
                .columnQualifier(columnQualifier)
                .columnVisibility(columnVisibility)
                .timestamp(timestamp != null ? timestamp : 0L)
                .build();
    }
}
