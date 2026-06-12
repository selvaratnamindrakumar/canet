package com.canet.keypoc.repository.dynamo;

import software.amazon.awssdk.enhanced.dynamodb.mapper.annotations.DynamoDbBean;
import software.amazon.awssdk.enhanced.dynamodb.mapper.annotations.DynamoDbPartitionKey;
import software.amazon.awssdk.enhanced.dynamodb.mapper.annotations.DynamoDbSortKey;

/**
 * DynamoDB item representing a single Accumulo cell.
 *
 * Table design:
 *   PK  = rowId (CGI)                        ← Accumulo RowID
 *   SK  = family#qualifier#timestamp         ← Accumulo CF + CQ + Timestamp
 *
 * This preserves all three filtering dimensions as query-able key components.
 * No application-side filtering is needed for any Accumulo scanner pattern.
 *
 * Query patterns:
 *   ROW_SCAN:    PK=CGI                           → all cells for the row
 *   FAMILY_SCAN: PK=CGI, SK begins_with DEVICE#   → only DEVICE family cells
 *   CELL_LOOKUP: PK=CGI, SK begins_with DEVICE#MODEL# → only MODEL cells
 *   VERSIONED:   PK=CGI, SK between DEVICE#MODEL#tsFrom and DEVICE#MODEL#tsTo
 */
@DynamoDbBean
public class CellItem {

    private String rowId;     // PK = CGI
    private String sortKey;   // SK = family#qualifier#timestamp

    // Redundant attributes for readability + GSI use if needed
    private String columnFamily;
    private String columnQualifier;
    private String columnVisibility;
    private Long   timestamp;
    private String value;

    @DynamoDbPartitionKey
    public String getRowId()              { return rowId; }
    public void setRowId(String v)        { this.rowId = v; }

    @DynamoDbSortKey
    public String getSortKey()            { return sortKey; }
    public void setSortKey(String v)      { this.sortKey = v; }

    public String getColumnFamily()       { return columnFamily; }
    public void setColumnFamily(String v) { this.columnFamily = v; }

    public String getColumnQualifier()    { return columnQualifier; }
    public void setColumnQualifier(String v) { this.columnQualifier = v; }

    public String getColumnVisibility()   { return columnVisibility; }
    public void setColumnVisibility(String v) { this.columnVisibility = v; }

    public Long getTimestamp()            { return timestamp; }
    public void setTimestamp(Long v)      { this.timestamp = v; }

    public String getValue()              { return value; }
    public void setValue(String v)        { this.value = v; }
}
