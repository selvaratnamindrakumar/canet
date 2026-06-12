package com.canet.keypoc.scanner;

import com.canet.keypoc.model.AccumuloCell;

import java.util.List;

/**
 * Backend-agnostic interface that mirrors the Accumulo Scanner API surface.
 *
 * Both the DynamoDB and Aurora implementations satisfy this contract.
 * The key design principle: filtering happens IN THE DATABASE, not in Java.
 *
 * For each method, the implementation notes explain exactly what DynamoDB
 * and Aurora do differently, and whether any filtering leaks to the application layer.
 */
public interface CellStoreRepository {

    /**
     * ROW_SCAN: Fetch ALL cells for a row.
     * Accumulo: scanner.setRange(new Range(rowId))
     * DynamoDB:  Query PK=rowId  (returns all SK values — all families/qualifiers)
     * Aurora:    SELECT * FROM handset_cells WHERE cgi = ?
     */
    List<AccumuloCell> fetchRow(String rowId);

    /**
     * FAMILY_SCAN: Fetch all cells for row where family matches.
     * Accumulo: scanner.fetchColumnFamily("DEVICE")  — server skips other locality groups
     * DynamoDB:  Query PK=rowId, SK begins_with "DEVICE#"  — index-only, no scan
     * Aurora:    SELECT * FROM handset_cells WHERE cgi=? AND family=?  — indexed
     */
    List<AccumuloCell> fetchRowByFamily(String rowId, String family);

    /**
     * CELL_LOOKUP: Fetch a specific cell (row + family + qualifier).
     * Accumulo: scanner.fetchColumn("DEVICE", "MODEL")  — single cell read
     * DynamoDB:  Query PK=rowId, SK begins_with "DEVICE#MODEL#"  — or GetItem if ts known
     * Aurora:    SELECT * FROM handset_cells WHERE cgi=? AND family=? AND qualifier=?
     */
    List<AccumuloCell> fetchCell(String rowId, String family, String qualifier);

    /**
     * VERSIONED_CELL_LOOKUP: Fetch versioned history of a specific cell.
     * Accumulo: fetchColumn + setTimestampRange  — returns multiple versions
     * DynamoDB:  Query PK=rowId, SK between "DEVICE#MODEL#tsFrom" and "DEVICE#MODEL#tsTo"
     * Aurora:    SELECT * FROM handset_cells WHERE cgi=? AND family=? AND qualifier=? AND ts BETWEEN ? AND ?
     */
    List<AccumuloCell> fetchCellVersions(String rowId, String family, String qualifier,
                                          long timestampFrom, long timestampTo);

    /**
     * MULTI_FAMILY: Fetch cells for a row across two families in one query.
     * Accumulo: scanner.fetchColumnFamily("DEVICE"); scanner.fetchColumnFamily("LOCATION")
     * DynamoDB:  Two separate queries OR scan-with-filter (no multi-prefix begins_with)
     * Aurora:    WHERE cgi=? AND family IN ('DEVICE','LOCATION')  — single query
     */
    List<AccumuloCell> fetchRowByFamilies(String rowId, List<String> families);

    /**
     * Execute a scanner operation described by the AccumuloScannerOp object.
     * This is the unified entry point used by the NEO API simulation and comparison engine.
     */
    List<AccumuloCell> execute(AccumuloScannerOp op);

    void save(AccumuloCell cell);
    void saveAll(List<AccumuloCell> cells);
    void deleteAll();

    String backendName();
}
