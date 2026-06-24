package com.canet.poc;

import com.canet.poc.model.HandsetRecord;
import com.canet.poc.model.NeoApiResponse;
import com.canet.poc.neo.NeoApiService;
import com.canet.poc.repository.HandsetRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

class NeoApiServiceTest {

    private HandsetRepository mockDynamo;
    private HandsetRepository mockAurora;
    private NeoApiService service;

    @BeforeEach
    void setUp() {
        mockDynamo = Mockito.mock(HandsetRepository.class);
        mockAurora = Mockito.mock(HandsetRepository.class);
        when(mockDynamo.backendName()).thenReturn("DynamoDB");
        when(mockAurora.backendName()).thenReturn("Aurora PostgreSQL");
        service = new NeoApiService(mockDynamo, mockAurora, java.util.Optional.empty());
    }

    @Test
    void lookupByCgi_returnsNeoResponseWithDerivedFields() {
        HandsetRecord record = HandsetRecord.builder()
                .cgi("234-10-1234-56789")
                .manufacturer("Samsung")
                .model("Galaxy S23")
                .technology("4G")
                .region("London")
                .country("UK")
                .cellId("56789")
                .subscriberTier("PREMIUM")
                .dataSource("BB")
                .build();

        when(mockDynamo.findByCgi("234-10-1234-56789")).thenReturn(Optional.of(record));

        Optional<NeoApiResponse> result = service.lookupByCgi("234-10-1234-56789", "dynamo");

        assertThat(result).isPresent();
        NeoApiResponse resp = result.get();
        assertThat(resp.getCgi()).isEqualTo("234-10-1234-56789");
        assertThat(resp.getManufacturer()).isEqualTo("Samsung");

        // Post-retrieval: location label assembled from region + country + cellId
        assertThat(resp.getLocationLabel()).contains("London").contains("UK").contains("56789");

        // Post-retrieval: 4G PREMIUM → PREMIUM tier
        assertThat(resp.getNetworkTier()).isEqualTo("PREMIUM");
    }

    @Test
    void networkTierClassification_5G_isPremium() {
        HandsetRecord r5g = HandsetRecord.builder().cgi("x").technology("5G").dataSource("IS").build();
        when(mockAurora.findByCgi("x")).thenReturn(Optional.of(r5g));

        NeoApiResponse resp = service.lookupByCgi("x", "aurora").orElseThrow();
        assertThat(resp.getNetworkTier()).isEqualTo("PREMIUM");
    }

    @Test
    void networkTierClassification_2G_isBasic() {
        HandsetRecord r2g = HandsetRecord.builder().cgi("y").technology("2G").subscriberTier("BASIC").dataSource("BB").build();
        when(mockDynamo.findByCgi("y")).thenReturn(Optional.of(r2g));

        NeoApiResponse resp = service.lookupByCgi("y", "dynamo").orElseThrow();
        assertThat(resp.getNetworkTier()).isEqualTo("BASIC");
    }

    @Test
    void missingCgi_returnsEmpty() {
        when(mockDynamo.findByCgi("unknown")).thenReturn(Optional.empty());
        assertThat(service.lookupByCgi("unknown", "dynamo")).isEmpty();
    }
}
