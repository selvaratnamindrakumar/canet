package com.canet.poc.model;

import lombok.Builder;
import lombok.Data;

import java.util.List;

/**
 * Side-by-side POC evaluation result returned by GET /api/compare/{cgi}.
 */
@Data
@Builder
public class ComparisonResult {

    private String cgi;

    // ── Query results ─────────────────────────────────────────────────────────
    private NeoApiResponse dynamoDbResult;
    private NeoApiResponse auroraResult;

    private boolean resultsMatch;

    // ── Latency ───────────────────────────────────────────────────────────────
    private long dynamoDbQueryMs;
    private long auroraQueryMs;

    // ── Evaluation matrix ─────────────────────────────────────────────────────
    private EvaluationMatrix evaluation;

    @Data
    @Builder
    public static class EvaluationMatrix {

        // DynamoDB
        private String dynamoEaseOfMigration;
        private String dynamoSimilarityToAccumulo;
        private String dynamoQueryFlexibility;
        private String dynamoAppChangesRequired;
        private String dynamoOperationalComplexity;
        private String dynamoCostEstimate;
        private String dynamoLongTermMaintainability;

        // Aurora PostgreSQL
        private String auroraEaseOfMigration;
        private String auroraSimilarityToAccumulo;
        private String auroraQueryFlexibility;
        private String auroraAppChangesRequired;
        private String auroraOperationalComplexity;
        private String auroraCostEstimate;
        private String auroraLongTermMaintainability;

        private String recommendation;
        private List<String> recommendationReasons;
    }
}
