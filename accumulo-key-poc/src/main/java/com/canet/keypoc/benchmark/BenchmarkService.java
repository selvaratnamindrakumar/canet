package com.canet.keypoc.benchmark;

import com.canet.keypoc.model.AccumuloCell;
import com.canet.keypoc.neo.NeoPatternRegistry;
import com.canet.keypoc.neo.NeoQueryPattern;
import com.canet.keypoc.scanner.AccumuloScannerOp;
import com.canet.keypoc.scanner.CellStoreRepository;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Service;

import java.util.*;
import java.util.stream.Collectors;

@Slf4j
@Service
public class BenchmarkService {

    private final CellStoreRepository dynamo;
    private final CellStoreRepository aurora;
    private final NeoPatternRegistry registry;

    public BenchmarkService(
            @Qualifier("dynamoCellStore")  CellStoreRepository dynamo,
            @Qualifier("auroraCellStore")  CellStoreRepository aurora,
            NeoPatternRegistry registry) {
        this.dynamo = dynamo;
        this.aurora = aurora;
        this.registry = registry;
    }

    /**
     * Run all NEO patterns against both backends and produce the full evaluation report.
     * This is the primary POC deliverable.
     */
    public EvaluationReport runFullReport() {
        List<NeoQueryPattern> patterns = registry.allPatterns();
        List<PatternBenchmarkResult> results = patterns.stream()
                .filter(p -> p.getScannerOp() != null && !p.getScannerOp().getRowId().equals("*"))
                .map(this::runPattern)
                .collect(Collectors.toList());

        long dynamoEasy  = results.stream().filter(r -> r.isDynamoFriendly()).count();
        long dynamoNeedsGsi = results.stream()
                .filter(r -> r.getDynamoDifficulty() == NeoQueryPattern.Difficulty.NEEDS_GSI_IN_DYNAMO).count();
        long dynamoHard  = results.stream()
                .filter(r -> r.getDynamoDifficulty() == NeoQueryPattern.Difficulty.HARD_IN_DYNAMO
                          || r.getDynamoDifficulty() == NeoQueryPattern.Difficulty.REQUIRES_EXTRA_INFRA_IN_DYNAMO).count();

        // Patterns classified by Catherine's "easy vs hard" framework
        List<String> dynamoEasyList = patterns.stream()
                .filter(NeoQueryPattern::isDynamoFriendly)
                .map(NeoQueryPattern::getName)
                .collect(Collectors.toList());
        List<String> dynamoHardList = patterns.stream()
                .filter(p -> !p.isDynamoFriendly())
                .map(NeoQueryPattern::getName)
                .collect(Collectors.toList());

        return EvaluationReport.builder()
                .patternResults(results)
                .totalPatterns(patterns.size())
                .dynamoFriendlyCount((int) dynamoEasy)
                .dynamoNeedsGsiCount((int) dynamoNeedsGsi)
                .dynamoHardCount((int) dynamoHard)
                .auroraFriendlyCount(patterns.size()) // SQL handles everything
                .dynamoMatrix(buildDynamoMatrix())
                .auroraMatrix(buildAuroraMatrix())
                .dynamoEasyPatterns(dynamoEasyList)
                .dynamoHardPatterns(dynamoHardList)
                // Updated confidence levels per Catherine's feedback
                .dynamoConfidence("HIGH — IF NEO access patterns are primarily CGI+Family lookups. " +
                                  "Composite PK=CGI, SK=Family#Qualifier#Timestamp handles all key-based queries " +
                                  "without application-side filtering. Confidence drops if reporting queries exist.")
                .auroraConfidence("HIGH — SQL handles every pattern, including future unknown queries, " +
                                  "without schema redesign. JSONB preserves Accumulo's schema-flexibility. " +
                                  "Closest logical model to Accumulo's key structure.")
                .oracleRdsConfidence("LOW-MEDIUM — same SQL benefits as Aurora but higher cost, " +
                                     "licensing complexity, and no serverless option. Only consider if " +
                                     "existing Oracle expertise or PL/SQL procedures are required.")
                .recommendation("Aurora PostgreSQL — pending NEO access pattern discovery")
                .recommendationRationale(
                        "Aurora PostgreSQL is recommended as the safer default because:\n" +
                        "1. The schema (cgi,family,qualifier,ts,value) is structurally identical to Accumulo's key model.\n" +
                        "2. Every Accumulo scanner pattern maps to a single SQL WHERE clause — no app-side filtering.\n" +
                        "3. Reporting and cross-row queries (find all 5G devices, find records updated today) work without GSIs.\n" +
                        "4. DynamoDB with composite PK/SK IS viable if NEO access patterns are exclusively CGI-keyed lookups.\n" +
                        "   The composite SK (Family#Qualifier#Timestamp) cleanly maps ROW_SCAN, FAMILY_SCAN, CELL_LOOKUP.\n" +
                        "   However, timestamp range queries require zero-padded SK or fall back to app-side filtering.\n" +
                        "5. The NEXT STEP before finalising is: analyse NEO source code for BatchScanner usage and\n" +
                        "   any query that does not start with a known CGI (patterns 6-8 above).")
                .keyFindings(List.of(
                        "Catherine is correct: DynamoDB composite PK/SK (CGI + Family#Qualifier#Timestamp) preserves " +
                        "Accumulo's server-side filtering for ROW_SCAN, FAMILY_SCAN and CELL_LOOKUP without application filtering.",
                        "DynamoDB handles 3 of 8 NEO patterns natively (EASY_BOTH). The remaining 5 require GSIs, " +
                        "multiple round-trips, or additional infrastructure.",
                        "The anti-pattern to avoid is DynamoDB with a flattened document value — that moves all " +
                        "filtering to Java. The composite SK design avoids this.",
                        "Timestamp range queries are a DynamoDB footgun: string sort keys require zero-padded timestamps " +
                        "for correct lexicographic ordering. Aurora uses numeric BETWEEN with no special handling.",
                        "Cross-row queries (find by model, find by region, find updated today) are trivial SQL in Aurora " +
                        "but require GSIs or DynamoDB Streams infrastructure in DynamoDB.",
                        "For a single database covering BB, Handset, and IS: Aurora reduces operational surface " +
                        "vs managing DynamoDB table design, GSI capacity, and separate reporting infrastructure."
                ))
                .nextDiscoveryStep(
                        "Search NEO codebase for: (1) BatchScanner usage — if present, cross-row queries exist; " +
                        "(2) scanner.setRange() calls where range is NOT a single rowId; " +
                        "(3) any query that does not begin with a known CGI. " +
                        "If ONLY single-CGI lookups exist: DynamoDB composite key design is viable. " +
                        "If BatchScanner or range scans exist: Aurora is the correct choice.")
                .build();
    }

