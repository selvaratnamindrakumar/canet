# Cloud Migration – Database Evaluation POC

**Objective:** Validate DynamoDB (key-value) vs Aurora PostgreSQL (relational) as Accumulo replacements
by modelling BB/IS handset reference data and executing representative NEO API queries against both.

---

## Accumulo → Database Mapping

| Accumulo                        | DynamoDB                    | Aurora PostgreSQL              |
|---------------------------------|-----------------------------|--------------------------------|
| Row key (CGI)                   | Partition Key               | `cgi` PRIMARY KEY              |
| Column family: `device`         | Top-level attributes        | `manufacturer`, `model`, etc.  |
| Column family: `location`       | Top-level attributes        | `latitude`, `longitude`, etc.  |
| Column family: `enrichment`     | Top-level attributes        | `signal_strength`, `isp`, etc. |
| Arbitrary column qualifiers     | `additionalAttributes` Map  | `additional_attributes` JSONB  |
| Visibility label                | Application-enforced        | Row-level security (future)    |

---

## Sample Dataset (5 Records)

| CGI                   | Manufacturer    | Model           | Tech | Region     | Data Source |
|-----------------------|-----------------|-----------------|------|------------|-------------|
| 234-10-1234-56789     | Samsung         | Galaxy S23      | 4G   | London     | BB          |
| 234-20-2345-67890     | Apple           | iPhone 15 Pro   | 5G   | Manchester | IS          |
| 234-30-3456-78901     | Nokia           | 3310 3G         | 3G   | Birmingham | BB          |
| 234-50-4567-89012     | Google          | Pixel 8         | 4G   | Scotland   | BB          |
| 234-10-1234-11111     | Sierra Wireless | RV55            | 2G   | London     | IS (IoT)    |

---

## Quick Start — Local

### 1. Start both databases

```bash
docker-compose up -d
```

| Service           | URL                     | Credentials         |
|-------------------|-------------------------|---------------------|
| DynamoDB Local    | http://localhost:8000   | any                 |
| DynamoDB Admin UI | http://localhost:8001   | —                   |
| PostgreSQL        | localhost:5432/pocdb    | poc / poc           |
| pgAdmin           | http://localhost:5050   | admin@poc.local / admin |

### 2. Run the app

```bash
./mvnw spring-boot:run -Dspring-boot.run.profiles=local
```

The `local` profile automatically:
- Creates the DynamoDB `handset-reference` table
- Runs Flyway to create the `handset_reference` PostgreSQL table
- Seeds all 5 sample records into **both** backends

---

## API Endpoints

### NEO API Simulation

All endpoints accept `?backend=dynamo|aurora` to choose the backend. Default: `dynamo`.

```bash
# Primary lookup: CGI → full record (both backends)
GET /api/neo/handset/{cgi}?backend=dynamo
GET /api/neo/handset/{cgi}?backend=aurora

# Projected lookup: return only specific fields
GET /api/neo/handset/{cgi}/fields?f=manufacturer,model,technology&backend=dynamo

# Secondary patterns (note scan vs index difference)
GET /api/neo/handsets/technology/4G?backend=dynamo      # DynamoDB: full-table scan
GET /api/neo/handsets/technology/4G?backend=aurora      # Aurora: index scan

GET /api/neo/handsets/region/London?backend=aurora
GET /api/neo/handsets/region/London/technology/4G?backend=aurora
```

### Side-by-Side Comparison

```bash
# Run same query against both backends — returns latency + full evaluation matrix
GET /api/compare/{cgi}
```

Example:
```bash
curl http://localhost:8080/api/compare/234-10-1234-56789 | jq .
```

Returns:
```json
{
  "cgi": "234-10-1234-56789",
  "dynamoDbResult": { "manufacturer": "Samsung", "networkTier": "PREMIUM", ... },
  "auroraResult":   { "manufacturer": "Samsung", "networkTier": "PREMIUM", ... },
  "resultsMatch": true,
  "dynamoDbQueryMs": 3,
  "auroraQueryMs": 2,
  "evaluation": {
    "dynamoQueryFlexibility": "LOW — secondary patterns require GSIs or full-table scans...",
    "auroraQueryFlexibility": "HIGH — SQL handles any query pattern with indexes...",
    "recommendation": "Aurora PostgreSQL",
    "recommendationReasons": [ ... ]
  }
}
```

### Admin

```bash
POST /api/admin/init               # Create tables + seed data
GET  /api/admin/data?backend=dynamo|aurora
DELETE /api/admin/data?backend=dynamo|aurora
```

---

## Evaluation Matrix

