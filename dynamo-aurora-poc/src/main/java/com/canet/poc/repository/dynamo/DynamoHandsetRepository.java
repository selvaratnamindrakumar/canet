package com.canet.poc.repository.dynamo;

import com.canet.poc.model.HandsetRecord;
import com.canet.poc.repository.HandsetRepository;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Repository;
import software.amazon.awssdk.enhanced.dynamodb.*;
import software.amazon.awssdk.enhanced.dynamodb.model.QueryConditional;
import software.amazon.awssdk.enhanced.dynamodb.model.ScanEnhancedRequest;
import software.amazon.awssdk.services.dynamodb.DynamoDbClient;
import software.amazon.awssdk.services.dynamodb.model.*;

import java.util.*;
import java.util.stream.Collectors;

@Slf4j
@Repository("dynamoHandsetRepository")
public class DynamoHandsetRepository implements HandsetRepository {

    private final DynamoDbTable<HandsetItem> table;
    private final DynamoDbClient rawClient;
    private final String tableName;

    public DynamoHandsetRepository(
            DynamoDbEnhancedClient enhancedClient,
            DynamoDbClient rawClient,
            @Value("${poc.dynamo.table-name:handset-reference}") String tableName) {
        this.rawClient = rawClient;
        this.tableName = tableName;
        this.table = enhancedClient.table(tableName, TableSchema.fromBean(HandsetItem.class));
    }

    @Override
    public Optional<HandsetRecord> findByCgi(String cgi) {
        HandsetItem item = table.getItem(Key.builder().partitionValue(cgi).build());
        return Optional.ofNullable(item).map(this::toDomain);
    }

    /**
     * DynamoDB projection: use ProjectionExpression to fetch only requested attributes,
     * reducing read capacity units consumed and response payload size.
     */
    @Override
    public Optional<HandsetRecord> findByCgiWithFields(String cgi, List<String> fields) {
        if (fields == null || fields.isEmpty()) {
            return findByCgi(cgi);
        }
        // Always include PK
        Set<String> requested = new LinkedHashSet<>(fields);
        requested.add("cgi");

        // Build expression attribute name aliases (DynamoDB reserved word safety)
        Map<String, String> nameAliases = new HashMap<>();
        List<String> projParts = new ArrayList<>();
        int i = 0;
        for (String f : requested) {
            String alias = "#f" + i++;
            nameAliases.put(alias, f);
            projParts.add(alias);
        }

        GetItemResponse response = rawClient.getItem(r -> r
                .tableName(tableName)
                .key(Map.of("cgi", AttributeValue.fromS(cgi)))
                .projectionExpression(String.join(", ", projParts))
                .expressionAttributeNames(nameAliases));

        if (!response.hasItem() || response.item().isEmpty()) {
            return Optional.empty();
        }
        return Optional.of(itemMapToDomain(response.item()));
    }

    @Override
    public List<HandsetRecord> findByTechnology(String technology) {
        // Full scan with filter — in production add a GSI on technology
        return table.scan(ScanEnhancedRequest.builder()
                        .filterExpression(Expression.builder()
                                .expression("technology = :tech")
                                .expressionValues(Map.of(":tech", AttributeValue.fromS(technology)))
                                .build())
                        .build())
                .items().stream().map(this::toDomain).collect(Collectors.toList());
    }

    @Override
    public List<HandsetRecord> findByRegion(String region) {
        return table.scan(ScanEnhancedRequest.builder()
                        .filterExpression(Expression.builder()
                                .expression("#r = :region")
                                .expressionValues(Map.of(":region", AttributeValue.fromS(region)))
                                .expressionNames(Map.of("#r", "region"))
                                .build())
                        .build())
                .items().stream().map(this::toDomain).collect(Collectors.toList());
    }

    @Override
    public List<HandsetRecord> findByManufacturer(String manufacturer) {
        return table.scan(ScanEnhancedRequest.builder()
                        .filterExpression(Expression.builder()
                                .expression("manufacturer = :mfr")
                                .expressionValues(Map.of(":mfr", AttributeValue.fromS(manufacturer)))
                                .build())
                        .build())
                .items().stream().map(this::toDomain).collect(Collectors.toList());
    }

    @Override
    public List<HandsetRecord> findByRegionAndTechnology(String region, String technology) {
        return table.scan(ScanEnhancedRequest.builder()
                        .filterExpression(Expression.builder()
                                .expression("#r = :region AND technology = :tech")
                                .expressionValues(Map.of(
                                        ":region", AttributeValue.fromS(region),
                                        ":tech",   AttributeValue.fromS(technology)))
                                .expressionNames(Map.of("#r", "region"))
                                .build())
                        .build())
                .items().stream().map(this::toDomain).collect(Collectors.toList());
    }

    @Override
    public void save(HandsetRecord record) {
        table.putItem(toItem(record));
    }

    @Override
    public void saveAll(List<HandsetRecord> records) {
        records.forEach(r -> table.putItem(toItem(r)));
    }

