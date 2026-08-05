package com.canet.app.load;

import com.canet.app.config.CaNetProperties;
import com.canet.app.controller.DataController;
import com.canet.app.service.HttpsDataService;
import com.canet.app.service.HttpsDataService.TacSearchResult;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.test.web.servlet.MockMvc;

import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.LongAdder;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Concurrent load test for the main application layer.
 *
 * Uses MockMvc (in-process) with a stubbed data service so the test is
 * self-contained — no running mock server required.  This measures the
 * app's own processing throughput: request routing, TAC/IMEI parsing,
 * field-mapping logic, JSON flattening, and Thymeleaf template rendering.
 *
 * Run with:
 *   mvn test -pl spring-boot-app -Dtest=ConcurrentSearchLoadTest -Dsurefire.failIfNoSpecifiedTests=false
 *
 * Results are printed to stdout after each test case — look for the
 * ╔═══ CaNet Load Test Report ═══╗ block.
 */
@WebMvcTest(DataController.class)
class ConcurrentSearchLoadTest {

    // ── Test parameters — adjust to match your target environment ─────────────
    private static final int THREADS              = 20;
    private static final int REQUESTS_PER_THREAD  = 25;
    private static final int TOTAL                = THREADS * REQUESTS_PER_THREAD;
    private static final int TIMEOUT_SECONDS      = 60;

    // Sample TAC entries for handset mode
    private static final String HANDSET_ENTRIES =
            "35674108,35282402,35428109,86345601,35567812,86456723,35678934,35789045,35890156,35012367";

    // Sample IMEI entries for cell mode (15 digits each → TAC = first 8)
    private static final String CELL_ENTRIES =
            "353456789012345,354567890123456,352678012345678,866789012345678,867890123456789";

    @Autowired MockMvc             mockMvc;
    @MockBean  HttpsDataService    dataService;
    @MockBean  CaNetProperties     caNetProperties;

    @BeforeEach
    void setup() {
        CaNetProperties.EndpointMapping handset = buildMapping("handset", true,
                List.of("inputValue", "tac", "marketingName", "manufacturer", "modelName", "operatingSystem"));
        CaNetProperties.EndpointMapping cell = buildMapping("cell", false,
                List.of("inputValue", "tac", "marketingName", "manufacturer", "modelName"));

        when(caNetProperties.getEndpoints()).thenReturn(List.of(handset, cell));
        when(caNetProperties.findDefault()).thenReturn(handset);
        when(caNetProperties.findByType("handset")).thenReturn(handset);
        when(caNetProperties.findByType("cell")).thenReturn(cell);

        // Stub returns 20 realistic-sized result rows for any TAC list
        when(dataService.fetchByTacs(any(), any()))
                .thenReturn(new TacSearchResult(buildSampleRows(20), List.of()));
    }

    // =========================================================================
    // Load test: Handset mode — 20 threads × 25 requests = 500 total
    // =========================================================================

    @Test
    @DisplayName("Load: Handset TAC search — " + THREADS + " threads × " + REQUESTS_PER_THREAD + " requests")
    void load_handsetMode_concurrent() throws Exception {
        LoadReport report = runLoad("handset", HANDSET_ENTRIES);
        report.print("Handset");
        assertEquals(0,     report.errors,   "Zero errors expected under load");
        assertEquals(TOTAL, report.successes, "All requests must complete");
    }

    // =========================================================================
    // Load test: Cell mode — IMEI → TAC extraction + annotation
    // =========================================================================

    @Test
    @DisplayName("Load: Cell IMEI search — " + THREADS + " threads × " + REQUESTS_PER_THREAD + " requests")
    void load_cellMode_concurrent() throws Exception {
        LoadReport report = runLoad("cell", CELL_ENTRIES);
        report.print("Cell");
        assertEquals(0,     report.errors,   "Zero errors expected under load");
        assertEquals(TOTAL, report.successes, "All requests must complete");
    }

    // =========================================================================
    // Load test: Mix of handset and cell mode requests
    // =========================================================================

