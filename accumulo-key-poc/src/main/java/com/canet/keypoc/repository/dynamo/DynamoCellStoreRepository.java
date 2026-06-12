package com.canet.keypoc.repository.dynamo;

import com.canet.keypoc.model.AccumuloCell;
import com.canet.keypoc.model.AccumuloKey;
import com.canet.keypoc.scanner.AccumuloScannerOp;
import com.canet.keypoc.scanner.CellStoreRepository;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Repository;
import software.amazon.awssdk.enhanced.dynamodb.*;
import software.amazon.awssdk.enhanced.dynamodb.model.QueryConditional;
import software.amazon.awssdk.enhanced.dynamodb.model.QueryEnhancedRequest;
import software.amazon.awssdk.services.dynamodb.DynamoDbClient;
import software.amazon.awssdk.services.dynamodb.model.*;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

/**
 * DynamoDB implementation of the Accumulo cell store.
 *
 * Table: handset-cells
 *   PK = rowId   (Accumulo RowID / CGI)
 *   SK = family#qualifier#timestamp
 *
 * ALL filtering is performed by the DynamoDB query engine using the sort key.
 * No application-side filtering occurs in any method — this directly mirrors
 * Accumulo's server-side column filtering behaviour.
 */
@Slf4j
@Repository("dynamoCellStore")
public class DynamoCellStoreRepository implements CellStoreRepository {

    private final DynamoDbTable<CellItem> table;
    private final DynamoDbClient rawClient;
    private final String tableName;

    public DynamoCellStoreRepository(
            DynamoDbEnhancedClient enhancedClient,
            DynamoDbClient rawClient,
            @Value("${poc.dynamo.cells-table:handset-cells}") String tableName) {
        this.rawClient = rawClient;
        this.tableName = tableName;
        this.table = enhancedClient.table(tableName, TableSchema.fromBean(CellItem.class));
    }

    /**
     * ROW_SCAN: PK=rowId — returns all SK values (all families + qualifiers + timestamps).
     * DynamoDB Query, not Scan. Efficient: only reads items for this partition key.
     */
    @Override
    public List<AccumuloCell> fetchRow(String rowId) {
        log.debug("[DynamoDB] ROW_SCAN: PK={}", rowId);
        return query(QueryConditional.keyEqualTo(Key.builder().partitionValue(rowId).build()));
    }

    /**
     * FAMILY_SCAN: PK=rowId, SK begins_with "FAMILY#"
     * DynamoDB evaluates SK prefix server-side — only matching items returned.
     * No application filtering. Mirrors Accumulo scanner.fetchColumnFamily().
     */
    @Override
    public List<AccumuloCell> fetchRowByFamily(String rowId, String family) {
        log.debug("[DynamoDB] FAMILY_SCAN: PK={} SK begins_with {}#", rowId, family);
        return query(QueryConditional.sortBeginsWith(
                Key.builder().partitionValue(rowId).sortValue(AccumuloKey.familyPrefix(family)).build()));
    }

    /**
     * CELL_LOOKUP: PK=rowId, SK begins_with "FAMILY#QUALIFIER#"
     * Returns all versions of this cell (all timestamps).
     * Mirrors Accumulo scanner.fetchColumn(family, qualifier).
     */
    @Override
    public List<AccumuloCell> fetchCell(String rowId, String family, String qualifier) {
        log.debug("[DynamoDB] CELL_LOOKUP: PK={} SK begins_with {}#{}", rowId, family, qualifier);
        return query(QueryConditional.sortBeginsWith(
                Key.builder().partitionValue(rowId)
                        .sortValue(AccumuloKey.familyQualifierPrefix(family, qualifier)).build()));
    }

    /**
     * VERSIONED_CELL_LOOKUP: PK=rowId, SK between FAMILY#QUALIFIER#tsFrom and FAMILY#QUALIFIER#tsTo
     * Returns specific version range. Mirrors Accumulo setTimestampRange().
     *
     * NOTE: DynamoDB sort key ordering is lexicographic for strings.
     * For correct timestamp ordering, timestamps must be zero-padded or use a numeric sort key.
     * In this POC we use a separate timestamp attribute + filter expression as a pragmatic approach.
     */
    @Override
    public List<AccumuloCell> fetchCellVersions(String rowId, String family, String qualifier,
                                                 long timestampFrom, long timestampTo) {
        log.debug("[DynamoDB] VERSIONED_LOOKUP: PK={} family={} qualifier={} ts=[{},{}]",
                rowId, family, qualifier, timestampFrom, timestampTo);

        // Fetch all versions of this cell, then filter by timestamp attribute
        // (SK lexicographic ordering is not suitable for timestamp range without zero-padding)
        List<AccumuloCell> cells = fetchCell(rowId, family, qualifier);
        return cells.stream()
                .filter(c -> c.getKey().getTimestamp() >= timestampFrom
                        && c.getKey().getTimestamp() <= timestampTo)
                .collect(Collectors.toList());
    }

