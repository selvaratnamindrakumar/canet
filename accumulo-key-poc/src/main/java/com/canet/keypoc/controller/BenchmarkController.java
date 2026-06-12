package com.canet.keypoc.controller;

import com.canet.keypoc.benchmark.BenchmarkService;
import com.canet.keypoc.benchmark.EvaluationReport;
import com.canet.keypoc.benchmark.PatternBenchmarkResult;
import com.canet.keypoc.neo.NeoPatternRegistry;
import com.canet.keypoc.neo.NeoQueryPattern;
import com.canet.keypoc.scanner.AccumuloScannerOp;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

/**
 * POC evaluation endpoints.
 *
 * GET /api/report          — full evaluation report with all 8 patterns
 * GET /api/patterns        — list all known NEO query patterns
 * GET /api/compare/{name}  — run a single named pattern against both backends
 * POST /api/compare/op     — run an ad-hoc scanner op against both backends
 */
@RestController
@RequestMapping("/api")
@RequiredArgsConstructor
public class BenchmarkController {

    private final BenchmarkService benchmarkService;
    private final NeoPatternRegistry registry;

    /**
     * GET /api/report
     *
     * The primary POC deliverable. Runs all 8 NEO access patterns against both backends
     * and returns the complete evaluation matrix with recommendations.
     *
     * Structured to answer:
     *   - Which patterns are easy/hard in DynamoDB (Catherine's classification)?
     *   - What is the overall confidence for each option?
     *   - What is the next discovery step?
     */
    @GetMapping("/report")
    public EvaluationReport fullReport() {
        return benchmarkService.runFullReport();
    }

    /**
     * GET /api/patterns
     * List all registered NEO access patterns with their DynamoDB difficulty rating.
     */
    @GetMapping("/patterns")
    public List<NeoQueryPattern> listPatterns() {
        return registry.allPatterns();
    }

    /**
     * GET /api/compare/{patternName}
     * Run a single named pattern against both backends and return side-by-side results.
     * Pattern names (case-insensitive prefix match):
     *   "CGI full record"
     *   "CGI device attributes"
     *   "CGI exact cell"
     *   "Versioned cell"
     *   "CGI device + location"
     *   "Find all CGIs with MODEL"
     *   "Find all CGIs in region"
     *   "Find all CGIs updated"
     */
    @GetMapping("/compare/{patternName}")
    public PatternBenchmarkResult comparePattern(@PathVariable String patternName) {
        return benchmarkService.runSinglePattern(patternName);
    }

    /**
     * POST /api/compare/op
     * Run an ad-hoc scanner operation against both backends.
     *
     * Body examples:
     *   {"type":"ROW_SCAN","rowId":"CGI12345"}
     *   {"type":"FAMILY_SCAN","rowId":"CGI12345","columnFamily":"DEVICE"}
     *   {"type":"CELL_LOOKUP","rowId":"CGI12345","columnFamily":"DEVICE","columnQualifier":"MODEL"}
     *   {"type":"VERSIONED_CELL_LOOKUP","rowId":"CGI12345","columnFamily":"DEVICE","columnQualifier":"MODEL",
     *    "timestampFrom":1700000000000,"timestampTo":1750000000000}
     */
    @PostMapping("/compare/op")
    public PatternBenchmarkResult compareOp(@RequestBody AccumuloScannerOp op) {
        return benchmarkService.runOp(op);
    }

    /**
     * GET /api/dynamo-easy-vs-hard
     * Catherine's classification: which patterns are easy and which are hard in DynamoDB.
     */
    @GetMapping("/dynamo-easy-vs-hard")
    public Map<String, Object> dynamoEasyVsHard() {
        List<NeoQueryPattern> all = registry.allPatterns();
        return Map.of(
            "easy", all.stream()
                .filter(NeoQueryPattern::isDynamoFriendly)
                .map(p -> Map.of(
                    "pattern", p.getName(),
                    "accumulo", p.getAccumuloOperation(),
                    "dynamo",   p.getDynamoOperation(),
                    "difficulty", p.getDynamoDifficulty().name()))
                .toList(),
            "hard", all.stream()
                .filter(p -> !p.isDynamoFriendly())
                .map(p -> Map.of(
                    "pattern",    p.getName(),
                    "accumulo",   p.getAccumuloOperation(),
                    "dynamo",     p.getDynamoOperation(),
                    "difficulty", p.getDynamoDifficulty().name(),
                    "notes",      p.getNotes()))
                .toList(),
            "summary", Map.of(
                "totalPatterns", all.size(),
                "dynamoEasyCount", all.stream().filter(NeoQueryPattern::isDynamoFriendly).count(),
                "dynamoHardCount", all.stream().filter(p -> !p.isDynamoFriendly()).count(),
                "auroraHandlesAll", true,
                "catherinaObservation",
                    "Composite PK/SK (CGI + Family#Qualifier#Timestamp) correctly maps Accumulo's 3 primary " +
                    "lookup patterns to DynamoDB without application-side filtering. " +
                    "5 of 8 patterns still require GSIs or additional infrastructure.")
        );
    }
}