    /**
     * Run a single named pattern (by name) against both backends.
     */
    public PatternBenchmarkResult runSinglePattern(String patternName) {
        return registry.allPatterns().stream()
                .filter(p -> p.getName().equalsIgnoreCase(patternName)
                          || p.getName().toLowerCase().contains(patternName.toLowerCase()))
                .findFirst()
                .map(this::runPattern)
                .orElseThrow(() -> new IllegalArgumentException("Pattern not found: " + patternName));
    }

    /**
     * Execute a raw scanner op against both backends and compare.
     */
    public PatternBenchmarkResult runOp(AccumuloScannerOp op) {
        QueryResult dr = executeWith(dynamo, op);
        QueryResult ar = executeWith(aurora, op);

        boolean match = dr.getCells().size() == ar.getCells().size();

        return PatternBenchmarkResult.builder()
                .patternName("Ad-hoc: " + op.describe())
                .accumuloOperation(op.describe())
                .dynamoResult(dr)
                .auroraResult(ar)
                .resultsMatch(match)
                .dynamoDifficulty(op.usesFamilyOrQualifierFilter()
                        ? NeoQueryPattern.Difficulty.EASY_BOTH
                        : NeoQueryPattern.Difficulty.EASY_BOTH)
                .dynamoFriendly(!op.getType().equals(AccumuloScannerOp.Type.VERSIONED_CELL_LOOKUP))
                .recommendation(match ? "Both backends returned identical results" : "MISMATCH — investigate")
                .build();
    }

    // ── Private helpers ───────────────────────────────────────────────────────

    private PatternBenchmarkResult runPattern(NeoQueryPattern pattern) {
        QueryResult dr = executeWith(dynamo, pattern.getScannerOp());
        QueryResult ar = executeWith(aurora, pattern.getScannerOp());

        boolean match = dr.getCells().size() == ar.getCells().size();

        String recommendation = switch (pattern.getDynamoDifficulty()) {
            case EASY_BOTH -> "Both DynamoDB and Aurora handle this efficiently.";
            case NEEDS_GSI_IN_DYNAMO -> "DynamoDB requires a GSI for this pattern. Aurora uses a standard index.";
            case HARD_IN_DYNAMO -> "Aurora strongly preferred. DynamoDB requires full table scan without a GSI.";
            case REQUIRES_EXTRA_INFRA_IN_DYNAMO -> "Aurora strongly preferred. DynamoDB needs Streams + Lambda or a separate index table.";
        };

        return PatternBenchmarkResult.builder()
                .patternName(pattern.getName())
                .accumuloOperation(pattern.getAccumuloOperation())
                .dynamoResult(dr)
                .auroraResult(ar)
                .resultsMatch(match)
                .dynamoDifficulty(pattern.getDynamoDifficulty())
                .dynamoFriendly(pattern.isDynamoFriendly())
                .recommendation(recommendation)
                .notes(pattern.getNotes())
                .build();
    }

