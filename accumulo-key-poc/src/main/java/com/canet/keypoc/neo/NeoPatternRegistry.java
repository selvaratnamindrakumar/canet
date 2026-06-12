package com.canet.keypoc.neo;

import com.canet.keypoc.scanner.AccumuloScannerOp;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * Catalogue of all known NEO API Accumulo access patterns.
 *
 * This is the central finding of the POC discovery phase.
 * Each entry answers: "what does this NEO call do, and how hard is it to migrate?"
 *
 * In a real engagement you would populate this by:
 *   1. Searching the NEO codebase for Scanner, BatchScanner, fetchColumn, fetchColumnFamily.
 *   2. Running the app with Accumulo audit logging enabled.
 *   3. Interviewing the NEO team about planned future queries.
 *
 * For this POC we model the patterns that are representative of BB/IS data access.
 */
@Component
public class NeoPatternRegistry {

    public List<NeoQueryPattern> allPatterns() {
        return List.of(
            pattern1_cgiFullRecord(),
            pattern2_cgiDeviceFamily(),
            pattern3_cgiModelCell(),
            pattern4_cgiVersionedCell(),
            pattern5_cgiDeviceAndLocation(),
            pattern6_findByModel(),
            pattern7_findByRegion(),
            pattern8_findUpdatedLast24h()
        );
    }

    // ── Pattern 1: Full CGI record ─────────────────────────────────────────────
    private NeoQueryPattern pattern1_cgiFullRecord() {
        return NeoQueryPattern.builder()
            .name("CGI full record fetch")
            .accumuloOperation("scanner.setRange(new Range(\"CGI12345\"))")
            .scannerOp(AccumuloScannerOp.rowScan("CGI12345"))
            .dynamoOperation("Query PK='CGI12345' → all SK values returned")
            .auroraOperation("SELECT * FROM handset_cells WHERE cgi='CGI12345'")
            .requiredFields(List.of("all"))
            .usesFamilyQualifierFilter(false)
            .usesTimestampFilter(false)
            .dynamoDifficulty(NeoQueryPattern.Difficulty.EASY_BOTH)
            .dynamoFriendly(true)
            .postRetrievalLogic("Assemble record from cells; apply visibility filter; derive locationLabel and networkTier")
            .notes("Primary NEO lookup. DynamoDB GetItem/Query by PK is O(1). Aurora index on cgi. Both are fast.")
            .build();
    }

    // ── Pattern 2: Device family only ─────────────────────────────────────────
    private NeoQueryPattern pattern2_cgiDeviceFamily() {
        return NeoQueryPattern.builder()
            .name("CGI device attributes (family filter)")
            .accumuloOperation("scanner.setRange(new Range(\"CGI12345\")); scanner.fetchColumnFamily(\"DEVICE\")")
            .scannerOp(AccumuloScannerOp.familyScan("CGI12345", "DEVICE"))
            .dynamoOperation("Query PK='CGI12345', SK begins_with 'DEVICE#' → only DEVICE cells returned")
            .auroraOperation("SELECT * FROM handset_cells WHERE cgi='CGI12345' AND family='DEVICE'")
            .requiredFields(List.of("manufacturer", "model", "os", "technology"))
            .usesFamilyQualifierFilter(true)
            .usesTimestampFilter(false)
            .dynamoDifficulty(NeoQueryPattern.Difficulty.EASY_BOTH)
            .dynamoFriendly(true)
            .postRetrievalLogic("Build DeviceInfo object from cells. No further filtering needed.")
            .notes("DynamoDB SK begins_with is a server-side operation — no application filtering. " +
                   "This is Catherine's key pattern: Family IS used as a filter dimension. " +
                   "The composite SK design handles it cleanly.")
            .build();
    }

