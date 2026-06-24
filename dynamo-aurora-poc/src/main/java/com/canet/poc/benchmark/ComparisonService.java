package com.canet.poc.benchmark;

import com.canet.poc.model.ComparisonResult;
import com.canet.poc.model.NeoApiResponse;
import com.canet.poc.neo.NeoApiService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Map;
import java.util.Optional;

@Slf4j
@Service
@RequiredArgsConstructor
public class ComparisonService {

    private final NeoApiService neoApiService;

    /**
     * Three-way CGI lookup: DynamoDB vs Aurora PostgreSQL vs RDS Oracle.
     * Oracle result is null when oracle.enabled=false (local/dev mode).
     */
    public ComparisonResult compare(String cgi) {
        long t0 = System.currentTimeMillis();
        Optional<NeoApiResponse> dynamoResult = neoApiService.lookupByCgi(cgi, "dynamo");
        long dynamoMs = System.currentTimeMillis() - t0;

        long t1 = System.currentTimeMillis();
        Optional<NeoApiResponse> auroraResult = neoApiService.lookupByCgi(cgi, "aurora");
        long auroraMs = System.currentTimeMillis() - t1;

        NeoApiResponse oracleResp = null;
        long oracleMs = 0;
        if (neoApiService.isOracleEnabled()) {
            long t2 = System.currentTimeMillis();
            oracleResp = neoApiService.lookupByCgi(cgi, "oracle").orElse(null);
            oracleMs = System.currentTimeMillis() - t2;
        }

        boolean dynamoAuroraMatch = dynamoResult.isPresent() && auroraResult.isPresent()
                && dynamoResult.get().getCgi().equals(auroraResult.get().getCgi())
                && eq(dynamoResult.get().getManufacturer(), auroraResult.get().getManufacturer());

        boolean dynamoOracleMatch = oracleResp != null && dynamoResult.isPresent()
                && dynamoResult.get().getCgi().equals(oracleResp.getCgi())
                && eq(dynamoResult.get().getManufacturer(), oracleResp.getManufacturer());

        log.info("3-way compare CGI={}: DynamoDB={}ms Aurora={}ms Oracle={}ms dynamo-aurora-match={}",
                cgi, dynamoMs, auroraMs, oracleMs, dynamoAuroraMatch);

        return ComparisonResult.builder()
                .cgi(cgi)
                .dynamoDbResult(dynamoResult.orElse(null))
                .auroraResult(auroraResult.orElse(null))
                .oracleResult(oracleResp)
                .dynamoAuroraMatch(dynamoAuroraMatch)
                .dynamoOracleMatch(dynamoOracleMatch)
                .allMatch(dynamoAuroraMatch && (oracleResp == null || dynamoOracleMatch))
                .dynamoDbQueryMs(dynamoMs)
                .auroraQueryMs(auroraMs)
                .oracleQueryMs(oracleMs)
                .evaluation(buildEvaluationMatrix())
                .build();
    }

    // ── Evaluation matrix (7 POC criteria, 3 backends) ───────────────────────

