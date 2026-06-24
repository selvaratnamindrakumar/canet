package com.canet.poc.model;

import lombok.Builder;
import lombok.Data;

import java.util.List;
import java.util.Map;

/**
 * Three-way side-by-side evaluation: DynamoDB vs Aurora PostgreSQL vs RDS Oracle.
 * Returned by GET /api/compare/{cgi}
 */
@Data
@Builder
public class ComparisonResult {

    private String cgi;

    // ── Query results ─────────────────────────────────────────────────────────
    private NeoApiResponse dynamoDbResult;
    private NeoApiResponse auroraResult;
    private NeoApiResponse oracleResult;        // null when oracle.enabled=false

    private boolean dynamoAuroraMatch;
    private boolean dynamoOracleMatch;
    private boolean allMatch;

    // ── Latency ───────────────────────────────────────────────────────────────
    private long dynamoDbQueryMs;
    private long auroraQueryMs;
    private long oracleQueryMs;

    // ── Evaluation matrix ─────────────────────────────────────────────────────
    private EvaluationMatrix evaluation;

    @Data
    @Builder
    public static class EvaluationMatrix {

        // ── DynamoDB ──────────────────────────────────────────────────────────
        private String dynamoEaseOfMigration;
        private String dynamoSimilarityToAccumulo;
        private String dynamoQueryFlexibility;
        private String dynamoAppChangesRequired;
        private String dynamoOperationalComplexity;
        private String dynamoCostEstimate;
        private String dynamoLongTermMaintainability;

        // ── Aurora PostgreSQL ─────────────────────────────────────────────────
        private String auroraEaseOfMigration;
        private String auroraSimilarityToAccumulo;
        private String auroraQueryFlexibility;
        private String auroraAppChangesRequired;
        private String auroraOperationalComplexity;
        private String auroraCostEstimate;
        private String auroraLongTermMaintainability;

        // ── RDS Oracle SE2 ────────────────────────────────────────────────────
        private String oracleEaseOfMigration;
        private String oracleSimilarityToAccumulo;
        private String oracleQueryFlexibility;
        private String oracleAppChangesRequired;
        private String oracleOperationalComplexity;
        private String oracleCostEstimate;
        private String oracleLongTermMaintainability;

        // ── Summary ───────────────────────────────────────────────────────────
        private Map<String, String> confidenceLevels;  // option → HIGH/MEDIUM/LOW
        private String recommendation;
        private List<String> recommendationReasons;
    }
}
