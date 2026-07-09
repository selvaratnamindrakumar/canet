package com.canet.poc.benchmark;

import com.canet.poc.model.HandsetRecord;
import com.canet.poc.repository.HandsetRepository;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Service;

import java.util.*;
import java.util.function.Supplier;
import java.util.stream.Collectors;
import java.util.stream.LongStream;

/**
 * Statistical benchmark harness: warmup → N timed iterations → stats.
 *
 * Each query pattern is represented as a Supplier<Boolean> (true = success).
 * The harness is backend-agnostic — callers pass repository references.
 */
@Slf4j
@Service
public class BenchmarkService {

    private final HandsetRepository dynamoRepo;
    private final HandsetRepository auroraRepo;

    public BenchmarkService(
            @Qualifier("dynamoHandsetRepository") HandsetRepository dynamoRepo,
            @Qualifier("auroraHandsetRepository") HandsetRepository auroraRepo) {
        this.dynamoRepo = dynamoRepo;
        this.auroraRepo = auroraRepo;
    }

    // ── Public entry points ───────────────────────────────────────────────────

    /** CGI point-lookup — the primary NEO API pattern. */
    public BenchmarkResult benchmarkCgiLookup(String cgi, int iterations, int warmup, boolean includeRaw) {
        return runBenchmark(
                "CGI Point Lookup (PK=CGI)",
                () -> dynamoRepo.findByCgi(cgi).isPresent(),
                () -> auroraRepo.findByCgi(cgi).isPresent(),
                iterations, warmup, includeRaw);
    }

    /** Technology scan — DynamoDB full scan vs Aurora index seek. */
    public BenchmarkResult benchmarkTechnologyScan(String technology, int iterations, int warmup, boolean includeRaw) {
        return runBenchmark(
                "Technology Filter Scan (technology=" + technology + ")",
                () -> !dynamoRepo.findByTechnology(technology).isEmpty(),
                () -> !auroraRepo.findByTechnology(technology).isEmpty(),
                iterations, warmup, includeRaw);
    }

    /** Region scan — tests secondary index vs SQL WHERE. */
    public BenchmarkResult benchmarkRegionScan(String region, int iterations, int warmup, boolean includeRaw) {
        return runBenchmark(
                "Region Filter Scan (region=" + region + ")",
                () -> !dynamoRepo.findByRegion(region).isEmpty(),
                () -> !auroraRepo.findByRegion(region).isEmpty(),
                iterations, warmup, includeRaw);
    }

    /** Write throughput — insert N records and measure total time. */
    public BenchmarkResult benchmarkWrite(List<HandsetRecord> records, int iterations, int warmup, boolean includeRaw) {
        return runBenchmark(
                "Write (saveAll " + records.size() + " records)",
                () -> { dynamoRepo.saveAll(records); return true; },
                () -> { auroraRepo.saveAll(records); return true; },
                iterations, warmup, includeRaw);
    }

    /**
     * Run all four standard patterns sequentially and return a map keyed by pattern name.
     * Uses seeded data that must already be in both backends (call /api/admin/init first).
     */
    public Map<String, BenchmarkResult> runFullSuite(String sampleCgi, int iterations, int warmup) {
        Map<String, BenchmarkResult> results = new LinkedHashMap<>();
        results.put("cgi-lookup",       benchmarkCgiLookup(sampleCgi, iterations, warmup, false));
        results.put("technology-4g",    benchmarkTechnologyScan("4G", iterations, warmup, false));
        results.put("technology-5g",    benchmarkTechnologyScan("5G", iterations, warmup, false));
        results.put("region-london",    benchmarkRegionScan("London", iterations, warmup, false));
        return results;
    }

    // ── Core harness ──────────────────────────────────────────────────────────

