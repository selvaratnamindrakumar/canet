package com.canet.poc;

import com.canet.poc.benchmark.BenchmarkResult;
import com.canet.poc.benchmark.BenchmarkService;
import com.canet.poc.model.HandsetRecord;
import com.canet.poc.repository.HandsetRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;

class BenchmarkServiceTest {

    private BenchmarkService benchmarkService;

    @BeforeEach
    void setUp() {
        HandsetRepository mockDynamo = Mockito.mock(HandsetRepository.class);
        HandsetRepository mockAurora = Mockito.mock(HandsetRepository.class);

        HandsetRecord rec = HandsetRecord.builder()
                .cgi("234-10-1234-56789").manufacturer("Samsung").technology("4G").region("London").build();

        when(mockDynamo.findByCgi(anyString())).thenReturn(Optional.of(rec));
        when(mockAurora.findByCgi(anyString())).thenReturn(Optional.of(rec));
        when(mockDynamo.findByTechnology(anyString())).thenReturn(List.of(rec));
        when(mockAurora.findByTechnology(anyString())).thenReturn(List.of(rec));
        when(mockDynamo.findByRegion(anyString())).thenReturn(List.of(rec));
        when(mockAurora.findByRegion(anyString())).thenReturn(List.of(rec));
        when(mockDynamo.backendName()).thenReturn("DynamoDB");
        when(mockAurora.backendName()).thenReturn("Aurora PostgreSQL");

        benchmarkService = new BenchmarkService(mockDynamo, mockAurora);
    }

    @Test
    void cgiLookup_producesStatisticalSummary() {
        BenchmarkResult result = benchmarkService.benchmarkCgiLookup("234-10-1234-56789", 20, 3, false);

        assertThat(result.getIterations()).isEqualTo(20);
        assertThat(result.getWarmupRounds()).isEqualTo(3);
        assertThat(result.getDynamoDb()).isNotNull();
        assertThat(result.getAurora()).isNotNull();
        assertThat(result.getDynamoDb().getMinMs()).isGreaterThanOrEqualTo(0);
        assertThat(result.getDynamoDb().getMaxMs()).isGreaterThanOrEqualTo(result.getDynamoDb().getMinMs());
        assertThat(result.getDynamoDb().getP95Ms()).isGreaterThanOrEqualTo(result.getDynamoDb().getMedianMs());
        assertThat(result.getDynamoDb().getThroughputQps()).isGreaterThan(0);
    }

    @Test
    void benchmark_rawSamplesIncludedWhenRequested() {
        BenchmarkResult result = benchmarkService.benchmarkCgiLookup("cgi", 10, 2, true);

        assertThat(result.getDynamoRawMs()).hasSize(10);
        assertThat(result.getAuroraRawMs()).hasSize(10);
    }

    @Test
    void benchmark_rawSamplesOmittedByDefault() {
        BenchmarkResult result = benchmarkService.benchmarkCgiLookup("cgi", 10, 2, false);

        assertThat(result.getDynamoRawMs()).isNull();
        assertThat(result.getAuroraRawMs()).isNull();
    }

    @Test
    void fullSuite_returnsFourPatterns() {
        var suite = benchmarkService.runFullSuite("234-10-1234-56789", 5, 1);

        assertThat(suite).containsKeys("cgi-lookup", "technology-4g", "technology-5g", "region-london");
        assertThat(suite.values()).allSatisfy(r -> assertThat(r.getFasterBackend()).isNotBlank());
    }

    @Test
    void benchmark_fasterBackendAndSpeedupPresent() {
        BenchmarkResult result = benchmarkService.benchmarkTechnologyScan("4G", 15, 2, false);

        assertThat(result.getFasterBackend()).isIn("DynamoDB", "Aurora PostgreSQL");
        assertThat(result.getSpeedupFactor()).isGreaterThanOrEqualTo(0.0); // 0 when both take <1ms (mocked)
        assertThat(result.getRecommendation()).isNotBlank();
    }
}