| Criterion                    | DynamoDB                                      | Aurora PostgreSQL                              |
|------------------------------|-----------------------------------------------|------------------------------------------------|
| **Ease of migration**        | HIGH — CGI maps directly to partition key     | MEDIUM — schema design needed, JSONB covers extras |
| **Similarity to Accumulo**   | HIGH — GetItem mirrors Accumulo row lookup    | LOW-MEDIUM — SQL is a different paradigm       |
| **Query flexibility**        | LOW — new access patterns need new GSIs       | HIGH — SQL + indexes handle any pattern        |
| **App changes required**     | LOW for CGI lookup only                       | MEDIUM — JDBC/JPA vs key-value API             |
| **Operational complexity**   | LOW — fully managed, no servers               | MEDIUM — VPC, connections, Aurora Serverless v2 |
| **Cost estimate**            | Low at POC; GSIs + scans expensive at scale   | ~$50/mo (t4g.medium); Serverless v2 scales to zero |
| **Long-term maintainability**| MEDIUM — no SQL tooling, joins need app code  | HIGH — SQL universal, BI tools, Flyway schema mgmt |

### Recommendation: **Aurora PostgreSQL**

Reasons:
1. The NEO API already has secondary access patterns beyond CGI lookup (technology, region, manufacturer). Each requires a DynamoDB GSI; Aurora uses standard SQL indexes.
2. Aurora's `SELECT` projection returns only required columns; DynamoDB reads and charges for the full item even with `ProjectionExpression`.
3. `JSONB` column covers arbitrary Accumulo column qualifiers without schema changes — best of both worlds.
4. SQL is universally understood; ad-hoc reporting works without application code.
5. Aurora Serverless v2 scales to zero when idle, making it cost-competitive at variable workloads.
6. **Single database** covers BB, Handset, and IS datasets — reduces operational complexity vs managing DynamoDB GSI design + Aurora schema.

> **DynamoDB is the right choice only if:** the access pattern is exclusively CGI lookup at >10k TPS with zero secondary queries — which does not match the current NEO API pattern.

---

## Project Structure

```
src/main/java/com/canet/poc/
├── PocApplication.java
├── model/
│   ├── HandsetRecord.java          # Canonical domain object (backend-agnostic)
│   ├── NeoApiResponse.java         # API response shape (post-retrieval fields)
│   └── ComparisonResult.java       # Side-by-side evaluation result
├── repository/
│   ├── HandsetRepository.java      # Shared interface (both backends satisfy this)
│   ├── dynamo/
│   │   ├── HandsetItem.java        # DynamoDB @DynamoDbBean item
│   │   └── DynamoHandsetRepository.java
│   └── postgres/
│       ├── HandsetEntity.java      # JPA @Entity with JSONB column
│       ├── HandsetJpaRepository.java
│       ├── HandsetProjection.java  # Partial SELECT projection
│       └── AuroraHandsetRepository.java
├── neo/
│   └── NeoApiService.java          # Post-retrieval business logic (backend-agnostic)
├── benchmark/
│   └── ComparisonService.java      # Runs both + builds evaluation matrix
├── service/
│   └── SeedDataService.java        # Seeds 5 BB/IS records into both backends
├── controller/
│   ├── NeoApiController.java       # /api/neo/** endpoints
│   ├── ComparisonController.java   # /api/compare/{cgi}
│   └── AdminController.java        # /api/admin/**
└── config/
    └── DynamoDbConfig.java

src/main/resources/
├── db/migration/
│   └── V1__create_handset_reference.sql   # Aurora schema + indexes
├── application.properties
├── application-local.properties           # DynamoDB Local + local Postgres
└── application-aws.properties             # AWS Sandbox (Aurora endpoint + credentials)
```

---

## AWS Sandbox Deployment

```bash
# Export credentials
export AWS_REGION=us-east-1
export AWS_ACCESS_KEY_ID=...
export AWS_SECRET_ACCESS_KEY=...
export AURORA_ENDPOINT=your-cluster.cluster-xxx.us-east-1.rds.amazonaws.com
export AURORA_USER=poc
export AURORA_PASSWORD=...

# Run against real AWS
./mvnw spring-boot:run -Dspring-boot.run.profiles=aws

# One-time: create DynamoDB table and seed data
curl -X POST http://localhost:8080/api/admin/init
```

### Aurora Serverless v2 (recommended for POC)

```bash
aws rds create-db-cluster \
  --db-cluster-identifier poc-handset \
  --engine aurora-postgresql \
  --engine-version 15.4 \
  --serverless-v2-scaling-configuration MinCapacity=0.5,MaxCapacity=4 \
  --master-username poc \
  --master-user-password <password> \
  --region us-east-1
```

Scales to zero when idle — ideal for POC evaluation.