    private BenchmarkResult runBenchmark(
            String pattern,
            Supplier<Boolean> dynamoOp,
            Supplier<Boolean> auroraOp,
            int iterations, int warmup, boolean includeRaw) {

        log.info("Benchmark [{}]: warmup={} iterations={}", pattern, warmup, iterations);

        // Warmup — discard results, lets JIT and connection pool settle
        for (int i = 0; i < warmup; i++) {
            quietly(dynamoOp);
            quietly(auroraOp);
        }

        long[] dynamoSamples = collectSamples(dynamoOp, iterations);
        long[] auroraSamples = collectSamples(auroraOp, iterations);

        BenchmarkResult.BackendStats dynamoStats = computeStats("DynamoDB", dynamoSamples);
        BenchmarkResult.BackendStats auroraStats = computeStats("Aurora PostgreSQL", auroraSamples);

        String faster = dynamoStats.getAvgMs() <= auroraStats.getAvgMs() ? "DynamoDB" : "Aurora PostgreSQL";
        double ratio = faster.equals("DynamoDB")
                ? auroraStats.getAvgMs() / Math.max(dynamoStats.getAvgMs(), 0.01)
                : dynamoStats.getAvgMs() / Math.max(auroraStats.getAvgMs(), 0.01);

        String throughputWinner = dynamoStats.getThroughputQps() >= auroraStats.getThroughputQps()
                ? "DynamoDB" : "Aurora PostgreSQL";

        log.info("Benchmark [{}] done: dynamo avg={}ms aurora avg={}ms faster={}",
                pattern,
                String.format("%.2f", dynamoStats.getAvgMs()),
                String.format("%.2f", auroraStats.getAvgMs()),
                faster);

        BenchmarkResult.BenchmarkResultBuilder b = BenchmarkResult.builder()
                .queryPattern(pattern)
                .iterations(iterations)
                .warmupRounds(warmup)
                .dynamoDb(dynamoStats)
                .aurora(auroraStats)
                .fasterBackend(faster)
                .speedupFactor(Math.round(ratio * 100.0) / 100.0)
                .throughputWinner(throughputWinner)
                .recommendation(buildRecommendation(pattern, dynamoStats, auroraStats));

        if (includeRaw) {
            b.dynamoRawMs(LongStream.of(dynamoSamples).boxed().collect(Collectors.toList()));
            b.auroraRawMs(LongStream.of(auroraSamples).boxed().collect(Collectors.toList()));
        }

        return b.build();
    }

    private long[] collectSamples(Supplier<Boolean> op, int n) {
        long[] samples = new long[n];
        for (int i = 0; i < n; i++) {
            long t = System.nanoTime();
            quietly(op);
            samples[i] = (System.nanoTime() - t) / 1_000_000L; // ns → ms
        }
        return samples;
    }

    private BenchmarkResult.BackendStats computeStats(String backend, long[] rawMs) {  // NOSONAR
        long[] sorted = Arrays.copyOf(rawMs, rawMs.length);
        Arrays.sort(sorted);

        long sum = 0;
        int successes = rawMs.length;
        for (long v : rawMs) sum += v;

        double avg = (double) sum / rawMs.length;
        double variance = 0;
        for (long v : rawMs) variance += Math.pow(v - avg, 2);
        double stddev = Math.sqrt(variance / rawMs.length);

        return BenchmarkResult.BackendStats.builder()
                .backend(backend)
                .minMs(sorted[0])
                .maxMs(sorted[sorted.length - 1])
                .avgMs(round2(avg))
                .medianMs(round2(percentile(sorted, 50)))
                .p95Ms(round2(percentile(sorted, 95)))
                .p99Ms(round2(percentile(sorted, 99)))
                .stddevMs(round2(stddev))
                .throughputQps(round2(1000.0 / Math.max(avg, 0.01)))
                .successCount(successes)
                .errorCount(0)
                .build();
    }

    private double percentile(long[] sorted, int p) {
        if (sorted.length == 0) return 0;
        int idx = (int) Math.ceil(p / 100.0 * sorted.length) - 1;
        return sorted[Math.max(0, Math.min(idx, sorted.length - 1))];
    }

    private String buildRecommendation(String pattern,
                                       BenchmarkResult.BackendStats dynamo,
                                       BenchmarkResult.BackendStats aurora) {
        boolean isScan = pattern.toLowerCase().contains("scan");
        boolean dynamoFaster = dynamo.getAvgMs() < aurora.getAvgMs();

        if (!isScan && dynamoFaster) {
            return "DynamoDB wins on point-lookup latency. Preferred for CGI-keyed access patterns matching the Accumulo row-key model.";
        } else if (!isScan) {
            return "Aurora PostgreSQL wins on point-lookup latency. Consider connection pool warm-up time in production baseline.";
        } else if (dynamoFaster) {
            return "DynamoDB faster on this scan, but note: scan cost scales with table size. Aurora uses an index — Aurora advantage grows as data volume increases.";
        } else {
            return "Aurora PostgreSQL wins on secondary query pattern. Aurora index seek vs DynamoDB full-table scan. Aurora recommended for non-PK access patterns.";
        }
    }

    private void quietly(Supplier<Boolean> op) {
        try { op.get(); } catch (Exception ignored) { }
    }

    private double round2(double v) {
        return Math.round(v * 100.0) / 100.0;
    }
}