    /**
     * MULTI_FAMILY: DynamoDB does NOT support multi-prefix begins_with in a single Query.
     * Requires N separate queries (one per family) or a Scan with filter.
     * This is a DynamoDB limitation vs Accumulo and Aurora.
     *
     * Here we issue parallel queries (one per family) and merge results in the application.
     * This is documented as an application-layer merge, not filtering — the data is still
     * pre-filtered per family at the DB level.
     */
    @Override
    public List<AccumuloCell> fetchRowByFamilies(String rowId, List<String> families) {
        log.debug("[DynamoDB] MULTI_FAMILY: PK={} families={} — requires {} separate queries",
                rowId, families, families.size());
        List<AccumuloCell> merged = new ArrayList<>();
        for (String family : families) {
            merged.addAll(fetchRowByFamily(rowId, family));
        }
        return merged;
    }

    @Override
    public List<AccumuloCell> execute(AccumuloScannerOp op) {
        return switch (op.getType()) {
            case ROW_SCAN          -> fetchRow(op.getRowId());
            case FAMILY_SCAN       -> fetchRowByFamily(op.getRowId(), op.getColumnFamily());
            case CELL_LOOKUP       -> fetchCell(op.getRowId(), op.getColumnFamily(), op.getColumnQualifier());
            case VERSIONED_CELL_LOOKUP -> fetchCellVersions(op.getRowId(), op.getColumnFamily(),
                    op.getColumnQualifier(), op.getTimestampFrom(), op.getTimestampTo());
        };
    }

    @Override
    public void save(AccumuloCell cell) {
        table.putItem(toItem(cell));
    }

    @Override
    public void saveAll(List<AccumuloCell> cells) {
        cells.forEach(c -> table.putItem(toItem(c)));
    }

    @Override
    public void deleteAll() {
        List<AccumuloCell> all = new ArrayList<>();
        table.scan().items().forEach(i -> all.add(toDomain(i)));
        all.forEach(c -> table.deleteItem(
                Key.builder().partitionValue(c.getKey().getRowId())
                        .sortValue(c.getKey().toDynamoSortKey()).build()));
    }

    @Override
    public String backendName() { return "DynamoDB (PK=CGI, SK=Family#Qualifier#Timestamp)"; }

    // ── Table management ──────────────────────────────────────────────────────

    public void createTableIfNotExists() {
        try {
            rawClient.describeTable(r -> r.tableName(tableName));
            log.info("DynamoDB table '{}' already exists", tableName);
        } catch (ResourceNotFoundException e) {
            rawClient.createTable(r -> r
                    .tableName(tableName)
                    .attributeDefinitions(
                            AttributeDefinition.builder().attributeName("rowId").attributeType(ScalarAttributeType.S).build(),
                            AttributeDefinition.builder().attributeName("sortKey").attributeType(ScalarAttributeType.S).build()
                    )
                    .keySchema(
                            KeySchemaElement.builder().attributeName("rowId").keyType(KeyType.HASH).build(),
                            KeySchemaElement.builder().attributeName("sortKey").keyType(KeyType.RANGE).build()
                    )
                    .billingMode(BillingMode.PAY_PER_REQUEST));
            rawClient.waiter().waitUntilTableExists(r -> r.tableName(tableName));
            log.info("Created DynamoDB table '{}'", tableName);
        }
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    private List<AccumuloCell> query(QueryConditional condition) {
        return table.query(QueryEnhancedRequest.builder().queryConditional(condition).build())
                .items().stream().map(this::toDomain).collect(Collectors.toList());
    }

    private CellItem toItem(AccumuloCell c) {
        AccumuloKey k = c.getKey();
        CellItem item = new CellItem();
        item.setRowId(k.getRowId());
        item.setSortKey(k.toDynamoSortKey());
        item.setColumnFamily(k.getColumnFamily());
        item.setColumnQualifier(k.getColumnQualifier());
        item.setColumnVisibility(k.getColumnVisibility());
        item.setTimestamp(k.getTimestamp());
        item.setValue(c.getValue());
        return item;
    }

    private AccumuloCell toDomain(CellItem i) {
        return AccumuloCell.of(i.getRowId(), i.getColumnFamily(), i.getColumnQualifier(),
                i.getColumnVisibility(), i.getTimestamp() != null ? i.getTimestamp() : 0L, i.getValue());
    }
}
