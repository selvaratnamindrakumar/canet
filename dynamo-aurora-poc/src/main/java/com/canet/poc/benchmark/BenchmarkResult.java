package com.canet.poc.benchmark;

import lombok.Builder;
import lombok.Data;

import java.util.List;
import java.util.Map;

@Data
@Builder
public class BenchmarkResult {

    private String queryPattern;
    private int iterations;
    private int warmupRounds;

    // ── Per-backend statistical summary ───────────────────────────────────────
    private BackendStats dynamoDb;
    private BackendStats aurora;

    // ── Head-to-head verdict ──────────────────────────────────────────────────
    private String fasterBackend;
    private double speedupFactor;      // e.g. 2.3 means faster backend is 2.3× quicker
    private String throughputWinner;   // which handles more queries/sec
    private String recommendation;

    // ── Raw samples (optional — set includeRaw=true on request) ───────────────
    private List<Long> dynamoRawMs;
    private List<Long> auroraRawMs;

    @Data
    @Builder
    public static class BackendStats {
        private String backend;
        private long minMs;
        private long maxMs;
        private double avgMs;
        private double medianMs;
        private double p95Ms;
        private double p99Ms;
        private double stddevMs;
        private double throughputQps;     // queries per second (1000 / avgMs)
        private int successCount;
        private int errorCount;
        private Map<String, Object> extras; // query-pattern-specific data
    }
}
