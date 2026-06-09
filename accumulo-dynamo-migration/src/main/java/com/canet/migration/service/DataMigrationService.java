package com.canet.migration.service;

import com.canet.migration.model.AccumuloKey;
import com.canet.migration.model.DataEntry;
import com.canet.migration.repository.DataStoreRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

@Slf4j
@Service
@RequiredArgsConstructor
public class DataMigrationService {

    private final DataStoreRepository repository;

    public Optional<DataEntry> getEntry(String row, String columnFamily, String columnQualifier) {
        return repository.getItem(row, columnFamily, columnQualifier);
    }

    public List<DataEntry> getRow(String row) {
        return repository.scanRow(row);
    }

    public List<DataEntry> getRowByFamily(String row, String columnFamily) {
        return repository.scanRowByColumnFamily(row, columnFamily);
    }

    public List<DataEntry> fullScan(int limit) {
        return repository.fullScan(limit);
    }

    /**
     * Write a single entry using Accumulo-style key/value API.
     * This is the primary migration ingestion path.
     */
    public DataEntry writeAccumuloEntry(String row, String columnFamily, String columnQualifier,
                                        String visibility, String value) {
        AccumuloKey key = AccumuloKey.builder()
                .row(row)
                .columnFamily(columnFamily)
                .columnQualifier(columnQualifier)
                .columnVisibility(visibility != null ? visibility : "")
                .timestamp(Instant.now().toEpochMilli())
                .build();

        DataEntry entry = DataEntry.fromAccumulo(key, value);
        repository.putItem(entry);
        log.info("Wrote Accumulo-style entry: row={} cf={} cq={}", row, columnFamily, columnQualifier);
        return entry;
    }

    /**
     * Bulk ingest a list of entries (use for migration batches).
     */
    public void bulkIngest(List<DataEntry> entries) {
        log.info("Starting bulk ingest of {} entries", entries.size());
        repository.putItems(entries);
    }

    public void deleteEntry(String row, String columnFamily, String columnQualifier) {
        repository.deleteItem(row, columnFamily, columnQualifier);
    }

    public void ensureTableExists() {
        repository.createTableIfNotExists();
    }
}
