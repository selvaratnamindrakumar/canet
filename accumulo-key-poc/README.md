# Accumulo Key Model POC — DynamoDB vs Aurora PostgreSQL

**Objective:** Prove which database correctly preserves Accumulo's 5-part key lookup mechanism
(RowID, ColumnFamily, ColumnQualifier, Visibility, Timestamp → Value) without moving filtering
to the application layer. Directly addresses Catherine's observation.

---

## The Problem with Naïve Migration

A flattened document model moves filtering from the database to Java:

```java
// ANTI-PATTERN — do not do this
record = dynamo.get(cgi);                       // fetches entire record
for (attribute : record.getAttributes()) {      // application does the filtering
    if (attribute.family.equals("DEVICE")) { ... }
}
```

This loses Accumulo's server-side efficiency. Both DynamoDB and Aurora can avoid this — but only with the right design.

---

## Correct Key Mappings

### DynamoDB — Catherine's composite PK/SK design

```
Accumulo key part   →   DynamoDB
─────────────────────────────────────────────────────
RowID (CGI)         →   Partition Key (PK)
Family              →   Sort Key prefix:  "DEVICE#"
Qualifier           →   Sort Key:         "DEVICE#MODEL#"
Timestamp           →   Sort Key:         "DEVICE#MODEL#1750000000000"
```

**Table layout:**

| PK (rowId)  | SK (sortKey)                  | value    |
|-------------|-------------------------------|----------|
| CGI12345    | DEVICE#MANUFACTURER#1750...   | Samsung  |
| CGI12345    | DEVICE#MODEL#1700000000000    | Galaxy S21 |
| CGI12345    | DEVICE#MODEL#1750000000000    | Galaxy S23 |
| CGI12345    | LOCATION#REGION#1750...       | London   |
| CGI12345    | ENRICHMENT#ISP#1750...        | EE       |

**Accumulo scanner → DynamoDB query:**

| Accumulo                            | DynamoDB                                     |
|-------------------------------------|----------------------------------------------|
| `scanner.setRange(CGI12345)`        | `Query PK='CGI12345'`                        |
| `scanner.fetchColumnFamily("DEVICE")` | `Query PK='CGI12345' SK begins_with 'DEVICE#'` |
| `scanner.fetchColumn("DEVICE","MODEL")` | `Query PK='CGI12345' SK begins_with 'DEVICE#MODEL#'` |
| `scanner.setTimestampRange(a,b)`    | ⚠️ fetch all versions + filter ts in Java (string sort) |

### Aurora PostgreSQL — identical to Accumulo key structure

```sql
CREATE TABLE handset_cells (
    cgi        VARCHAR(60),   -- Accumulo RowID
    family     VARCHAR(50),   -- Accumulo ColumnFamily
    qualifier  VARCHAR(50),   -- Accumulo ColumnQualifier
    ts         BIGINT,        -- Accumulo Timestamp (epoch ms, numeric)
    visibility VARCHAR(100),  -- Accumulo ColumnVisibility
    value      TEXT,
    PRIMARY KEY (cgi, family, qualifier, ts)
);
```

**Accumulo scanner → SQL:**

| Accumulo                               | SQL                                                               |
|----------------------------------------|-------------------------------------------------------------------|
| `scanner.setRange(CGI12345)`           | `WHERE cgi='CGI12345'`                                           |
| `scanner.fetchColumnFamily("DEVICE")`  | `WHERE cgi='CGI12345' AND family='DEVICE'`                       |
| `scanner.fetchColumn("DEVICE","MODEL")`| `WHERE cgi=? AND family='DEVICE' AND qualifier='MODEL'`          |
| `scanner.setTimestampRange(a,b)`       | `WHERE cgi=? AND family=? AND qualifier=? AND ts BETWEEN a AND b` |
| Two families in one call               | `WHERE cgi=? AND family IN ('DEVICE','LOCATION')`                |

---

## Catherine's Easy vs Hard Classification

### Easy in DynamoDB (3 of 8 patterns)