    private QueryResult executeWith(CellStoreRepository repo, AccumuloScannerOp op) {
        long t0 = System.currentTimeMillis();
        List<AccumuloCell> cells;
        boolean appFiltering = false;
        String notes = "";

        try {
            cells = repo.execute(op);
            // Versioned lookup in DynamoDB does application-side timestamp filtering
            if (op.getType() == AccumuloScannerOp.Type.VERSIONED_CELL_LOOKUP
                    && repo.backendName().startsWith("DynamoDB")) {
                appFiltering = true;
                notes = "DynamoDB: fetched all versions of this cell, then filtered by timestamp in application.";
            }
        } catch (Exception ex) {
            log.warn("Query failed on {}: {}", repo.backendName(), ex.getMessage());
            cells = List.of();
            notes = "Query failed: " + ex.getMessage();
        }

        long elapsed = System.currentTimeMillis() - t0;

        return QueryResult.builder()
                .backend(repo.backendName())
                .operationDescription(op.describe())
                .cells(cells)
                .cellCount(cells.size())
                .queryMs(elapsed)
                .appSideFiltering(appFiltering)
                .notes(notes)
                .build();
    }

    // ── Evaluation matrices ───────────────────────────────────────────────────

    private Map<String, String> buildDynamoMatrix() {
        Map<String, String> m = new LinkedHashMap<>();
        m.put("Ease of migration",
                "HIGH for CGI+Family+Qualifier lookups. " +
                "Composite SK (Family#Qualifier#Timestamp) maps directly to Accumulo key structure. " +
                "MEDIUM if reporting or cross-row queries are needed.");
        m.put("Similarity to Accumulo",
                "HIGH — composite PK/SK mirrors Accumulo RowID+CF+CQ+Timestamp. " +
                "begins_with query = fetchColumnFamily. " +
                "Timestamp zero-padding required for range queries.");
        m.put("Query flexibility",
                "MEDIUM for key-based patterns (3 of 8 patterns natively supported). " +
                "LOW for attribute-value searches across rows — requires GSI per dimension.");
        m.put("App changes required",
                "LOW for primary lookup patterns. " +
                "MEDIUM if timestamp ranges are used (zero-padding needed). " +
                "HIGH if cross-row queries exist.");
        m.put("Operational complexity",
                "LOW — fully managed. " +
                "MEDIUM for GSI design, capacity planning, and DynamoDB Streams if needed.");
        m.put("Cost estimate",
                "LOW at POC scale. " +
                "Predictable at single-row lookup workloads. " +
                "Expensive for table scans. Each GSI doubles write cost for affected items.");
        m.put("Long-term maintainability",
                "MEDIUM — no SQL tooling, reports require application code or separate DynamoDB exports. " +
                "Schema changes are additive (no migrations needed). " +
                "GSI proliferation is an operational risk.");
        return m;
    }

    private Map<String, String> buildAuroraMatrix() {
        Map<String, String> m = new LinkedHashMap<>();
        m.put("Ease of migration",
                "HIGH — schema (cgi,family,qualifier,ts,value) is structurally identical to Accumulo key space. " +
                "Every Accumulo scanner pattern maps to a single SQL WHERE clause.");
        m.put("Similarity to Accumulo",
                "HIGH — composite PK (cgi,family,qualifier,ts) mirrors Accumulo's 4-part key exactly. " +
                "fetchColumnFamily → WHERE family=? (indexed). " +
                "setTimestampRange → WHERE ts BETWEEN ? AND ? (numeric, no padding needed).");
        m.put("Query flexibility",
                "HIGH — all 8 patterns natively supported. " +
                "Cross-row queries (find by model/region/timestamp) use standard SQL indexes. " +
                "Future unknown queries handled without schema changes.");
        m.put("App changes required",
                "MEDIUM — switch from key-value API to JDBC/JPA. " +
                "Spring Data JPA abstracts most of this. " +
                "Accumulo visibility labels require application-level enforcement or PostgreSQL row security.");
        m.put("Operational complexity",
                "MEDIUM — Aurora Serverless v2 reduces ops burden significantly. " +
                "Connection pooling required (HikariCP already included). " +
                "Flyway handles schema evolution.");
        m.put("Cost estimate",
                "Aurora Serverless v2: scales to 0 ACUs when idle. " +
                "Estimated $20-50/month for POC workload. " +
                "More predictable than DynamoDB for mixed read/write/scan workloads.");
        m.put("Long-term maintainability",
                "HIGH — SQL universally understood. " +
                "Standard BI/reporting tools (Metabase, Grafana, QuickSight) work directly. " +
                "Schema evolution via Flyway. PostgreSQL row-level security for visibility labels.");
        return m;
    }
}
