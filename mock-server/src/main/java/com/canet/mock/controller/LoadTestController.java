package com.canet.mock.controller;

import com.canet.mock.data.HandsetDataGenerator;
import com.canet.mock.data.HandsetRepository;
import com.canet.mock.model.HandsetRecord;
import com.canet.mock.model.TacSearchResponse;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.Duration;
import java.time.Instant;
import java.util.*;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.LongAdder;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

/**
 * Load / performance testing endpoints for the mock server.
 *
 * ── Endpoints ────────────────────────────────────────────────────────────────
 *
 * Bulk data (generated records — TACs 10000000..10000999):
 *   GET /loadtest/bulk?count=100&delayMs=0
 *       Returns N generated HandsetRecords as a JSON array.
 *
 *   GET /loadtest/search?count=20&delayMs=0
 *       Returns N generated records wrapped in TacSearchResponse envelope
 *       (all found, notFound=[]).
 *
 * Slow-response simulation (uses existing static 20-record dataset):
 *   GET /loadtest/slow?tac=35674108,35282402&delayMs=500
 *       Identical to /handsetdetails?tac=... but sleeps delayMs before responding.
 *       Use to test the main app's read-timeout behaviour.
 *
 * Input helpers:
 *   GET /loadtest/tacs?count=20&separator=newline
 *       Returns generated TAC codes separated by newlines (default) or commas.
 *       Copy-paste into the main app textarea for handset-mode testing.
 *
 *   GET /loadtest/imeis?count=20
 *       Returns fake 15-digit IMEIs derived from generated TACs; paste into
 *       the textarea in cell mode.
 *
 * Observability:
 *   GET  /loadtest/stats   — request count, avg/min/max response time
 *   POST /loadtest/reset   — reset all counters
 *
 * ── Application property ─────────────────────────────────────────────────────
 *   mock.response-delay-ms: 0     # default artificial delay for all /loadtest endpoints
 */
@RestController
@RequestMapping("/loadtest")
public class LoadTestController {

    private static final int MAX_RECORDS = 1_000;
    private static final int MAX_TACS    = 20;

    @Value("${mock.response-delay-ms:0}")
    private long defaultDelayMs;

    private final HandsetRepository repository;

    private final AtomicLong requestCount = new AtomicLong(0);
    private final LongAdder  totalNs      = new LongAdder();
    private final AtomicLong minNs        = new AtomicLong(Long.MAX_VALUE);
    private final AtomicLong maxNs        = new AtomicLong(0);
    private final Instant    startedAt    = Instant.now();

    public LoadTestController(HandsetRepository repository) {
        this.repository = repository;
    }

    // ── /loadtest/bulk ────────────────────────────────────────────────────────
    @GetMapping("/bulk")
    public ResponseEntity<List<HandsetRecord>> bulk(
            @RequestParam(defaultValue = "100") int  count,
            @RequestParam(defaultValue = "0")   long delayMs) throws InterruptedException {

        long t0 = System.nanoTime();
        applyDelay(delayMs);
        List<HandsetRecord> records = HandsetDataGenerator.generate(0, Math.min(count, MAX_RECORDS));
        record(System.nanoTime() - t0);
        return ResponseEntity.ok(records);
    }

    // ── /loadtest/search ──────────────────────────────────────────────────────
    @GetMapping("/search")
    public ResponseEntity<TacSearchResponse> search(
            @RequestParam(defaultValue = "20") int  count,
            @RequestParam(defaultValue = "0")  long delayMs) throws InterruptedException {

        long t0 = System.nanoTime();
        applyDelay(delayMs);
        int n = Math.min(count, MAX_RECORDS);
        List<HandsetRecord> records = HandsetDataGenerator.generate(0, n);
        TacSearchResponse resp = new TacSearchResponse(n, n, records, List.of());
        record(System.nanoTime() - t0);
        return ResponseEntity.ok(resp);
    }

