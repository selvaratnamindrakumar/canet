package com.canet.poc.benchmark;

import com.canet.poc.model.ComparisonResult;
import com.canet.poc.model.NeoApiResponse;
import com.canet.poc.neo.NeoApiService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Optional;

@Slf4j
@Service
@RequiredArgsConstructor
public class ComparisonService {

    private final NeoApiService neoApiService;

    /**
     * Runs the same NEO API CGI lookup against both backends and returns
     * a side-by-side evaluation matrix.
     */
    public ComparisonResult compare(String cgi) {
        long t0 = System.currentTimeMillis();
        Optional<NeoApiResponse> dynamoResult = neoApiService.lookupByCgi(cgi, "dynamo");
        long dynamoMs = System.currentTimeMillis() - t0;

        long t1 = System.currentTimeMillis();
        Optional<NeoApiResponse> auroraResult = neoApiService.lookupByCgi(cgi, "aurora");
        long auroraMs = System.currentTimeMillis() - t1;

        boolean match = dynamoResult.isPresent() && auroraResult.isPresent()
                && dynamoResult.get().getCgi().equals(auroraResult.get().getCgi())
                && dynamoResult.get().getManufacturer().equals(auroraResult.get().getManufacturer());

        log.info("Comparison for CGI={}: DynamoDB={}ms Aurora={}ms match={}", cgi, dynamoMs, auroraMs, match);

        return ComparisonResult.builder()
                .cgi(cgi)
                .dynamoDbResult(dynamoResult.orElse(null))
                .auroraResult(auroraResult.orElse(null))
                .dynamoDbQueryMs(dynamoMs)
                .auroraQueryMs(auroraMs)
                .resultsMatch(match)
                .evaluation(buildEvaluationMatrix())
                .build();
    }

    private ComparisonResult.EvaluationMatrix buildEvaluationMatrix() {
        return ComparisonResult.EvaluationMatrix.builder()
                // ── DynamoDB ───────────────────────────────────────────────────────
                .dynamoEaseOfMigration(
                        "HIGH — key-value model maps directly to Accumulo row key. " +
                        "CGI as partition key mirrors Accumulo row key exactly.")
                .dynamoSimilarityToAccumulo(
                        "HIGH — single-key lookup (GetItem by CGI) is structurally identical " +
                        "to Accumulo exact-row lookup. No query language change required.")
                .dynamoQueryFlexibility(
                        "LOW — secondary access patterns (by region, technology, manufacturer) " +
                        "require Global Secondary Indexes (GSIs) or expensive full-table scans. " +
                        "Each new query pattern may need a new GSI (additional cost + management).")
                .dynamoAppChangesRequired(
                        "LOW for primary lookup (CGI → full record). " +
                        "MEDIUM-HIGH if any secondary query patterns are needed now or in future.")
                .dynamoOperationalComplexity(
                        "LOW — fully managed, no server/patching. " +
                        "Capacity planning via on-demand or provisioned RCU/WCU. " +
                        "GSI design requires upfront access-pattern analysis.")
                .dynamoCostEstimate(
                        "LOW at POC scale (<$1/month for sample data). " +
                        "Production: ~$1.25/million reads (on-demand). " +
                        "Full-table scans are expensive at scale — avoid without GSIs.")
                .dynamoLongTermMaintainability(
                        "MEDIUM — schema-less is flexible for new attributes but " +
                        "makes ad-hoc reporting and joins very difficult. " +
                        "No SQL tooling; requires application code for aggregations.")

                // ── Aurora PostgreSQL ──────────────────────────────────────────────
                .auroraEaseOfMigration(
                        "MEDIUM — requires schema design and column mapping from Accumulo CF/CQ. " +
                        "JSONB column covers extension fields without schema changes.")
                .auroraSimilarityToAccumulo(
                        "LOW-MEDIUM — SQL is a different paradigm from key-value. " +
                        "Primary lookup (SELECT * WHERE cgi=?) is simple but syntax differs.")
                .auroraQueryFlexibility(
                        "HIGH — SQL handles any query pattern with indexes. " +
                        "SELECT only needed columns, JOIN, aggregate, report — all standard SQL. " +
                        "JSONB column enables querying into extension attributes without schema change.")
                .auroraAppChangesRequired(
                        "MEDIUM — switch from key-value API to JDBC/JPA. " +
                        "Spring Data JPA abstracts most of this; application logic unchanged.")
                .auroraOperationalComplexity(
                        "MEDIUM — managed by AWS (patching, backups, Multi-AZ). " +
                        "Requires VPC, security groups, connection pooling. " +
                        "Easier than self-managed Accumulo but more than DynamoDB.")
                .auroraCostEstimate(
                        "MEDIUM — db.t4g.medium ~$50/month. " +
                        "Aurora Serverless v2 scales to zero when idle (good for POC). " +
                        "Storage $0.10/GB-month. More predictable cost than DynamoDB at scale.")
                .auroraLongTermMaintainability(
                        "HIGH — SQL is universally understood. " +
                        "Standard tools: psql, pgAdmin, Grafana, Metabase, BI tools all work. " +
                        "Schema evolution via Flyway migrations. Strong operational visibility.")

                // ── Recommendation ─────────────────────────────────────────────────
                .recommendation("Aurora PostgreSQL")
                .recommendationReasons(List.of(
                        "The NEO API requires more than simple CGI lookup — technology, region, " +
                        "and manufacturer filters are already present or likely to be needed.",
                        "Aurora's SELECT projection returns only required fields efficiently; " +
                        "DynamoDB always reads and charges for the full item even with ProjectionExpression.",
                        "SQL is universally understood by the team and supports ad-hoc reporting " +
                        "without additional tooling or application code.",
                        "JSONB column provides a schema-less extension bag matching Accumulo's " +
                        "arbitrary column qualifier model, giving the best of both worlds.",
                        "Aurora Serverless v2 eliminates idle cost, making it cost-competitive " +
                        "with DynamoDB for variable workloads.",
                        "Single database technology for BB, Handset, and IS datasets reduces " +
                        "operational complexity and avoids managing both GSI design (DynamoDB) " +
                        "and relational schema (Aurora).",
                        "DynamoDB remains the better choice ONLY if the access pattern is " +
                        "exclusively single-key CGI lookup at very high TPS (>10k/s) with no " +
                        "secondary queries — which does not appear to be the current NEO API pattern."
                ))
                .build();
    }
}
