package com.canet.migration.repository;

import com.canet.migration.model.DataEntry;

import java.util.List;
import java.util.Optional;

/**
 * Abstraction that mirrors the Accumulo Scanner / BatchWriter API surface.
 * Both the legacy Accumulo adapter and the new DynamoDB implementation satisfy this interface,
 * so the service layer and REST controllers are backend-agnostic.
 */
public interface DataStoreRepository {

    /**
     * Fetch a single item by exact Accumulo row + column family + column qualifier.
     */
    Optional<DataEntry> getItem(String row, String columnFamily, String columnQualifier);

    /**
     * Scan all items for a given row (equivalent to Accumulo row scanner).
     */
    List<DataEntry> scanRow(String row);

    /**
     * Scan all items for a given row where the sort key starts with columnFamilyPrefix
     * (equivalent to Accumulo setFetchColumnFamily).
     */
    List<DataEntry> scanRowByColumnFamily(String row, String columnFamily);

    /**
     * Full table scan with an optional limit (use sparingly in production).
     */
    List<DataEntry> fullScan(int limit);

    /**
     * Write (put) a single item.
     */
    void putItem(DataEntry entry);

    /**
     * Batch write a list of items.
     */
    void putItems(List<DataEntry> entries);

    /**
     * Delete a single item.
     */
    void deleteItem(String row, String columnFamily, String columnQualifier);

    /**
     * Create the backing table if it does not exist (dev/test utility).
     */
    void createTableIfNotExists();
}