    @Test
    @DisplayName("Load: Mixed mode — alternating Handset/Cell across " + THREADS + " threads")
    void load_mixedMode_concurrent() throws Exception {
        ExecutorService executor = Executors.newFixedThreadPool(THREADS);
        CountDownLatch  ready    = new CountDownLatch(THREADS);
        CountDownLatch  start    = new CountDownLatch(1);
        CountDownLatch  done     = new CountDownLatch(TOTAL);

        AtomicInteger successCount = new AtomicInteger();
        AtomicInteger errorCount   = new AtomicInteger();
        LongAdder     totalNs      = new LongAdder();
        AtomicLong    minNs        = new AtomicLong(Long.MAX_VALUE);
        AtomicLong    maxNs        = new AtomicLong(0);

        for (int t = 0; t < THREADS; t++) {
            final int threadId = t;
            executor.submit(() -> {
                ready.countDown();
                try { start.await(); } catch (InterruptedException e) { Thread.currentThread().interrupt(); return; }

                for (int r = 0; r < REQUESTS_PER_THREAD; r++) {
                    String mode    = (threadId % 2 == 0) ? "handset" : "cell";
                    String entries = (threadId % 2 == 0) ? HANDSET_ENTRIES : CELL_ENTRIES;
                    long   t0      = System.nanoTime();
                    try {
                        mockMvc.perform(get("/?mode=" + mode + "&entries=" + entries))
                               .andExpect(status().isOk());
                        long elapsed = System.nanoTime() - t0;
                        totalNs.add(elapsed);
                        successCount.incrementAndGet();
                        minNs.updateAndGet(v -> Math.min(v, elapsed));
                        maxNs.updateAndGet(v -> Math.max(v, elapsed));
                    } catch (Exception e) {
                        errorCount.incrementAndGet();
                    } finally {
                        done.countDown();
                    }
                }
            });
        }

        ready.await();
        long wallStart = System.nanoTime();
        start.countDown();
        boolean completed = done.await(TIMEOUT_SECONDS, TimeUnit.SECONDS);
        long wallNs = System.nanoTime() - wallStart;
        executor.shutdown();

        assertTrue(completed, "Mixed load test did not complete within " + TIMEOUT_SECONDS + "s");
        LoadReport report = new LoadReport(successCount.get(), errorCount.get(),
                totalNs.sum(), minNs.get(), maxNs.get(), wallNs);
        report.print("Mixed");
        assertEquals(0,     report.errors,   "Zero errors expected under mixed load");
        assertEquals(TOTAL, report.successes, "All requests must complete");
    }

    // =========================================================================
    // Load test: with JSON flatten enabled
    // =========================================================================

    @Test
    @DisplayName("Load: Handset with JSON flatten — " + TOTAL + " requests")
    void load_withFlatten_concurrent() throws Exception {
        // Override to enable flatten and return nested JSON
        CaNetProperties.EndpointMapping handsetFlat = buildMapping("handset", true,
                List.of("inputValue", "tac", "marketingName", "manufacturer"));
        handsetFlat.setFlatten(true);
        handsetFlat.setFlattenSeparator("-");

        when(caNetProperties.findDefault()).thenReturn(handsetFlat);
        when(caNetProperties.findByType("handset")).thenReturn(handsetFlat);

        // Return rows with nested maps to exercise flattening logic
        when(dataService.fetchByTacs(any(), any()))
                .thenReturn(new TacSearchResult(buildNestedRows(20), List.of()));

        LoadReport report = runLoad("handset", HANDSET_ENTRIES);
        report.print("Flatten");
        assertEquals(0,     report.errors,   "Zero errors expected under flatten load");
        assertEquals(TOTAL, report.successes, "All requests must complete");
    }

    // =========================================================================
    // Helpers
    // =========================================================================

    private LoadReport runLoad(String mode, String entries) throws InterruptedException {
        ExecutorService executor = Executors.newFixedThreadPool(THREADS);
        CountDownLatch  ready    = new CountDownLatch(THREADS);
        CountDownLatch  start    = new CountDownLatch(1);
        CountDownLatch  done     = new CountDownLatch(TOTAL);

        AtomicInteger successCount = new AtomicInteger();
        AtomicInteger errorCount   = new AtomicInteger();
        LongAdder     totalNs      = new LongAdder();
        AtomicLong    minNs        = new AtomicLong(Long.MAX_VALUE);
        AtomicLong    maxNs        = new AtomicLong(0);

        for (int t = 0; t < THREADS; t++) {
            executor.submit(() -> {
                ready.countDown();
                try { start.await(); } catch (InterruptedException e) { Thread.currentThread().interrupt(); return; }

                for (int r = 0; r < REQUESTS_PER_THREAD; r++) {
                    long t0 = System.nanoTime();
                    try {
                        mockMvc.perform(get("/?mode=" + mode + "&entries=" + entries))
                               .andExpect(status().isOk());
                        long elapsed = System.nanoTime() - t0;
                        totalNs.add(elapsed);
                        successCount.incrementAndGet();
                        minNs.updateAndGet(v -> Math.min(v, elapsed));
                        maxNs.updateAndGet(v -> Math.max(v, elapsed));
                    } catch (Exception e) {
                        errorCount.incrementAndGet();
                    } finally {
                        done.countDown();
                    }
                }
            });
        }

        ready.await();
        long wallStart = System.nanoTime();
        start.countDown();
        boolean completed = done.await(TIMEOUT_SECONDS, TimeUnit.SECONDS);
        long wallNs = System.nanoTime() - wallStart;
        executor.shutdown();

        assertTrue(completed, "Load test did not complete within " + TIMEOUT_SECONDS + "s");
        return new LoadReport(successCount.get(), errorCount.get(),
                totalNs.sum(), minNs.get(), maxNs.get(), wallNs);
    }

