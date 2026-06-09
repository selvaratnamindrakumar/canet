package com.canet.migration;

import com.canet.migration.model.AccumuloKey;
import com.canet.migration.model.DataEntry;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class DataEntryMappingTest {

    @Test
    void accumuloKeyMapsToDataEntry() {
        AccumuloKey key = AccumuloKey.builder()
                .row("sensor:001")
                .columnFamily("reading")
                .columnQualifier("temperature")
                .columnVisibility("PUBLIC")
                .timestamp(1700000000000L)
                .build();

        DataEntry entry = DataEntry.fromAccumulo(key, "22.5");

        assertThat(entry.getPartitionKey()).isEqualTo("sensor:001");
        assertThat(entry.getSortKey()).isEqualTo("reading#temperature");
        assertThat(entry.getColumnFamily()).isEqualTo("reading");
        assertThat(entry.getColumnQualifier()).isEqualTo("temperature");
        assertThat(entry.getColumnVisibility()).isEqualTo("PUBLIC");
        assertThat(entry.getValue()).isEqualTo("22.5");
    }

    @Test
    void dataEntryRoundTripsToAccumuloKey() {
        AccumuloKey original = AccumuloKey.builder()
                .row("user:alice")
                .columnFamily("profile")
                .columnQualifier("email")
                .columnVisibility("ADMIN")
                .timestamp(1700000000000L)
                .build();

        DataEntry entry = DataEntry.fromAccumulo(original, "alice@example.com");
        AccumuloKey reconstructed = entry.toAccumuloKey();

        assertThat(reconstructed.getRow()).isEqualTo(original.getRow());
        assertThat(reconstructed.getColumnFamily()).isEqualTo(original.getColumnFamily());
        assertThat(reconstructed.getColumnQualifier()).isEqualTo(original.getColumnQualifier());
        assertThat(reconstructed.getColumnVisibility()).isEqualTo(original.getColumnVisibility());
        assertThat(reconstructed.getTimestamp()).isEqualTo(original.getTimestamp());
    }
}
