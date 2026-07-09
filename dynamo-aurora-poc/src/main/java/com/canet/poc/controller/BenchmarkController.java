package com.canet.poc.controller;

import com.canet.poc.benchmark.BenchmarkResult;
import com.canet.poc.benchmark.BenchmarkService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

/**
 * Statistical benchmark endpoints — warmup + N iterations → min/max/avg/p95/p99/stddev/QPS.
 *
 * Run /api/admin/init first to seed both backends with sample data.
 */
@RestController
@RequestMapping("/api/benchmark")
@RequiredArgsConstructor
public class BenchmarkController {

    private final BenchmarkService benchmarkService;

    /**
     * GET /api/benchmark/cgi/{cgi}?iterations=50&warmup=10&raw=false
     *
     * Primary NEO API pattern: CGI point-lookup.
     * DynamoDB: GetItem by PK (O(1))
     * Aurora:   SELECT ... WHERE cgi = ? (B-tree PK index)
     */
    @GetMapping("/cgi/{cgi}")
    public BenchmarkResult cgiLookup(
            @PathVariable String cgi,
            @RequestParam(defaultValue = "50") int iterations,
            @RequestParam(defaultValue = "10") int warmup,
            @RequestParam(defaultValue = "false") boolean raw) {
        return benchmarkService.benchmarkCgiLookup(cgi, iterations, warmup, raw);
    }

    /**
     * GET /api/benchmark/technology/{tech}?iterations=50&warmup=10
     *
     * Secondary pattern: find all records by technology (4G/5G).
     * DynamoDB: full table scan (no GSI on technology)
     * Aurora:   index seek on idx_handset_technology
     */
    @GetMapping("/technology/{tech}")
    public BenchmarkResult technologyScan(
            @PathVariable String tech,
            @RequestParam(defaultValue = "50") int iterations,
            @RequestParam(defaultValue = "10") int warmup,
            @RequestParam(defaultValue = "false") boolean raw) {
        return benchmarkService.benchmarkTechnologyScan(tech, iterations, warmup, raw);
    }

    /**
     * GET /api/benchmark/region/{region}?iterations=50&warmup=10
     *
     * Secondary pattern: find all records by region (London/Scotland/etc).
     * DynamoDB: full table scan
     * Aurora:   index seek on idx_handset_region
     */
    @GetMapping("/region/{region}")
    public BenchmarkResult regionScan(
            @PathVariable String region,
            @RequestParam(defaultValue = "50") int iterations,
            @RequestParam(defaultValue = "10") int warmup,
            @RequestParam(defaultValue = "false") boolean raw) {
        return benchmarkService.benchmarkRegionScan(region, iterations, warmup, raw);
    }

    /**
     * GET /api/benchmark/suite?cgi=234-10-1234-56789&iterations=30&warmup=5
     *
     * Full suite: CGI lookup + technology scans + region scan.
     * Returns a map keyed by pattern name for side-by-side comparison.
     * Suitable for a Thursday demo — one call, full picture.
     */
    @GetMapping("/suite")
    public Map<String, BenchmarkResult> fullSuite(
            @RequestParam(defaultValue = "234-10-1234-56789") String cgi,
            @RequestParam(defaultValue = "30") int iterations,
            @RequestParam(defaultValue = "5") int warmup) {
        return benchmarkService.runFullSuite(cgi, iterations, warmup);
    }
}