    private CaNetProperties.EndpointMapping buildMapping(
            String type, boolean isDefault, List<String> compact) {

        CaNetProperties.EndpointMapping m = new CaNetProperties.EndpointMapping();
        m.setType(type);
        m.setUrl("https://test.example.com");
        m.setDefaultEndpoint(isDefault);
        m.setCompactFields(compact);
        m.setDetailFields(List.of("*"));
        m.setLabels(Map.of("inputValue", "Searched Value", "tac", "TAC"));
        m.setFlatten(false);
        m.setFlattenSeparator("-");
        m.setMaxEntries(20);
        return m;
    }

    private List<Map<String, Object>> buildSampleRows(int count) {
        String[] mfrs = {"Samsung", "Apple", "Google", "OnePlus", "Xiaomi",
                         "Sony", "Motorola", "Nokia", "Realme", "Vivo"};
        List<Map<String, Object>> rows = new ArrayList<>(count);
        for (int i = 0; i < count; i++) {
            Map<String, Object> row = new LinkedHashMap<>();
            row.put("tac",             String.format("35%06d", i));
            row.put("marketingName",   mfrs[i % mfrs.length] + " Device " + i);
            row.put("manufacturer",    mfrs[i % mfrs.length]);
            row.put("modelName",       "MDL-" + i);
            row.put("operatingSystem", i % 5 == 1 ? "iOS" : "Android");
            row.put("osVersion",       String.valueOf(12 + (i % 3)));
            row.put("displaySizeInches", 6.1 + (i % 5) * 0.1);
            row.put("releaseYear",     2021 + (i % 4));
            rows.add(row);
        }
        return rows;
    }

    private List<Map<String, Object>> buildNestedRows(int count) {
        List<Map<String, Object>> rows = buildSampleRows(count);
        rows.forEach(row -> {
            Map<String, Object> network = new LinkedHashMap<>();
            network.put("generations", "2G/3G/4G/5G");
            network.put("type", "NSA/SA");
            row.put("network", network);

            Map<String, Object> caps = new LinkedHashMap<>();
            caps.put("nfc", true);
            caps.put("wirelessCharging", true);
            row.put("capabilities", caps);
        });
        return rows;
    }

    // ─── Report ────────────────────────────────────────────────────────────────

    private static class LoadReport {
        final int    successes;
        final int    errors;
        final double avgMs;
        final double minMs;
        final double maxMs;
        final double wallSec;
        final double rps;

        LoadReport(int successes, int errors, long totalNs, long minNs, long maxNs, long wallNs) {
            this.successes = successes;
            this.errors    = errors;
            this.wallSec   = wallNs  / 1_000_000_000.0;
            this.rps       = TOTAL   / this.wallSec;
            this.avgMs     = successes > 0 ? totalNs / 1_000_000.0 / successes : 0;
            this.minMs     = (minNs < Long.MAX_VALUE) ? minNs / 1_000_000.0 : 0;
            this.maxMs     = maxNs  / 1_000_000.0;
        }

        void print(String label) {
            System.out.printf("%n╔══════════════════════════════════════════════╗%n");
            System.out.printf("║  CaNet Load Test Report — %-18s║%n", label);
            System.out.printf("╠══════════════════════════════════════════════╣%n");
            System.out.printf("║  Threads          : %-25d║%n", THREADS);
            System.out.printf("║  Requests/thread  : %-25d║%n", REQUESTS_PER_THREAD);
            System.out.printf("║  Total requests   : %-25d║%n", TOTAL);
            System.out.printf("║  Successes        : %-25d║%n", successes);
            System.out.printf("║  Errors           : %-25d║%n", errors);
            System.out.printf("║  Wall time        : %-21.3f sec ║%n", wallSec);
            System.out.printf("║  Throughput       : %-18.1f req/s ║%n", rps);
            System.out.printf("║  Avg latency      : %-18.2f ms   ║%n", avgMs);
            System.out.printf("║  Min latency      : %-18.2f ms   ║%n", minMs);
            System.out.printf("║  Max latency      : %-18.2f ms   ║%n", maxMs);
            System.out.printf("╚══════════════════════════════════════════════╝%n");
        }
    }
}