    // ── Pattern 3: Exact cell lookup ──────────────────────────────────────────
    private NeoQueryPattern pattern3_cgiModelCell() {
        return NeoQueryPattern.builder()
            .name("CGI exact cell lookup (family + qualifier)")
            .accumuloOperation("scanner.fetchColumn(new Text(\"DEVICE\"), new Text(\"MODEL\"))")
            .scannerOp(AccumuloScannerOp.cellLookup("CGI12345", "DEVICE", "MODEL"))
            .dynamoOperation("Query PK='CGI12345', SK begins_with 'DEVICE#MODEL#' → one cell per version")
            .auroraOperation("SELECT value FROM handset_cells WHERE cgi='CGI12345' AND family='DEVICE' AND qualifier='MODEL'")
            .requiredFields(List.of("model"))
            .usesFamilyQualifierFilter(true)
            .usesTimestampFilter(false)
            .dynamoDifficulty(NeoQueryPattern.Difficulty.EASY_BOTH)
            .dynamoFriendly(true)
            .postRetrievalLogic("None — single value returned directly.")
            .notes("DynamoDB: SK begins_with 'DEVICE#MODEL#' is efficient. Aurora: composite index on (cgi,family,qualifier). " +
                   "Both return a single row/item without reading other cells.")
            .build();
    }

    // ── Pattern 4: Versioned cell ─────────────────────────────────────────────
    private NeoQueryPattern pattern4_cgiVersionedCell() {
        return NeoQueryPattern.builder()
            .name("Versioned cell history (timestamp range)")
            .accumuloOperation("scanner.fetchColumn(\"DEVICE\",\"MODEL\"); scanner.setTimestampRange(new TimestampRange(from,to))")
            .scannerOp(AccumuloScannerOp.versionedLookup("CGI12345", "DEVICE", "MODEL", 1700000000000L, 1750000000000L))
            .dynamoOperation("Query PK='CGI12345', SK between 'DEVICE#MODEL#1700000000000' and 'DEVICE#MODEL#1750000000000' " +
                             "— NOTE: requires zero-padded timestamps in SK for correct lexicographic ordering. " +
                             "Or: fetch all DEVICE#MODEL# versions and filter ts attribute in application (1 extra read).")
            .auroraOperation("SELECT * FROM handset_cells WHERE cgi='CGI12345' AND family='DEVICE' AND qualifier='MODEL' AND ts BETWEEN 1700000000000 AND 1750000000000")
            .requiredFields(List.of("model", "timestamp"))
            .usesFamilyQualifierFilter(true)
            .usesTimestampFilter(true)
            .dynamoDifficulty(NeoQueryPattern.Difficulty.NEEDS_GSI_IN_DYNAMO)
            .dynamoFriendly(false)
            .postRetrievalLogic("Sort versions descending; take latest or display history.")
            .notes("IMPORTANT: DynamoDB SK is a string — '9' > '10' lexicographically. " +
                   "Timestamps in the SK must be zero-padded to 15 digits for range queries to be correct. " +
                   "Aurora ts BETWEEN uses numeric comparison — correct without special handling. " +
                   "This is a real DynamoDB footgun for timestamp range queries on composite sort keys.")
            .build();
    }

    // ── Pattern 5: Multi-family (DEVICE + LOCATION) ───────────────────────────
    private NeoQueryPattern pattern5_cgiDeviceAndLocation() {
        return NeoQueryPattern.builder()
            .name("CGI device + location (two families)")
            .accumuloOperation("scanner.fetchColumnFamily(\"DEVICE\"); scanner.fetchColumnFamily(\"LOCATION\")")
            .scannerOp(AccumuloScannerOp.familyScan("CGI12345", "DEVICE")) // representative
            .dynamoOperation("Two separate Query calls: PK='CGI12345' SK begins_with 'DEVICE#'; " +
                             "PK='CGI12345' SK begins_with 'LOCATION#'. Merge results in application. " +
                             "No filtering in application — each query is server-side filtered.")
            .auroraOperation("SELECT * FROM handset_cells WHERE cgi='CGI12345' AND family IN ('DEVICE','LOCATION')")
            .requiredFields(List.of("manufacturer", "model", "technology", "latitude", "longitude", "region"))
            .usesFamilyQualifierFilter(true)
            .usesTimestampFilter(false)
            .dynamoDifficulty(NeoQueryPattern.Difficulty.NEEDS_GSI_IN_DYNAMO)
            .dynamoFriendly(false)
            .postRetrievalLogic("Build NeoResponse from both cell sets. Derive locationLabel.")
            .notes("DynamoDB limitation: a single Query can only use one begins_with prefix. " +
                   "Two round-trips required. Aurora IN clause is a single round-trip. " +
                   "At low volume this is acceptable; at >100k req/s it doubles DynamoDB read cost.")
            .build();
    }

