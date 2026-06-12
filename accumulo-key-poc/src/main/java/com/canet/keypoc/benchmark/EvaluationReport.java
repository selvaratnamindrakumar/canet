package com.canet.keypoc.benchmark;

import lombok.Builder;
import lombok.Data;

import java.util.List;
import java.util.Map;

/**
 * Full POC evaluation report — returned by GET /api/report
 *
 * Structured to answer the exact questions from the POC spec:
 *   - Which patterns are easy/hard in DynamoDB?
 *   - What is the overall recommendation?
 *   - What confidence level for each option?
 */
@Data
@Builder
public class EvaluationReport {

    // ── Per-pattern results ───────────────────────────────────────────────────
    private List<PatternBenchmarkResult> patternResults;

    // ── Summary counts ────────────────────────────────────────────────────────
    private int totalPatterns;
    private int dynamoFriendlyCount;     // "EASY_BOTH"
    private int dynamoNeedsGsiCount;     // "NEEDS_GSI_IN_DYNAMO"
    private int dynamoHardCount;         // "HARD_IN_DYNAMO" + "REQUIRES_EXTRA_INFRA"
    private int auroraFriendlyCount;     // all patterns (SQL handles everything)

    // ── Evaluation matrix (7 criteria from the POC spec) ─────────────────────
    private Map<String, String> dynamoMatrix;
    private Map<String, String> auroraMatrix;

    // ── Catherine's "easy vs hard" classification ─────────────────────────────
    private List<String> dynamoEasyPatterns;
    private List<String> dynamoHardPatterns;

    // ── Confidence levels (as per updated recommendation) ─────────────────────
    private String dynamoConfidence;      // "HIGH" | "MEDIUM" | "LOW"
    private String auroraConfidence;
    private String oracleRdsConfidence;

    // ── Final recommendation ──────────────────────────────────────────────────
    private String recommendation;
    private String recommendationRationale;
    private List<String> keyFindings;
    private String nextDiscoveryStep;
}
