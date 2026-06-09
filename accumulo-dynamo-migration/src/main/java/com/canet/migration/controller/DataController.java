package com.canet.migration.controller;

import com.canet.migration.model.DataEntry;
import com.canet.migration.service.DataMigrationService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/data")
@RequiredArgsConstructor
public class DataController {

    private final DataMigrationService service;

    /**
     * GET /api/data/row/{row}
     * Returns all column family/qualifier entries for a row (Accumulo row scan equivalent).
     */
    @GetMapping("/row/{row}")
    public ResponseEntity<List<DataEntry>> getRow(@PathVariable String row) {
        List<DataEntry> entries = service.getRow(row);
        if (entries.isEmpty()) {
            return ResponseEntity.notFound().build();
        }
        return ResponseEntity.ok(entries);
    }

    /**
     * GET /api/data/row/{row}/family/{columnFamily}
     * Fetch all entries for a row filtered by column family (Accumulo setFetchColumnFamily).
     */
    @GetMapping("/row/{row}/family/{columnFamily}")
    public ResponseEntity<List<DataEntry>> getRowByFamily(
            @PathVariable String row,
            @PathVariable String columnFamily) {
        return ResponseEntity.ok(service.getRowByFamily(row, columnFamily));
    }

    /**
     * GET /api/data/row/{row}/family/{columnFamily}/qualifier/{columnQualifier}
     * Fetch a single Accumulo cell.
     */
    @GetMapping("/row/{row}/family/{columnFamily}/qualifier/{columnQualifier}")
    public ResponseEntity<DataEntry> getItem(
            @PathVariable String row,
            @PathVariable String columnFamily,
            @PathVariable String columnQualifier) {
        return service.getEntry(row, columnFamily, columnQualifier)
                .map(ResponseEntity::ok)
                .orElse(ResponseEntity.notFound().build());
    }

    /**
     * GET /api/data/scan?limit=50
     * Full table scan (dev/admin use only).
     */
    @GetMapping("/scan")
    public List<DataEntry> scan(@RequestParam(defaultValue = "50") int limit) {
        return service.fullScan(Math.min(limit, 500));
    }

    /**
     * POST /api/data/entry
     * Write a single entry using Accumulo-style key/value.
     * Body: { "row": "...", "columnFamily": "...", "columnQualifier": "...", "visibility": "...", "value": "..." }
     */
    @PostMapping("/entry")
    public ResponseEntity<DataEntry> putEntry(@RequestBody Map<String, String> body) {
        DataEntry entry = service.writeAccumuloEntry(
                body.get("row"),
                body.get("columnFamily"),
                body.get("columnQualifier"),
                body.getOrDefault("visibility", ""),
                body.get("value")
        );
        return ResponseEntity.ok(entry);
    }

    /**
     * POST /api/data/batch
     * Bulk write a list of DataEntry objects.
     */
    @PostMapping("/batch")
    public ResponseEntity<Map<String, Object>> batchPut(@RequestBody List<DataEntry> entries) {
        service.bulkIngest(entries);
        return ResponseEntity.ok(Map.of("written", entries.size()));
    }

    /**
     * DELETE /api/data/row/{row}/family/{columnFamily}/qualifier/{columnQualifier}
     */
    @DeleteMapping("/row/{row}/family/{columnFamily}/qualifier/{columnQualifier}")
    public ResponseEntity<Void> deleteItem(
            @PathVariable String row,
            @PathVariable String columnFamily,
            @PathVariable String columnQualifier) {
        service.deleteEntry(row, columnFamily, columnQualifier);
        return ResponseEntity.noContent().build();
    }
}
