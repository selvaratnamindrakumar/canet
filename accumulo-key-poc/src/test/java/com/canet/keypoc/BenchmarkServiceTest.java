package com.canet.keypoc;

import com.canet.keypoc.benchmark.BenchmarkService;
import com.canet.keypoc.benchmark.EvaluationReport;
import com.canet.keypoc.model.AccumuloCell;
import com.canet.keypoc.neo.NeoPatternRegistry;
import com.canet.keypoc.scanner.AccumuloScannerOp;
import com.canet.keypoc.scanner.CellStoreRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

class BenchmarkServiceTest {

    private CellStoreRepository mockDynamo;
    private CellStoreRepository mockAurora;
    private BenchmarkService service;

    @BeforeEach
    void setUp() {
        mockDynamo = Mockito.mock(CellStoreRepository.class);
        mockAurora = Mockito.mock(CellStoreRepository.class);
        when(mockDynamo.backendName()).thenReturn("DynamoDB");
        when(mockAurora.backendName()).thenReturn("Aurora PostgreSQL");

        // Return a sample cell for key-based patterns
        List<AccumuloCell> sampleCells = List.of(
                AccumuloCell.of("CGI12345", "DEVICE", "MODEL", "PUBLIC", 1750000000000L, "iPhone16")
        );
        when(mockDynamo.execute(any())).thenReturn(sampleCells);
        when(mockAurora.execute(any())).thenReturn(sampleCells);

        service = new BenchmarkService(mockDynamo, mockAurora, new NeoPatternRegistry());
    }

    @Test
    void fullReport_hasAllSections() {
        EvaluationReport report = service.runFullReport();

        assertThat(report.getTotalPatterns()).isEqualTo(8);
        assertThat(report.getDynamoFriendlyCount()).isEqualTo(3);
        assertThat(report.getDynamoEasyPatterns()).hasSize(3);
        assertThat(report.getDynamoHardPatterns()).hasSize(5);
        assertThat(report.getAuroraFriendlyCount()).isEqualTo(8); // SQL handles everything
    }

    @Test
    void fullReport_confidenceLevelsPresent() {
        EvaluationReport report = service.runFullReport();
        assertThat(report.getDynamoConfidence()).contains("HIGH").contains("Composite");
        assertThat(report.getAuroraConfidence()).contains("HIGH");
        assertThat(report.getOracleRdsConfidence()).contains("LOW");
    }

    @Test
    void fullReport_nextDiscoveryStep_mentionsBatchScanner() {
        EvaluationReport report = service.runFullReport();
        assertThat(report.getNextDiscoveryStep()).contains("BatchScanner");
    }

    @Test
    void fullReport_evaluationMatrixHasSevenCriteria() {
        EvaluationReport report = service.runFullReport();
        assertThat(report.getDynamoMatrix()).containsKeys(
                "Ease of migration", "Similarity to Accumulo", "Query flexibility",
                "App changes required", "Operational complexity", "Cost estimate",
                "Long-term maintainability");
        assertThat(report.getAuroraMatrix()).containsKeys(
                "Ease of migration", "Similarity to Accumulo", "Query flexibility",
                "App changes required", "Operational complexity", "Cost estimate",
                "Long-term maintainability");
    }

    @Test
    void runOp_rowScan_returnsBothResults() {
        var result = service.runOp(AccumuloScannerOp.rowScan("CGI12345"));
        assertThat(result.getDynamoResult().getBackend()).isEqualTo("DynamoDB");
        assertThat(result.getAuroraResult().getBackend()).isEqualTo("Aurora PostgreSQL");
        assertThat(result.isResultsMatch()).isTrue();
    }
}
