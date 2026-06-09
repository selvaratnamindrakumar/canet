# Accumulo → Amazon DynamoDB Migration Prototype

Spring Boot 3.2 / Java 17 app that demonstrates migrating from Apache Accumulo to Amazon DynamoDB.

## Accumulo Key → DynamoDB Mapping

Accumulo stores data as `(row, columnFamily, columnQualifier, visibility, timestamp) → value`.
DynamoDB uses a two-key model. The mapping is:

| Accumulo             | DynamoDB                         |
|----------------------|----------------------------------|
| `row`                | Partition Key (`partitionKey`)   |
| `colFamily#colQual`  | Sort Key (`sortKey`)             |
| `columnVisibility`   | Attribute (`columnVisibility`)   |
| `timestamp`          | Attribute (`timestamp`)          |
| `value`              | Attribute (`value`)              |

The composite sort key `colFamily#colQualifier` lets you:
- Query all entries for a row → `QueryConditional.keyEqualTo(row)`
- Filter by column family → `QueryConditional.sortBeginsWith(row, "family#")`

---

## Quick Start — Local Development

### 1. Start DynamoDB Local

```bash
docker-compose up -d
```

- DynamoDB Local: http://localhost:8000
- DynamoDB Admin UI: http://localhost:8001

### 2. Run the app (local profile auto-creates table + seeds data)

```bash
./mvnw spring-boot:run -Dspring-boot.run.profiles=local
```

### 3. Test the endpoints

```bash
# Scan all rows
curl http://localhost:8080/api/data/scan

# Get all cells for a row (Accumulo row-scan equivalent)
curl http://localhost:8080/api/data/row/user:alice

# Get by column family (Accumulo setFetchColumnFamily)
curl http://localhost:8080/api/data/row/user:alice/family/profile

# Get a single cell (exact key lookup)
curl http://localhost:8080/api/data/row/user:alice/family/profile/qualifier/email

# Write a new entry (Accumulo-style key/value)
curl -X POST http://localhost:8080/api/data/entry \
  -H "Content-Type: application/json" \
  -d '{"row":"user:carol","columnFamily":"profile","columnQualifier":"name","visibility":"PUBLIC","value":"Carol White"}'

# Delete an entry
curl -X DELETE http://localhost:8080/api/data/row/user:alice/family/profile/qualifier/email
```

---

## AWS Sandbox Deployment

### Prerequisites

- AWS credentials configured (`~/.aws/credentials` or IAM role / env vars)
- `AWS_REGION` set (default: `us-east-1`)

### Create the DynamoDB table

```bash
curl -X POST http://localhost:8080/api/admin/table/init
```

Or via AWS CLI:

```bash
aws dynamodb create-table \
  --table-name accumulo-migration \
  --attribute-definitions \
    AttributeName=partitionKey,AttributeType=S \
    AttributeName=sortKey,AttributeType=S \
  --key-schema \
    AttributeName=partitionKey,KeyType=HASH \
    AttributeName=sortKey,KeyType=RANGE \
  --billing-mode PAY_PER_REQUEST \
  --region us-east-1
```

### Run against AWS

```bash
export AWS_REGION=us-east-1
export AWS_ACCESS_KEY_ID=<your-key>
export AWS_SECRET_ACCESS_KEY=<your-secret>

./mvnw spring-boot:run
```

---

## Project Structure

```
src/main/java/com/canet/migration/
├── MigrationApplication.java          # Spring Boot entry point + local seed data
├── model/
│   ├── AccumuloKey.java               # Mirrors Accumulo 5-part key structure
│   ├── DataEntry.java                 # DynamoDB item + fromAccumulo() / toAccumuloKey()
│   └── ScanRequest.java               # Scan filter parameters
├── repository/
│   ├── DataStoreRepository.java       # Backend-agnostic interface (Accumulo API shape)
│   └── DynamoDbDataStoreRepository.java  # DynamoDB implementation
├── service/
│   └── DataMigrationService.java      # Business logic + migration helpers
├── controller/
│   ├── DataController.java            # CRUD REST endpoints
│   └── AdminController.java           # Table management endpoints
└── config/
    └── DynamoDbConfig.java            # AWS SDK v2 client setup (local + cloud)
```

---

## Migration Path

1. **Phase 1 (now):** Run this app against DynamoDB. Validate the key mapping fits your data model.
2. **Phase 2:** Add an Accumulo reader that feeds `DataEntry.fromAccumulo()` → `service.bulkIngest()` to copy data.
3. **Phase 3:** Run both backends in parallel, compare results, validate parity.
4. **Phase 4:** Cut over to DynamoDB only.

The `DataStoreRepository` interface is the seam — swap the implementation without touching the service or controller layers.

---

## GraalVM Native Image

The app is Spring Boot 3.x / Java 17, compatible with GraalVM native compilation:

```bash
./mvnw -Pnative native:compile
./target/accumulo-dynamo-migration
```

> Add `spring-boot-starter-aot` and GraalVM hints for the AWS SDK if pursuing native image.
