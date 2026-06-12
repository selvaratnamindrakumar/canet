package com.canet.keypoc.scanner;

import lombok.Builder;
import lombok.Data;

/**
 * Represents a single Accumulo scanner operation as it would be written in NEO.
 *
 * This is the central object for the POC analysis:
 * - Capture what Accumulo operations NEO actually performs.
 * - Determine whether Family/Qualifier are used as filter dimensions.
 * - Drive identical queries against both DynamoDB and Aurora.
 *
 * Example NEO Accumulo code translated to this object:
 *
 *   // Case 1: full row fetch (only RowID matters)
 *   scanner.setRange(new Range("CGI12345"));
 *   → ScannerOp.rowScan("CGI12345")
 *
 *   // Case 2: row + column family filter (server-side in Accumulo)
 *   scanner.setRange(new Range("CGI12345"));
 *   scanner.fetchColumnFamily(new Text("DEVICE"));
 *   → ScannerOp.familyScan("CGI12345", "DEVICE")
 *
 *   // Case 3: exact cell lookup (row + family + qualifier)
 *   scanner.fetchColumn(new Text("DEVICE"), new Text("MODEL"));
 *   → ScannerOp.cellLookup("CGI12345", "DEVICE", "MODEL")
 *
 *   // Case 4: timestamp range (versioned reads)
 *   scanner.setTimestampRange(new TimestampRange(from, to));
 *   → ScannerOp.timestampRange("CGI12345", "DEVICE", "MODEL", from, to)
 */
@Data
@Builder
public class AccumuloScannerOp {

    public enum Type {
        /** scanner.setRange(rowId) — fetch all cells for a row */
        ROW_SCAN,

        /** scanner.fetchColumnFamily(family) — server filters by family */
        FAMILY_SCAN,

        /** scanner.fetchColumn(family, qualifier) — server returns one cell type */
        CELL_LOOKUP,

        /** scanner.fetchColumn + setTimestampRange — versioned cell lookup */
        VERSIONED_CELL_LOOKUP
    }

    private Type type;
    private String rowId;
    private String columnFamily;      // null for ROW_SCAN
    private String columnQualifier;   // null for ROW_SCAN and FAMILY_SCAN
    private Long timestampFrom;       // null unless VERSIONED
    private Long timestampTo;         // null unless VERSIONED

    // ── Factory methods mirroring the Accumulo Scanner API ────────────────────

    public static AccumuloScannerOp rowScan(String rowId) {
        return AccumuloScannerOp.builder().type(Type.ROW_SCAN).rowId(rowId).build();
    }

    public static AccumuloScannerOp familyScan(String rowId, String family) {
        return AccumuloScannerOp.builder()
                .type(Type.FAMILY_SCAN).rowId(rowId).columnFamily(family).build();
    }

    public static AccumuloScannerOp cellLookup(String rowId, String family, String qualifier) {
        return AccumuloScannerOp.builder()
                .type(Type.CELL_LOOKUP).rowId(rowId).columnFamily(family).columnQualifier(qualifier).build();
    }

    public static AccumuloScannerOp versionedLookup(String rowId, String family, String qualifier,
                                                      long from, long to) {
        return AccumuloScannerOp.builder()
                .type(Type.VERSIONED_CELL_LOOKUP).rowId(rowId)
                .columnFamily(family).columnQualifier(qualifier)
                .timestampFrom(from).timestampTo(to).build();
    }

    /**
     * True when this operation uses Family or Qualifier as a server-side filter.
     * If this returns true, a flattened document model (fetch-all + app-filter) loses efficiency.
     */
    public boolean usesFamilyOrQualifierFilter() {
        return type == Type.FAMILY_SCAN || type == Type.CELL_LOOKUP || type == Type.VERSIONED_CELL_LOOKUP;
    }

    public String describe() {
        return switch (type) {
            case ROW_SCAN          -> "scanner.setRange(\"" + rowId + "\")";
            case FAMILY_SCAN       -> "scanner.setRange(\"" + rowId + "\"); scanner.fetchColumnFamily(\"" + columnFamily + "\")";
            case CELL_LOOKUP       -> "scanner.fetchColumn(\"" + columnFamily + "\", \"" + columnQualifier + "\") for row=" + rowId;
            case VERSIONED_CELL_LOOKUP -> "scanner.fetchColumn(\"" + columnFamily + "\",\"" + columnQualifier + "\") + setTimestampRange(" + timestampFrom + "," + timestampTo + ")";
        };
    }
}