    // ── Pattern 6: Find all devices by model (HARD in DynamoDB) ───────────────
    private NeoQueryPattern pattern6_findByModel() {
        return NeoQueryPattern.builder()
            .name("Find all CGIs with MODEL=iPhone16 (attribute filter across rows)")
            .accumuloOperation("BatchScanner over all rows; filter CF=DEVICE, CQ=MODEL, value=iPhone16")
            .scannerOp(AccumuloScannerOp.cellLookup("*", "DEVICE", "MODEL"))
            .dynamoOperation("FULL TABLE SCAN with FilterExpression on value. " +
                             "Requires a GSI (value → PK) or a separate reverse-lookup table. " +
                             "Without GSI: O(n) scan, reads every item, slow and expensive.")
            .auroraOperation("SELECT cgi FROM handset_cells WHERE family='DEVICE' AND qualifier='MODEL' AND value='iPhone16'")
            .requiredFields(List.of("cgi"))
            .usesFamilyQualifierFilter(true)
            .usesTimestampFilter(false)
            .dynamoDifficulty(NeoQueryPattern.Difficulty.HARD_IN_DYNAMO)
            .dynamoFriendly(false)
            .postRetrievalLogic("Return list of CGIs; may need secondary lookup per CGI for full record.")
            .notes("This is the clearest case where Aurora wins. DynamoDB can only do this via full scan " +
                   "or a GSI on value (limited to one value attribute). Aurora simply indexes (family, qualifier, value). " +
                   "If NEO ever needs to 'find all iPhones' or 'find all 5G devices', Aurora is required.")
            .build();
    }

    // ── Pattern 7: Regional query ─────────────────────────────────────────────
    private NeoQueryPattern pattern7_findByRegion() {
        return NeoQueryPattern.builder()
            .name("Find all CGIs in region=London (cross-row attribute filter)")
            .accumuloOperation("BatchScanner; filter CF=LOCATION, CQ=REGION, value=London")
            .scannerOp(AccumuloScannerOp.cellLookup("*", "LOCATION", "REGION"))
            .dynamoOperation("Requires GSI on (value) with filter on family+qualifier. " +
                             "Each new 'find by value' query needs its own GSI. " +
                             "Maximum 20 GSIs per table; managing them adds operational overhead.")
            .auroraOperation("SELECT cgi FROM handset_cells WHERE family='LOCATION' AND qualifier='REGION' AND value='London'")
            .requiredFields(List.of("cgi"))
            .usesFamilyQualifierFilter(true)
            .usesTimestampFilter(false)
            .dynamoDifficulty(NeoQueryPattern.Difficulty.REQUIRES_EXTRA_INFRA_IN_DYNAMO)
            .dynamoFriendly(false)
            .postRetrievalLogic("None — list of CGIs returned for further processing.")
            .notes("Reporting and operational queries. Trivial in Aurora. " +
                   "In DynamoDB you would need a GSI per query dimension OR a separate 'inverted index' table " +
                   "maintained by application writes or DynamoDB Streams.")
            .build();
    }

    // ── Pattern 8: Temporal query ─────────────────────────────────────────────
    private NeoQueryPattern pattern8_findUpdatedLast24h() {
        return NeoQueryPattern.builder()
            .name("Find all CGIs updated in last 24 hours (temporal range across rows)")
            .accumuloOperation("BatchScanner with timestamp range filter across all rows")
            .scannerOp(AccumuloScannerOp.versionedLookup("*", "*", "*", 0L, Long.MAX_VALUE))
            .dynamoOperation("No native support. Options: " +
                             "(a) GSI on timestamp attribute — only viable if timestamp is in every item. " +
                             "(b) DynamoDB Streams → Lambda → separate 'recent-updates' table. " +
                             "(c) Full table scan with FilterExpression — very expensive.")
            .auroraOperation("SELECT DISTINCT cgi FROM handset_cells WHERE ts >= NOW() - INTERVAL '24 hours'")
            .requiredFields(List.of("cgi", "ts"))
            .usesFamilyQualifierFilter(false)
            .usesTimestampFilter(true)
            .dynamoDifficulty(NeoQueryPattern.Difficulty.REQUIRES_EXTRA_INFRA_IN_DYNAMO)
            .dynamoFriendly(false)
            .postRetrievalLogic("None — list of recently updated CGIs.")
            .notes("Operational monitoring, data freshness checks, incremental exports. " +
                   "Natural SQL query in Aurora. Requires architectural workaround in DynamoDB.")
            .build();
    }
}
