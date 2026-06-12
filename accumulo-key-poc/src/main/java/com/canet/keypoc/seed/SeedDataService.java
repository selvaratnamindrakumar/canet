package com.canet.keypoc.seed;

import com.canet.keypoc.model.AccumuloCell;
import com.canet.keypoc.scanner.CellStoreRepository;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * Inserts the POC sample dataset into both backends.
 *
 * Each record mirrors exactly how Accumulo stores it:
 *   AccumuloCell(rowId=CGI, family, qualifier, visibility, timestamp) → value
 *
 * This directly tests the composite key design:
 *   DynamoDB: PK=CGI  SK=FAMILY#QUALIFIER#TIMESTAMP
 *   Aurora:   (cgi, family, qualifier, ts) → value
 */
@Slf4j
@Service
public class SeedDataService {

    private final CellStoreRepository dynamo;
    private final CellStoreRepository aurora;

    public SeedDataService(
            @Qualifier("dynamoCellStore")  CellStoreRepository dynamo,
            @Qualifier("auroraCellStore")  CellStoreRepository aurora) {
        this.dynamo = dynamo;
        this.aurora = aurora;
    }

    public void seedAll() {
        List<AccumuloCell> cells = buildSampleCells();
        log.info("Seeding {} cells into DynamoDB and Aurora", cells.size());
        dynamo.saveAll(cells);
        aurora.saveAll(cells);
        log.info("Seed complete — test with GET /api/poc/row/CGI12345");
    }

    public void clearAll() {
        dynamo.deleteAll();
        aurora.deleteAll();
    }