    @Override
    public List<HandsetRecord> findAll() {
        List<HandsetRecord> out = new ArrayList<>();
        table.scan().items().forEach(i -> out.add(toDomain(i)));
        return out;
    }

    @Override
    public void deleteAll() {
        List<HandsetRecord> all = findAll();
        all.forEach(r -> table.deleteItem(Key.builder().partitionValue(r.getCgi()).build()));
    }

    @Override
    public String backendName() { return "DynamoDB"; }

    // ── Table management ──────────────────────────────────────────────────────

    public void createTableIfNotExists() {
        try {
            rawClient.describeTable(r -> r.tableName(tableName));
            log.info("DynamoDB table '{}' already exists", tableName);
        } catch (ResourceNotFoundException e) {
            log.info("Creating DynamoDB table '{}'", tableName);
            rawClient.createTable(CreateTableRequest.builder()
                    .tableName(tableName)
                    .attributeDefinitions(
                            AttributeDefinition.builder().attributeName("cgi").attributeType(ScalarAttributeType.S).build()
                    )
                    .keySchema(
                            KeySchemaElement.builder().attributeName("cgi").keyType(KeyType.HASH).build()
                    )
                    .billingMode(BillingMode.PAY_PER_REQUEST)
                    .build());
            rawClient.waiter().waitUntilTableExists(r -> r.tableName(tableName));
            log.info("DynamoDB table '{}' ready", tableName);
        }
    }

    // ── Mapping ───────────────────────────────────────────────────────────────

    private HandsetItem toItem(HandsetRecord r) {
        HandsetItem i = new HandsetItem();
        i.setCgi(r.getCgi());
        i.setManufacturer(r.getManufacturer());
        i.setModel(r.getModel());
        i.setTechnology(r.getTechnology());
        i.setImei(r.getImei());
        i.setOperatingSystem(r.getOperatingSystem());
        i.setLatitude(r.getLatitude());
        i.setLongitude(r.getLongitude());
        i.setCellId(r.getCellId());
        i.setLac(r.getLac());
        i.setMcc(r.getMcc());
        i.setMnc(r.getMnc());
        i.setCountry(r.getCountry());
        i.setRegion(r.getRegion());
        i.setSignalStrength(r.getSignalStrength());
        i.setNetworkType(r.getNetworkType());
        i.setIsp(r.getIsp());
        i.setSubscriberTier(r.getSubscriberTier());
        i.setAdditionalAttributes(r.getAdditionalAttributes());
        i.setLastUpdatedEpochMs(r.getLastUpdatedEpochMs());
        i.setDataSource(r.getDataSource());
        return i;
    }

    private HandsetRecord toDomain(HandsetItem i) {
        return HandsetRecord.builder()
                .cgi(i.getCgi())
                .manufacturer(i.getManufacturer())
                .model(i.getModel())
                .technology(i.getTechnology())
                .imei(i.getImei())
                .operatingSystem(i.getOperatingSystem())
                .latitude(i.getLatitude())
                .longitude(i.getLongitude())
                .cellId(i.getCellId())
                .lac(i.getLac())
                .mcc(i.getMcc())
                .mnc(i.getMnc())
                .country(i.getCountry())
                .region(i.getRegion())
                .signalStrength(i.getSignalStrength())
                .networkType(i.getNetworkType())
                .isp(i.getIsp())
                .subscriberTier(i.getSubscriberTier())
                .additionalAttributes(i.getAdditionalAttributes())
                .lastUpdatedEpochMs(i.getLastUpdatedEpochMs())
                .dataSource(i.getDataSource())
                .build();
    }

    private HandsetRecord itemMapToDomain(Map<String, AttributeValue> m) {
        return HandsetRecord.builder()
                .cgi(str(m, "cgi"))
                .manufacturer(str(m, "manufacturer"))
                .model(str(m, "model"))
                .technology(str(m, "technology"))
                .imei(str(m, "imei"))
                .operatingSystem(str(m, "operatingSystem"))
                .latitude(dbl(m, "latitude"))
                .longitude(dbl(m, "longitude"))
                .cellId(str(m, "cellId"))
                .lac(str(m, "lac"))
                .mcc(str(m, "mcc"))
                .mnc(str(m, "mnc"))
                .country(str(m, "country"))
                .region(str(m, "region"))
                .signalStrength(str(m, "signalStrength"))
                .networkType(str(m, "networkType"))
                .isp(str(m, "isp"))
                .subscriberTier(str(m, "subscriberTier"))
                .dataSource(str(m, "dataSource"))
                .build();
    }

    private String str(Map<String, AttributeValue> m, String k) {
        AttributeValue v = m.get(k);
        return v != null ? v.s() : null;
    }
    private Double dbl(Map<String, AttributeValue> m, String k) {
        AttributeValue v = m.get(k);
        return (v != null && v.n() != null) ? Double.parseDouble(v.n()) : null;
    }
}