| Pattern | DynamoDB operation | No app filtering? |
|---|---|---|
| CGI full record fetch | `Query PK=CGI12345` | ✅ |
| CGI + column family (e.g. DEVICE) | `Query PK=CGI SK begins_with DEVICE#` | ✅ |
| CGI + exact cell (DEVICE#MODEL) | `Query PK=CGI SK begins_with DEVICE#MODEL#` | ✅ |

### Hard in DynamoDB (5 of 8 patterns)

| Pattern | Why it's hard | Aurora |
|---|---|---|
| Timestamp range on a cell | String SK needs zero-padded ts; falls back to app-filter | `ts BETWEEN a AND b` ✅ |
| Device family + Location family together | Two separate Query calls required | `family IN (...)` ✅ |
| Find all CGIs with MODEL=iPhone16 | Full table scan or GSI on value | Indexed WHERE value=? ✅ |
| Find all CGIs in region=London | GSI or Streams + Lambda | Indexed WHERE value=? ✅ |
| Find CGIs updated in last 24h | No native support; needs Streams/GSI | `WHERE ts >= NOW()-interval` ✅ |

---

## POC Endpoints

### Start locally

```bash
docker-compose up -d
./mvnw spring-boot:run -Dspring-boot.run.profiles=local
curl -X POST http://localhost:8080/api/admin/init
```

### Core NEO query patterns (switch backend with `?backend=dynamo|aurora`)

```bash
# ROW_SCAN: scanner.setRange("CGI12345")
GET /api/poc/row/CGI12345?backend=dynamo

# FAMILY_SCAN: scanner.fetchColumnFamily("DEVICE")
GET /api/poc/row/CGI12345/family/DEVICE?backend=dynamo
GET /api/poc/row/CGI12345/family/DEVICE?backend=aurora

# CELL_LOOKUP: scanner.fetchColumn("DEVICE","MODEL")
GET /api/poc/row/CGI12345/family/DEVICE/qualifier/MODEL?backend=aurora

# VERSIONED: setTimestampRange(1700000000000, 1750000000000)
GET /api/poc/row/CGI12345/family/DEVICE/qualifier/MODEL/versions?from=1700000000000&to=1750000000000&backend=dynamo
GET /api/poc/row/CGI12345/family/DEVICE/qualifier/MODEL/versions?from=1700000000000&to=1750000000000&backend=aurora

# MULTI_FAMILY: two fetchColumnFamily calls
GET /api/poc/row/CGI12345/families?f=DEVICE,LOCATION&backend=dynamo  # 2 queries
GET /api/poc/row/CGI12345/families?f=DEVICE,LOCATION&backend=aurora  # 1 query
```

### Evaluation

```bash
# Full POC report — all 8 patterns, latency, matrix, recommendation
GET /api/report

# Catherine's easy-vs-hard classification
GET /api/dynamo-easy-vs-hard

# All 8 registered NEO access patterns with DynamoDB difficulty ratings
GET /api/patterns

# Side-by-side comparison for a single named pattern
GET /api/compare/CGI device attributes

# Ad-hoc scanner op against both backends
POST /api/compare/op
{"type":"FAMILY_SCAN","rowId":"CGI12345","columnFamily":"DEVICE"}
```

---

## Confidence Assessment

| Option                           | Confidence | Conditions                                                                 |
|----------------------------------|------------|----------------------------------------------------------------------------|
| **DynamoDB (composite PK/SK)**   | **HIGH**   | IF NEO access patterns are exclusively CGI-keyed lookups (patterns 1–3)    |
| **Aurora PostgreSQL**            | **HIGH**   | Works for all 8 patterns. Scales to reporting + future queries. Safer default. |
| Oracle RDS                       | Low-Medium | Same SQL benefits as Aurora, higher cost, no serverless                    |

---

## Key Findings

1. **Catherine is correct:** DynamoDB composite `PK=CGI, SK=Family#Qualifier#Timestamp` correctly maps Accumulo's 3 primary lookup patterns to server-side filtering without application code changes.

2. **The anti-pattern confirmed:** A flattened JSON document model (everything in the value) moves filtering to Java and loses all DB-side efficiency. The composite SK avoids this.

3. **Timestamp ordering is a DynamoDB footgun:** Sort key strings sort lexicographically (`'9' > '10'`). Timestamp range queries require zero-padded 15-digit timestamps in the SK, or fall back to application-side filtering. Aurora's `BETWEEN` is correct on numeric columns without special handling.

4. **Multi-family queries are a meaningful DynamoDB limitation:** Accumulo allows `scanner.fetchColumnFamily(A); scanner.fetchColumnFamily(B)` in one scan. DynamoDB requires two separate Query calls. Aurora uses `family IN (A, B)` — single round-trip.

5. **Cross-row patterns are decisive:** If NEO uses `BatchScanner` or any query that does not start with a known single CGI (find all 5G devices, find recent updates), DynamoDB cannot serve these without GSIs or additional infrastructure. Aurora handles them with standard SQL indexes.

6. **The next discovery task** (as recommended in the POC spec) is to search NEO source code for:
   - `BatchScanner` usage
   - `scanner.setRange()` with a non-exact Range (prefix scan, range scan)
   - Any query parameter that is NOT a CGI

   **If only single-CGI lookups exist** → DynamoDB composite key design is viable and efficient.
   **If BatchScanner or range scans exist** → Aurora is the correct choice.

---

## Next Steps

1. Run `GET /api/report` against both local backends with seeded data — observe latency difference on versioned and multi-family queries.
2. Search NEO codebase: `grep -r "BatchScanner\|setRange\|fetchColumnFamily" src/`
3. Document findings in a `NeoPatternRegistry` entry per discovered call site.
4. Re-run `GET /api/report` with updated patterns to confirm recommendation.
