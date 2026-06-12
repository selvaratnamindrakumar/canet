package com.canet.keypoc;

import com.canet.keypoc.model.AccumuloCell;
import com.canet.keypoc.model.AccumuloKey;
import com.canet.keypoc.scanner.AccumuloScannerOp;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Tests that the Accumulo key model and DynamoDB sort key helpers are correct.
 * These are the building blocks that Catherine's composite key design depends on.
 */
class AccumuloKeyModelTest {

    @Test
    void dynamoSortKeyCompositeFormat() {
        AccumuloKey key = AccumuloKey.builder()
                .rowId("CGI12345")
                .columnFamily("DEVICE")
                .columnQualifier("MODEL")
                .columnVisibility("PUBLIC")
                .timestamp(1750000000000L)
                .build();

        // DynamoDB SK must be FAMILY#QUALIFIER#TIMESTAMP for begins_with to work
        assertThat(key.toDynamoSortKey()).isEqualTo("DEVICE#MODEL#1750000000000");
    }

    @Test
    void familyPrefixForBeginsWith() {
        // DynamoDB: SK begins_with "DEVICE#" — must include the # separator
        assertThat(AccumuloKey.familyPrefix("DEVICE")).isEqualTo("DEVICE#");
    }

    @Test
    void familyQualifierPrefixForCellLookup() {
        // DynamoDB: SK begins_with "DEVICE#MODEL#" — includes both separators
        assertThat(AccumuloKey.familyQualifierPrefix("DEVICE", "MODEL")).isEqualTo("DEVICE#MODEL#");
    }

    @Test
    void scannerOpDescriptions_matchAccumuloApiCalls() {
        AccumuloScannerOp rowScan = AccumuloScannerOp.rowScan("CGI12345");
        assertThat(rowScan.describe()).contains("setRange").contains("CGI12345");
        assertThat(rowScan.usesFamilyOrQualifierFilter()).isFalse();

        AccumuloScannerOp familyScan = AccumuloScannerOp.familyScan("CGI12345", "DEVICE");
        assertThat(familyScan.describe()).contains("fetchColumnFamily").contains("DEVICE");
        assertThat(familyScan.usesFamilyOrQualifierFilter()).isTrue();

        AccumuloScannerOp cellLookup = AccumuloScannerOp.cellLookup("CGI12345", "DEVICE", "MODEL");
        assertThat(cellLookup.describe()).contains("fetchColumn").contains("DEVICE").contains("MODEL");
        assertThat(cellLookup.usesFamilyOrQualifierFilter()).isTrue();

        AccumuloScannerOp versioned = AccumuloScannerOp.versionedLookup("CGI12345", "DEVICE", "MODEL",
                1700000000000L, 1750000000000L);
        assertThat(versioned.describe()).contains("setTimestampRange");
        assertThat(versioned.usesFamilyOrQualifierFilter()).isTrue();
    }

    @Test
    void accumuloCellConstructor_setsAllKeyParts() {
        AccumuloCell cell = AccumuloCell.of("CGI12345", "DEVICE", "MODEL", "PUBLIC", 1750000000000L, "iPhone16");
        assertThat(cell.getKey().getRowId()).isEqualTo("CGI12345");
        assertThat(cell.getKey().getColumnFamily()).isEqualTo("DEVICE");
        assertThat(cell.getKey().getColumnQualifier()).isEqualTo("MODEL");
        assertThat(cell.getKey().getColumnVisibility()).isEqualTo("PUBLIC");
        assertThat(cell.getKey().getTimestamp()).isEqualTo(1750000000000L);
        assertThat(cell.getValue()).isEqualTo("iPhone16");
    }

    @Test
    void toDynamoSortKey_usedAsBeginsWith_prefix_doesNotMatchSiblings() {
        // When SK is "DEVICE#MODEL#1750000000000"
        // begins_with("DEVICE#MODEL#") MATCHES it
        // begins_with("DEVICE#") MATCHES it (family scan)
        // begins_with("LOCATION#") does NOT match — correct server-side filtering

        String sk = AccumuloKey.builder()
                .columnFamily("DEVICE").columnQualifier("MODEL").timestamp(1750000000000L)
                .build().toDynamoSortKey();

        assertThat(sk.startsWith(AccumuloKey.familyPrefix("DEVICE"))).isTrue();
        assertThat(sk.startsWith(AccumuloKey.familyQualifierPrefix("DEVICE", "MODEL"))).isTrue();
        assertThat(sk.startsWith(AccumuloKey.familyPrefix("LOCATION"))).isFalse();  // correct filtering
        assertThat(sk.startsWith(AccumuloKey.familyQualifierPrefix("DEVICE", "OS"))).isFalse();  // qualifier filter
    }
}
