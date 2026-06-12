package com.canet.poc;

import com.canet.poc.benchmark.ComparisonService;
import com.canet.poc.model.ComparisonResult;
import com.canet.poc.model.HandsetRecord;
import com.canet.poc.neo.NeoApiService;
import com.canet.poc.repository.HandsetRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;

class ComparisonServiceTest {

    private ComparisonService comparisonService;
    private HandsetRepository mockDynamo;
    private HandsetRepository mockAurora;

    @BeforeEach
    void setUp() {
        mockDynamo = Mockito.mock(HandsetRepository.class);
        mockAurora = Mockito.mock(HandsetRepository.class);
        when(mockDynamo.backendName()).thenReturn("DynamoDB");
        when(mockAurora.backendName()).thenReturn("Aurora PostgreSQL");

        HandsetRecord record = HandsetRecord.builder()
                .cgi("234-10-1234-56789")
                .manufacturer("Samsung")
                .model("Galaxy S23")
                .technology("4G")
                .dataSource("BB")
                .build();

        when(mockDynamo.findByCgi(anyString())).thenReturn(Optional.of(record));
        when(mockAurora.findByCgi(anyString())).thenReturn(Optional.of(record));

        NeoApiService neoApiService = new NeoApiService(mockDynamo, mockAurora);
        comparisonService = new ComparisonService(neoApiService);
    }

    @Test
    void compare_returnsBothResultsAndMatrix() {
        ComparisonResult result = comparisonService.compare("234-10-1234-56789");

        assertThat(result.getCgi()).isEqualTo("234-10-1234-56789");
        assertThat(result.getDynamoDbResult()).isNotNull();
        assertThat(result.getAuroraResult()).isNotNull();
        assertThat(result.isResultsMatch()).isTrue();
        assertThat(result.getEvaluation()).isNotNull();
        assertThat(result.getEvaluation().getRecommendation()).isEqualTo("Aurora PostgreSQL");
        assertThat(result.getEvaluation().getRecommendationReasons()).hasSizeGreaterThan(3);
    }

    @Test
    void evaluationMatrix_hasAllSevenCriteria() {
        ComparisonResult.EvaluationMatrix m = comparisonService.compare("x").getEvaluation();

        assertThat(m.getDynamoEaseOfMigration()).isNotBlank();
        assertThat(m.getDynamoQueryFlexibility()).isNotBlank();
        assertThat(m.getDynamoOperationalComplexity()).isNotBlank();
        assertThat(m.getDynamoCostEstimate()).isNotBlank();
        assertThat(m.getAuroraQueryFlexibility()).isNotBlank();
        assertThat(m.getAuroraOperationalComplexity()).isNotBlank();
        assertThat(m.getAuroraCostEstimate()).isNotBlank();
    }
}
