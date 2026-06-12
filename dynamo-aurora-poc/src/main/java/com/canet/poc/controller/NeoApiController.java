package com.canet.poc.controller;

import com.canet.poc.model.HandsetRecord;
import com.canet.poc.model.NeoApiResponse;
import com.canet.poc.neo.NeoApiService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Arrays;
import java.util.List;

/**
 * NEO API simulation endpoints.
 *
 * The ?backend= parameter lets you switch between DynamoDB and Aurora
 * at request time, so you can compare responses and latency directly.
 * Default backend: dynamo
 *
 * In production you would remove the ?backend= parameter and wire to one backend.
 */
@RestController
@RequestMapping("/api/neo")
@RequiredArgsConstructor
public class NeoApiController {

    private final NeoApiService service;

    /**
     * GET /api/neo/handset/{cgi}?backend=dynamo|aurora
     *
     * Primary NEO API lookup — the CGI key access pattern.
     * Equivalent to: Accumulo scanner.setRange(new Range(cgi))
     */
    @GetMapping("/handset/{cgi}")
    public ResponseEntity<NeoApiResponse> lookupByCgi(
            @PathVariable String cgi,
            @RequestParam(defaultValue = "dynamo") String backend) {
        return service.lookupByCgi(cgi, backend)
                .map(ResponseEntity::ok)
                .orElse(ResponseEntity.notFound().build());
    }

    /**
     * GET /api/neo/handset/{cgi}/fields?f=manufacturer,model,technology&backend=dynamo|aurora
     *
     * Projected lookup — return only the specified fields.
     * Demonstrates Aurora column projection vs DynamoDB ProjectionExpression.
     */
    @GetMapping("/handset/{cgi}/fields")
    public ResponseEntity<NeoApiResponse> lookupProjected(
            @PathVariable String cgi,
            @RequestParam String f,
            @RequestParam(defaultValue = "dynamo") String backend) {
        List<String> fields = Arrays.asList(f.split(","));
        return service.lookupByCgiProjected(cgi, fields, backend)
                .map(ResponseEntity::ok)
                .orElse(ResponseEntity.notFound().build());
    }

    /**
     * GET /api/neo/handsets/technology/{technology}?backend=dynamo|aurora
     *
     * Secondary access pattern — filter by technology (2G/3G/4G/5G).
     * DynamoDB: requires full table scan (no GSI in POC) — note the latency difference.
     * Aurora: uses the technology index — indexed column scan.
     */
    @GetMapping("/handsets/technology/{technology}")
    public List<HandsetRecord> findByTechnology(
            @PathVariable String technology,
            @RequestParam(defaultValue = "dynamo") String backend) {
        return service.findByTechnology(technology, backend);
    }

    /**
     * GET /api/neo/handsets/region/{region}?backend=dynamo|aurora
     *
     * Secondary access pattern — filter by region.
     * Another case where Aurora index beats DynamoDB scan.
     */
    @GetMapping("/handsets/region/{region}")
    public List<HandsetRecord> findByRegion(
            @PathVariable String region,
            @RequestParam(defaultValue = "dynamo") String backend) {
        return service.findByRegion(region, backend);
    }

    /**
     * GET /api/neo/handsets/region/{region}/technology/{technology}?backend=dynamo|aurora
     *
     * Multi-field filter — combination query.
     * DynamoDB: scan + filter (reads entire table).
     * Aurora: composite index or two-column WHERE clause.
     */
    @GetMapping("/handsets/region/{region}/technology/{technology}")
    public List<HandsetRecord> findByRegionAndTechnology(
            @PathVariable String region,
            @PathVariable String technology,
            @RequestParam(defaultValue = "dynamo") String backend) {
        return service.findByRegionAndTechnology(region, technology, backend);
    }
}
