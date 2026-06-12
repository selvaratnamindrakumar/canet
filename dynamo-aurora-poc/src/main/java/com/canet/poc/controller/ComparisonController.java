package com.canet.poc.controller;

import com.canet.poc.benchmark.ComparisonService;
import com.canet.poc.model.ComparisonResult;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

/**
 * POC evaluation endpoints — run the same query against both backends
 * and return a structured side-by-side comparison with the evaluation matrix.
 */
@RestController
@RequestMapping("/api/compare")
@RequiredArgsConstructor
public class ComparisonController {

    private final ComparisonService comparisonService;

    /**
     * GET /api/compare/{cgi}
     *
     * Runs the NEO API CGI lookup against both DynamoDB and Aurora PostgreSQL
     * and returns:
     *   - Both query results (should be identical)
     *   - Query latency for each backend
     *   - Whether the results match (data parity check)
     *   - The full evaluation matrix with recommendation
     */
    @GetMapping("/{cgi}")
    public ComparisonResult compare(@PathVariable String cgi) {
        return comparisonService.compare(cgi);
    }
}
