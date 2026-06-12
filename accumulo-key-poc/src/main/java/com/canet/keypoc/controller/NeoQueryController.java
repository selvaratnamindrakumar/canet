package com.canet.keypoc.controller;

import com.canet.keypoc.model.AccumuloCell;
import com.canet.keypoc.scanner.AccumuloScannerOp;
import com.canet.keypoc.scanner.CellStoreRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * NEO API simulation — exposes each Accumulo scanner pattern as a REST endpoint.
 * Use ?backend=dynamo|aurora to switch backends at request time.
 *
 * Each endpoint documents:
 *   - The Accumulo scanner code it simulates
 *   - What DynamoDB does (SK begins_with / Query)
 *   - What Aurora does (SQL WHERE clause)
 *
 * This lets you compare responses and latency for identical queries against both backends.
 */
@RestController
@RequestMapping("/api/poc")
public class NeoQueryController {

    private final CellStoreRepository dynamo;
    private final CellStoreRepository aurora;

    public NeoQueryController(
            @Qualifier("dynamoCellStore")  CellStoreRepository dynamo,
            @Qualifier("auroraCellStore")  CellStoreRepository aurora) {
        this.dynamo = dynamo;
        this.aurora = aurora;
    }

    /**
     * ROW_SCAN — fetch all cells for a CGI.
     * Accumulo: scanner.setRange(new Range(rowId))
     * DynamoDB:  Query PK=rowId → all SK values
     * Aurora:    SELECT * FROM handset_cells WHERE cgi=?
     *
     * GET /api/poc/row/CGI12345?backend=dynamo
     */
    @GetMapping("/row/{rowId}")
    public List<AccumuloCell> rowScan(
            @PathVariable String rowId,
            @RequestParam(defaultValue = "dynamo") String backend) {
        return repo(backend).fetchRow(rowId);
    }

    /**
     * FAMILY_SCAN — fetch all cells for a CGI with a specific column family.
     * Accumulo: scanner.fetchColumnFamily("DEVICE")
     * DynamoDB:  Query PK=rowId, SK begins_with "DEVICE#"    ← server-side, no app filtering
     * Aurora:    SELECT * WHERE cgi=? AND family='DEVICE'    ← indexed
     *
     * GET /api/poc/row/CGI12345/family/DEVICE?backend=dynamo
     */
    @GetMapping("/row/{rowId}/family/{family}")
    public List<AccumuloCell> familyScan(
            @PathVariable String rowId,
            @PathVariable String family,
            @RequestParam(defaultValue = "dynamo") String backend) {
        return repo(backend).fetchRowByFamily(rowId, family);
    }

    /**
     * CELL_LOOKUP — fetch all versions of a specific cell (family + qualifier).
     * Accumulo: scanner.fetchColumn(family, qualifier)
     * DynamoDB:  Query PK=rowId, SK begins_with "DEVICE#MODEL#"
     * Aurora:    SELECT * WHERE cgi=? AND family=? AND qualifier=?
     *
     * GET /api/poc/row/CGI12345/family/DEVICE/qualifier/MODEL?backend=dynamo
     */
    @GetMapping("/row/{rowId}/family/{family}/qualifier/{qualifier}")
    public List<AccumuloCell> cellLookup(
            @PathVariable String rowId,
            @PathVariable String family,
            @PathVariable String qualifier,
            @RequestParam(defaultValue = "dynamo") String backend) {
        return repo(backend).fetchCell(rowId, family, qualifier);
    }

    /**
     * VERSIONED_CELL_LOOKUP — fetch a cell within a timestamp range.
     * Accumulo: scanner.fetchColumn + scanner.setTimestampRange(from, to)
     * DynamoDB:  fetch all FAMILY#QUALIFIER# versions + filter ts in application
     *            (string SK needs zero-padding for correct range ordering)
     * Aurora:    SELECT * WHERE cgi=? AND family=? AND qualifier=? AND ts BETWEEN ? AND ?
     *
     * GET /api/poc/row/CGI12345/family/DEVICE/qualifier/MODEL/versions?from=1700000000000&to=1750000000000
     */
    @GetMapping("/row/{rowId}/family/{family}/qualifier/{qualifier}/versions")
    public List<AccumuloCell> versionedLookup(
            @PathVariable String rowId,
            @PathVariable String family,
            @PathVariable String qualifier,
            @RequestParam long from,
            @RequestParam long to,
            @RequestParam(defaultValue = "dynamo") String backend) {
        return repo(backend).fetchCellVersions(rowId, family, qualifier, from, to);
    }

    /**
     * MULTI_FAMILY — fetch two families in one call.
     * Accumulo: scanner.fetchColumnFamily("DEVICE"); scanner.fetchColumnFamily("LOCATION")
     * DynamoDB:  Two separate Query calls merged in application (no multi-prefix begins_with)
     * Aurora:    SELECT * WHERE cgi=? AND family IN ('DEVICE','LOCATION')  — single query
     *
     * GET /api/poc/row/CGI12345/families?f=DEVICE,LOCATION&backend=dynamo
     */
    @GetMapping("/row/{rowId}/families")
    public List<AccumuloCell> multiFamilyScan(
            @PathVariable String rowId,
            @RequestParam String f,
            @RequestParam(defaultValue = "dynamo") String backend) {
        List<String> families = List.of(f.split(","));
        return repo(backend).fetchRowByFamilies(rowId, families);
    }

    private CellStoreRepository repo(String backend) {
        return "aurora".equalsIgnoreCase(backend) ? aurora : dynamo;
    }
}
