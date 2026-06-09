package com.canet.migration.repository;

import com.canet.migration.model.DataEntry;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Repository;
import software.amazon.awssdk.enhanced.dynamodb.*;
import software.amazon.awssdk.enhanced.dynamodb.model.*;
import software.amazon.awssdk.services.dynamodb.DynamoDbClient;
import software.amazon.awssdk.services.dynamodb.model.*;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

@Slf4j
@Repository
public class DynamoDbDataStoreRepository implements DataStoreRepository {

    private final DynamoDbTable<DataEntry> table;
    private final DynamoDbClient dynamoDbClient;
    private final String tableName;

    public DynamoDbDataStoreRepository(
            DynamoDbEnhancedClient enhancedClient,
            DynamoDbClient dynamoDbClient,
            @Value("${aws.dynamodb.table-name:accumulo-migration}") String tableName) {
        this.dynamoDbClient = dynamoDbClient;
        this.tableName = tableName;
        this.table = enhancedClient.table(tableName, TableSchema.fromBean(DataEntry.class));
    }

    @Override
    public Optional<DataEntry> getItem(String row, String columnFamily, String columnQualifier) {
        Key key = Key.builder()
                .partitionValue(row)
                .sortValue(columnFamily + "#" + columnQualifier)
                .build();
        DataEntry result = table.getItem(r -> r.key(key));
        return Optional.ofNullable(result);
    }

    @Override
    public List<DataEntry> scanRow(String row) {
        QueryConditional queryConditional = QueryConditional
                .keyEqualTo(Key.builder().partitionValue(row).build());
        return table.query(queryConditional)
                .items()
                .stream()
                .collect(Collectors.toList());
    }

    @Override
    public List<DataEntry> scanRowByColumnFamily(String row, String columnFamily) {
        QueryConditional queryConditional = QueryConditional
                .sortBeginsWith(Key.builder()
                        .partitionValue(row)
                        .sortValue(columnFamily + "#")
                        .build());
        return table.query(queryConditional)
                .items()
                .stream()
                .collect(Collectors.toList());
    }

    @Override
    public List<DataEntry> fullScan(int limit) {
        ScanEnhancedRequest scanRequest = ScanEnhancedRequest.builder()
                .limit(limit)
                .build();
        List<DataEntry> results = new ArrayList<>();
        table.scan(scanRequest).items().forEach(results::add);
        if (results.size() > limit) {
            return results.subList(0, limit);
        }
        return results;
    }

    @Override
    public void putItem(DataEntry entry) {
        table.putItem(entry);
        log.debug("Wrote item: pk={} sk={}", entry.getPartitionKey(), entry.getSortKey());
    }

    @Override
    public void putItems(List<DataEntry> entries) {
        var writeBatch = WriteBatch.builder(DataEntry.class).mappedTableResource(table);
        entries.forEach(e -> writeBatch.addPutItem(r -> r.item(e)));

        // DynamoDB batch write supports max 25 items per call
        List<DataEntry> buffer = new ArrayList<>();
        for (DataEntry entry : entries) {
            buffer.add(entry);
            if (buffer.size() == 25) {
                flushBatch(buffer);
                buffer.clear();
            }
        }
        if (!buffer.isEmpty()) {
            flushBatch(buffer);
        }
        log.info("Batch wrote {} items", entries.size());
    }

    private void flushBatch(List<DataEntry> batch) {
        // Use enhanced client batch write
        batch.forEach(table::putItem);
    }

    @Override
    public void deleteItem(String row, String columnFamily, String columnQualifier) {
        Key key = Key.builder()
                .partitionValue(row)
                .sortValue(columnFamily + "#" + columnQualifier)
                .build();
        table.deleteItem(key);
        log.debug("Deleted item: pk={} sk={}#{}", row, columnFamily, columnQualifier);
    }

    @Override
    public void createTableIfNotExists() {
        try {
            dynamoDbClient.describeTable(r -> r.tableName(tableName));
            log.info("Table '{}' already exists", tableName);
        } catch (ResourceNotFoundException e) {
            log.info("Creating table '{}'", tableName);
            dynamoDbClient.createTable(CreateTableRequest.builder()
                    .tableName(tableName)
                    .attributeDefinitions(
                            AttributeDefinition.builder()
                                    .attributeName("partitionKey")
                                    .attributeType(ScalarAttributeType.S)
                                    .build(),
                            AttributeDefinition.builder()
                                    .attributeName("sortKey")
                                    .attributeType(ScalarAttributeType.S)
                                    .build()
                    )
                    .keySchema(
                            KeySchemaElement.builder()
                                    .attributeName("partitionKey")
                                    .keyType(KeyType.HASH)
                                    .build(),
                            KeySchemaElement.builder()
                                    .attributeName("sortKey")
                                    .keyType(KeyType.RANGE)
                                    .build()
                    )
                    .billingMode(BillingMode.PAY_PER_REQUEST)
                    .build());

            // Wait for table to become active
            dynamoDbClient.waiter().waitUntilTableExists(r -> r.tableName(tableName));
            log.info("Table '{}' created successfully", tableName);
        }
    }
}
