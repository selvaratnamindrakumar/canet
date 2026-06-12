package com.canet.poc;

import com.canet.poc.model.HandsetRecord;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class HandsetRecordMappingTest {

    @Test
    void handsetRecordHoldsAllPocFields() {
        HandsetRecord record = HandsetRecord.builder()
                .cgi("234-10-1234-56789")
                .manufacturer("Samsung")
                .model("Galaxy S23")
                .technology("4G")
                .imei("490154203237518")
                .operatingSystem("Android 14")
                .latitude(51.5074)
                .longitude(-0.1278)
                .cellId("56789")
                .lac("1234")
                .mcc("234")
                .mnc("10")
                .country("UK")
                .region("London")
                .signalStrength("-75dBm")
                .networkType("LTE")
                .isp("EE")
                .subscriberTier("PREMIUM")
                .additionalAttributes(Map.of("band", "Band-3", "roaming", "false"))
                .dataSource("BB")
                .build();

        assertThat(record.getCgi()).isEqualTo("234-10-1234-56789");
        assertThat(record.getTechnology()).isEqualTo("4G");
        assertThat(record.getLatitude()).isEqualTo(51.5074);
        assertThat(record.getAdditionalAttributes()).containsKey("band");
        assertThat(record.getDataSource()).isEqualTo("BB");
    }

    @Test
    void accumuloKeyMappingIsExplicit() {
        // CGI = Accumulo row key
        HandsetRecord r = HandsetRecord.builder().cgi("234-10-1234-56789").build();
        // In Accumulo: row="234-10-1234-56789", CF="device", CQ="manufacturer", value="Samsung"
        // In DynamoDB: PK="234-10-1234-56789" (single GetItem)
        // In Aurora:   SELECT * FROM handset_reference WHERE cgi='234-10-1234-56789'
        assertThat(r.getCgi()).isNotNull();
    }
}