    private List<AccumuloCell> buildSampleCells() {
        long ts1 = 1700000000000L; // 2023-11-14 — first version
        long ts2 = 1750000000000L; // 2025-06-15 — updated version

        return List.of(
            // ── CGI12345 — Samsung 4G London ──────────────────────────────────
            //    Demonstrates: DEVICE family, LOCATION family, versioned DEVICE#MODEL
            cell("CGI12345", "DEVICE",     "MANUFACTURER", "PUBLIC",     ts1, "Samsung"),
            cell("CGI12345", "DEVICE",     "MODEL",        "PUBLIC",     ts1, "Galaxy S21"),
            cell("CGI12345", "DEVICE",     "MODEL",        "PUBLIC",     ts2, "Galaxy S23"),  // updated version
            cell("CGI12345", "DEVICE",     "OS",           "PUBLIC",     ts2, "Android 14"),
            cell("CGI12345", "DEVICE",     "TECHNOLOGY",   "PUBLIC",     ts2, "4G"),
            cell("CGI12345", "LOCATION",   "REGION",       "PUBLIC",     ts2, "London"),
            cell("CGI12345", "LOCATION",   "COUNTRY",      "PUBLIC",     ts2, "UK"),
            cell("CGI12345", "LOCATION",   "LAT",          "PUBLIC",     ts2, "51.5074"),
            cell("CGI12345", "LOCATION",   "LON",          "PUBLIC",     ts2, "-0.1278"),
            cell("CGI12345", "LOCATION",   "CELL_ID",      "PUBLIC",     ts2, "56789"),
            cell("CGI12345", "ENRICHMENT", "ISP",          "PUBLIC",     ts2, "EE"),
            cell("CGI12345", "ENRICHMENT", "SIGNAL",       "ADMIN",      ts2, "-75dBm"),
            cell("CGI12345", "ENRICHMENT", "SUBSCRIBER",   "ADMIN",      ts2, "PREMIUM"),

            // ── CGI67890 — Apple 5G Manchester ────────────────────────────────
            cell("CGI67890", "DEVICE",     "MANUFACTURER", "PUBLIC",     ts2, "Apple"),
            cell("CGI67890", "DEVICE",     "MODEL",        "PUBLIC",     ts2, "iPhone16"),
            cell("CGI67890", "DEVICE",     "OS",           "PUBLIC",     ts2, "iOS18"),
            cell("CGI67890", "DEVICE",     "TECHNOLOGY",   "PUBLIC",     ts2, "5G"),
            cell("CGI67890", "LOCATION",   "REGION",       "PUBLIC",     ts2, "Manchester"),
            cell("CGI67890", "LOCATION",   "COUNTRY",      "PUBLIC",     ts2, "UK"),
            cell("CGI67890", "LOCATION",   "LAT",          "PUBLIC",     ts2, "53.4808"),
            cell("CGI67890", "LOCATION",   "LON",          "PUBLIC",     ts2, "-2.2426"),
            cell("CGI67890", "ENRICHMENT", "ISP",          "PUBLIC",     ts2, "Vodafone"),
            cell("CGI67890", "ENRICHMENT", "SIGNAL",       "ADMIN",      ts2, "-65dBm"),
            cell("CGI67890", "ENRICHMENT", "SUBSCRIBER",   "ADMIN",      ts2, "PREMIUM"),

            // ── CGI11111 — Nokia 3G Birmingham (legacy, migration candidate) ──
            cell("CGI11111", "DEVICE",     "MANUFACTURER", "PUBLIC",     ts1, "Nokia"),
            cell("CGI11111", "DEVICE",     "MODEL",        "PUBLIC",     ts1, "3310 3G"),
            cell("CGI11111", "DEVICE",     "TECHNOLOGY",   "PUBLIC",     ts1, "3G"),
            cell("CGI11111", "LOCATION",   "REGION",       "PUBLIC",     ts1, "Birmingham"),
            cell("CGI11111", "LOCATION",   "COUNTRY",      "PUBLIC",     ts1, "UK"),
            cell("CGI11111", "ENRICHMENT", "ISP",          "PUBLIC",     ts1, "O2"),
            cell("CGI11111", "ENRICHMENT", "SUBSCRIBER",   "PUBLIC",     ts1, "BASIC"),

            // ── CGI22222 — Google 4G Scotland ─────────────────────────────────
            cell("CGI22222", "DEVICE",     "MANUFACTURER", "PUBLIC",     ts2, "Google"),
            cell("CGI22222", "DEVICE",     "MODEL",        "PUBLIC",     ts2, "Pixel8"),
            cell("CGI22222", "DEVICE",     "TECHNOLOGY",   "PUBLIC",     ts2, "4G"),
            cell("CGI22222", "LOCATION",   "REGION",       "PUBLIC",     ts2, "Scotland"),
            cell("CGI22222", "LOCATION",   "COUNTRY",      "PUBLIC",     ts2, "UK"),
            cell("CGI22222", "ENRICHMENT", "ISP",          "PUBLIC",     ts2, "Three"),
            cell("CGI22222", "ENRICHMENT", "SUBSCRIBER",   "ADMIN",      ts2, "STANDARD"),

            // ── CGI33333 — Sierra Wireless 2G IoT London ──────────────────────
            cell("CGI33333", "DEVICE",     "MANUFACTURER", "PUBLIC",     ts1, "Sierra Wireless"),
            cell("CGI33333", "DEVICE",     "MODEL",        "PUBLIC",     ts1, "RV55"),
            cell("CGI33333", "DEVICE",     "TECHNOLOGY",   "PUBLIC",     ts1, "2G"),
            cell("CGI33333", "LOCATION",   "REGION",       "PUBLIC",     ts1, "London"),
            cell("CGI33333", "LOCATION",   "COUNTRY",      "PUBLIC",     ts1, "UK"),
            cell("CGI33333", "ENRICHMENT", "DEVICE_TYPE",  "PUBLIC",     ts1, "IoT"),
            cell("CGI33333", "ENRICHMENT", "SUBSCRIBER",   "PUBLIC",     ts1, "BASIC")
        );
    }

    private AccumuloCell cell(String rowId, String family, String qualifier,
                               String visibility, long ts, String value) {
        return AccumuloCell.of(rowId, family, qualifier, visibility, ts, value);
    }
}
