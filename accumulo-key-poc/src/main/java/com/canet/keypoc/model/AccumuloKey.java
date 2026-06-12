package com.canet.keypoc.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * The true Accumulo 5-part key.
 *
 * Accumulo sorts all entries by this composite key:
 *   (rowId, columnFamily, columnQualifier, columnVisibility, timestamp DESC)
 *
 * This ordering is what enables Accumulo's server-side range/column filtering —
 * the tablet server can skip entire key ranges without reading values.
 *
 * A migration that discards this structure and moves filtering to the application
 * loses that efficiency guarantee.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AccumuloKey {

    /** The row identifier. For BB/IS this is the CGI. */
    private String rowId;

    /**
     * Column family — logical grouping (e.g. DEVICE, LOCATION, ENRICHMENT).
     * Accumulo stores families in separate locality groups on disk.
     * scanner.fetchColumnFamily("DEVICE") only reads the DEVICE locality group.
     */
    private String columnFamily;

    /**
     * Column qualifier — specific attribute within the family
     * (e.g. MODEL, OS, MANUFACTURER within DEVICE).
     * scanner.fetchColumn("DEVICE", "MODEL") — server returns only that one cell.
     */
    private String columnQualifier;

    /**
     * Cell-level visibility expression (e.g. "PUBLIC", "ADMIN&FINANCE").
     * Accumulo enforces this on read; tokens not in user's authorizations are invisible.
     * The migration must decide: replicate in Aurora row-level security / DynamoDB app logic?
     */
    private String columnVisibility;

    /**
     * Timestamp in epoch milliseconds.
     * Accumulo stores multiple versions per key, returning the latest by default.
     * scanner.setTimestamp(range) can filter to specific versions.
     */
    private long timestamp;

    // ── Composite sort key helpers ────────────────────────────────────────────

    /** DynamoDB sort key: Family#Qualifier#Timestamp — preserves Accumulo ordering. */
    public String toDynamoSortKey() {
        return columnFamily + "#" + columnQualifier + "#" + timestamp;
    }

    /** Sort key prefix for fetchColumnFamily queries: "DEVICE#" */
    public static String familyPrefix(String family) {
        return family + "#";
    }

    /** Sort key prefix for fetchColumn queries: "DEVICE#MODEL#" */
    public static String familyQualifierPrefix(String family, String qualifier) {
        return family + "#" + qualifier + "#";
    }
}