    private ComparisonResult.EvaluationMatrix buildEvaluationMatrix() {
        return ComparisonResult.EvaluationMatrix.builder()

            // ── DynamoDB ──────────────────────────────────────────────────────
            .dynamoEaseOfMigration(
                "HIGH — CGI as partition key maps directly to Accumulo row key. " +
                "Composite SK (Family#Qualifier#Timestamp) preserves Accumulo's column structure.")
            .dynamoSimilarityToAccumulo(
                "HIGH — GetItem/Query by PK mirrors Accumulo exact-row lookup. " +
                "begins_with SK = fetchColumnFamily. No query language change for primary patterns.")
            .dynamoQueryFlexibility(
                "LOW-MEDIUM — primary CGI lookups and family/qualifier scans work well. " +
                "Secondary filters (region, technology, manufacturer) need GSIs. " +
                "Cross-row reporting queries require DynamoDB Streams or separate infrastructure.")
            .dynamoAppChangesRequired(
                "LOW for CGI key lookups (primary NEO pattern). " +
                "MEDIUM if secondary query patterns exist. HIGH if reporting is needed.")
            .dynamoOperationalComplexity(
                "LOW — fully managed serverless. No patching, no connections to pool. " +
                "On-demand billing. GSI management adds design overhead.")
            .dynamoCostEstimate(
                "LOW at POC scale (<$1/month). ~$1.25/million reads on-demand. " +
                "No idle cost. Each GSI adds ~write cost per affected item.")
            .dynamoLongTermMaintainability(
                "MEDIUM — schema-less is agile for attribute additions. " +
                "No SQL tooling. Reporting requires application code or DynamoDB exports to S3/Athena.")

            // ── Aurora PostgreSQL ─────────────────────────────────────────────
            .auroraEaseOfMigration(
                "MEDIUM — schema design required. JSONB column covers arbitrary Accumulo qualifiers. " +
                "Flyway manages schema evolution. Spring Data JPA abstracts JDBC.")
            .auroraSimilarityToAccumulo(
                "MEDIUM — SQL WHERE clause mirrors Accumulo scanner patterns. " +
                "cgi,family,qualifier,ts table structure (in accumulo-key-poc) is structurally identical.")
            .auroraQueryFlexibility(
                "HIGH — SQL handles all 8 NEO patterns including cross-row, multi-family, " +
                "temporal range, and reporting. JSONB column queryable without schema changes.")
            .auroraAppChangesRequired(
                "MEDIUM — JDBC/JPA instead of key-value API. Spring Data JPA reduces boilerplate. " +
                "Business logic layer unchanged.")
            .auroraOperationalComplexity(
                "MEDIUM — Aurora Serverless v2 reduces ops significantly (scales to zero). " +
                "VPC, security groups, connection pooling required. AWS manages patching/backups.")
            .auroraCostEstimate(
                "LOW-MEDIUM — Serverless v2 from $0 when idle. ~$20-50/month POC workload. " +
                "Storage $0.10/GB-month. No Oracle licensing cost.")
            .auroraLongTermMaintainability(
                "HIGH — SQL universally known. Standard BI tools (QuickSight, Metabase) work directly. " +
                "Flyway schema versioning. PostgreSQL row-level security for visibility labels.")

            // ── RDS Oracle SE2 ────────────────────────────────────────────────
            .oracleEaseOfMigration(
                "MEDIUM — same SQL approach as Aurora but Oracle DDL syntax differs. " +
                "No JSONB: additionalAttributes stored as VARCHAR2(4000) JSON string with IS JSON constraint. " +
                "Oracle 21c+ adds native JSON column type.")
            .oracleSimilarityToAccumulo(
                "MEDIUM — SQL WHERE clause mirrors Accumulo scanner. " +
                "Oracle cost-based optimiser with B-tree indexes handles all primary patterns.")
            .oracleQueryFlexibility(
                "HIGH — all 8 NEO patterns supported. Oracle SQL is the most feature-rich: " +
                "analytical functions, Flashback Query (built-in versioning), partitioning, " +
                "JSON_VALUE() for extension attribute queries. Oracle Text for full-text search.")
            .oracleAppChangesRequired(
                "MEDIUM — JDBC/JPA same as Aurora. Oracle dialect and OJDBC11 driver required. " +
                "Flashback Query for historical reads is Oracle-specific and needs code changes.")
            .oracleOperationalComplexity(
                "HIGH — most complex of the three. RDS manages patching and backups but " +
                "Oracle requires DB parameter group tuning, Oracle licence management, " +
                "SGA/PGA sizing. No serverless option — minimum instance is always running.")
            .oracleCostEstimate(
                "HIGH — RDS Oracle SE2 db.t3.small ~$150-200/month (licence-included). " +
                "db.r6i.large ~$400+/month. No scale-to-zero. " +
                "Significant cost premium over Aurora/DynamoDB for same workload.")
            .oracleLongTermMaintainability(
                "HIGH technically — Oracle SQL is powerful and mature. " +
                "MEDIUM organisationally — Oracle DBA expertise required. " +
                "Oracle licence management adds vendor dependency and renewal cost.")

            // ── Summary ───────────────────────────────────────────────────────
            .confidenceLevels(Map.of(
                "DynamoDB",           "HIGH — if NEO access patterns are exclusively CGI-keyed lookups",
                "Aurora PostgreSQL",  "HIGH — recommended default; handles all patterns at low cost",
                "RDS Oracle SE2",     "LOW-MEDIUM — cost and operational overhead too high for this use case"
            ))
            .recommendation("Aurora PostgreSQL (with DynamoDB as viable alternative if access patterns confirm CGI-only)")
            .recommendationReasons(List.of(
                "Aurora PostgreSQL handles all 8 NEO access patterns with standard SQL and indexes — " +
                "no GSI design, no extra infrastructure.",
                "Aurora Serverless v2 has zero idle cost — comparable to DynamoDB on-demand for POC workloads.",
                "JSONB column covers arbitrary Accumulo column qualifiers without schema changes.",
                "SQL is universally known; reporting and ad-hoc queries work without application code.",
                "DynamoDB is a viable alternative IF the NEO codebase confirms CGI-only lookups " +
                "(no BatchScanner, no cross-row queries). Run 'grep -r BatchScanner src/' to confirm.",
                "RDS Oracle SE2 is eliminated on cost ($150-200+/month vs $0-50 for Aurora Serverless) " +
                "and operational complexity — Oracle features (Flashback, partitioning) are not required " +
                "for this workload and don't justify the overhead.",
                "A single database technology (Aurora PostgreSQL) for BB, Handset, and IS datasets " +
                "reduces operational surface and avoids DBA specialisation per store."
            ))
            .build();
    }

    private boolean eq(String a, String b) {
        return a != null && a.equals(b);
    }
}