    // ── /loadtest/slow ────────────────────────────────────────────────────────
    @GetMapping("/slow")
    public ResponseEntity<TacSearchResponse> slow(
            @RequestParam                       String tac,
            @RequestParam(defaultValue = "500") long   delayMs) throws InterruptedException {

        long t0 = System.nanoTime();
        Thread.sleep(Math.max(0, delayMs));
        List<String> tacs = Arrays.stream(tac.split(","))
                .map(String::trim).filter(s -> !s.isBlank())
                .limit(MAX_TACS).collect(Collectors.toList());
        TacSearchResponse resp = repository.findByTacs(tacs);
        record(System.nanoTime() - t0);
        return ResponseEntity.ok(resp);
    }

    // ── /loadtest/tacs ────────────────────────────────────────────────────────
    @GetMapping(value = "/tacs", produces = MediaType.TEXT_PLAIN_VALUE)
    public ResponseEntity<String> tacs(
            @RequestParam(defaultValue = "20")      int    count,
            @RequestParam(defaultValue = "newline") String separator) {

        int    n   = Math.min(count, MAX_RECORDS);
        String sep = "comma".equalsIgnoreCase(separator) ? "," : "\n";
        String body = IntStream.range(0, n)
                .mapToObj(HandsetDataGenerator::tacFor)
                .collect(Collectors.joining(sep));
        return ResponseEntity.ok(body);
    }

    // ── /loadtest/imeis ───────────────────────────────────────────────────────
    @GetMapping(value = "/imeis", produces = MediaType.TEXT_PLAIN_VALUE)
    public ResponseEntity<String> imeis(
            @RequestParam(defaultValue = "20")      int    count,
            @RequestParam(defaultValue = "newline") String separator) {

        int    n   = Math.min(count, MAX_RECORDS);
        String sep = "comma".equalsIgnoreCase(separator) ? "," : "\n";
        // Pad TAC with 7 zeros to produce a 15-digit IMEI (8 + 7)
        String body = IntStream.range(0, n)
                .mapToObj(i -> HandsetDataGenerator.tacFor(i) + "0000000")
                .collect(Collectors.joining(sep));
        return ResponseEntity.ok(body);
    }

    // ── /loadtest/stats ───────────────────────────────────────────────────────
    @GetMapping("/stats")
    public ResponseEntity<Map<String, Object>> stats() {
        long count   = requestCount.get();
        long uptimeS = Duration.between(startedAt, Instant.now()).toSeconds();

        Map<String, Object> m = new LinkedHashMap<>();
        m.put("requestsServed", count);
        m.put("uptimeSeconds",  uptimeS);
        if (count > 0) {
            m.put("avgResponseMs", String.format("%.2f", totalNs.sum() / 1_000_000.0 / count));
            m.put("minResponseMs", String.format("%.2f", minNs.get()   / 1_000_000.0));
            m.put("maxResponseMs", String.format("%.2f", maxNs.get()   / 1_000_000.0));
        }
        m.put("note", "Tracks requests to /loadtest/* endpoints only. POST /loadtest/reset to clear.");
        return ResponseEntity.ok(m);
    }

    // ── /loadtest/reset ───────────────────────────────────────────────────────
    @PostMapping("/reset")
    public ResponseEntity<String> reset() {
        requestCount.set(0);
        totalNs.reset();
        minNs.set(Long.MAX_VALUE);
        maxNs.set(0);
        return ResponseEntity.ok("Stats reset");
    }

    // ── helpers ───────────────────────────────────────────────────────────────
    private void applyDelay(long delayMs) throws InterruptedException {
        long effective = delayMs > 0 ? delayMs : defaultDelayMs;
        if (effective > 0) Thread.sleep(effective);
    }

    private void record(long elapsedNs) {
        requestCount.incrementAndGet();
        totalNs.add(elapsedNs);
        minNs.updateAndGet(v -> Math.min(v, elapsedNs));
        maxNs.updateAndGet(v -> Math.max(v, elapsedNs));
    }
}
