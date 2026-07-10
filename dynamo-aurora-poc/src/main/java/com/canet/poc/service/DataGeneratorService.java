package com.canet.poc.service;

import com.canet.poc.model.HandsetRecord;
import com.canet.poc.repository.HandsetRepository;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.*;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

/**
 * Generates large synthetic datasets for local load/benchmark testing.
 * Uses only DynamoDB Local + local PostgreSQL — zero AWS sandbox charges.
 *
 * CGI format: MCC-MNC-LAC-CellId  e.g. 234-10-1234-00001
 * Data is deterministic for a given seed index so reruns are comparable.
 */
@Slf4j
@Service
public class DataGeneratorService {

    private static final String[] MANUFACTURERS = {
        "Samsung", "Apple", "Nokia", "Huawei", "Motorola", "OnePlus", "Sony", "Google"
    };
    private static final String[][] MODELS = {
        {"Galaxy S23", "Galaxy S22", "Galaxy A54", "Galaxy Note 20"},
        {"iPhone 15", "iPhone 14", "iPhone 13", "iPhone SE"},
        {"G21", "G50", "X30", "5G Fleet"},
        {"P50 Pro", "Mate 50", "Nova 10", "P30"},
        {"Edge 40", "Moto G73", "G82", "Edge 30"},
        {"OnePlus 11", "OnePlus 10T", "Nord 3", "Nord CE3"},
        {"Xperia 1 V", "Xperia 5 IV", "Xperia 10 V", "Xperia Pro"},
        {"Pixel 8", "Pixel 7a", "Pixel 7 Pro", "Pixel 6"}
    };
    private static final String[] TECHNOLOGIES = {"4G", "5G", "3G"};
    private static final String[] REGIONS = {
        "London", "Scotland", "Wales", "Manchester", "Birmingham",
        "Bristol", "Leeds", "Sheffield", "Liverpool", "Newcastle"
    };
    private static final String[] ISPS = {"EE", "Vodafone", "O2", "Three", "BT Mobile"};
    private static final String[] TIERS = {"PREMIUM", "STANDARD", "BASIC"};
    private static final String[] OS_LIST = {
        "Android 14", "Android 13", "iOS 17", "iOS 16", "Android 12"
    };
    // Approximate lat/lon centres per region index (matches REGIONS order)
    private static final double[] LAT = {51.51, 55.86, 51.48, 53.48, 52.49, 51.45, 53.80, 53.38, 53.41, 54.97};
    private static final double[] LON = {-0.13, -4.25, -3.18, -2.24, -1.90, -2.60, -1.55, -1.47, -2.99, -1.61};

    private final HandsetRepository dynamoRepo;
    private final HandsetRepository auroraRepo;
    private final Optional<HandsetRepository> oracleRepo;

    public DataGeneratorService(
            @Qualifier("dynamoHandsetRepository") HandsetRepository dynamoRepo,
            @Qualifier("auroraHandsetRepository") HandsetRepository auroraRepo,
            @Qualifier("oracleHandsetRepository") Optional<HandsetRepository> oracleRepo) {
        this.dynamoRepo = dynamoRepo;
        this.auroraRepo = auroraRepo;
        this.oracleRepo = oracleRepo;
    }

    /**
     * Generate {@code count} synthetic records, write them to all enabled backends,
     * and return a summary of what was written.
     *
     * @param count         number of records to generate (recommend 100–10000 for local)
     * @param batchSize     how many records to write per saveAll call (default 100)
     * @param clearFirst    wipe existing data before generating
     */
    public GenerationSummary generate(int count, int batchSize, boolean clearFirst) {
        if (clearFirst) {
            dynamoRepo.deleteAll();
            auroraRepo.deleteAll();
            oracleRepo.ifPresent(HandsetRepository::deleteAll);
            log.info("Cleared existing data from all backends");
        }

        log.info("Generating {} synthetic records (batchSize={})", count, batchSize);
        long start = System.currentTimeMillis();

        List<HandsetRecord> allRecords = IntStream.range(0, count)
                .mapToObj(this::buildRecord)
                .collect(Collectors.toList());

        // Write in batches to avoid memory pressure
        int dynamoWritten  = writeBatched(dynamoRepo, allRecords, batchSize);
        int auroraWritten  = writeBatched(auroraRepo, allRecords, batchSize);
        int oracleWritten  = oracleRepo.map(r -> writeBatched(r, allRecords, batchSize)).orElse(0);

        long durationMs = System.currentTimeMillis() - start;
        log.info("Generation complete: {} records in {}ms", count, durationMs);

        // Count breakdown by dimension (useful for verifying spread)
        Map<String, Long> byTech   = allRecords.stream().collect(Collectors.groupingBy(HandsetRecord::getTechnology, Collectors.counting()));
        Map<String, Long> byRegion = allRecords.stream().collect(Collectors.groupingBy(HandsetRecord::getRegion, Collectors.counting()));
        Map<String, Long> byMfr    = allRecords.stream().collect(Collectors.groupingBy(HandsetRecord::getManufacturer, Collectors.counting()));

        return GenerationSummary.builder()
                .requested(count)
                .dynamoWritten(dynamoWritten)
                .auroraWritten(auroraWritten)
                .oracleWritten(oracleWritten)
                .durationMs(durationMs)
                .byTechnology(byTech)
                .byRegion(byRegion)
                .byManufacturer(byMfr)
                .sampleCgi(allRecords.get(0).getCgi())
                .build();
    }

    /** Build a single deterministic synthetic record for index {@code i}. */
    public HandsetRecord buildRecord(int i) {
        int mfrIdx  = i % MANUFACTURERS.length;
        int techIdx = i % TECHNOLOGIES.length;
        int regIdx  = i % REGIONS.length;
        int ispIdx  = i % ISPS.length;
        int tierIdx = i % TIERS.length;
        int osIdx   = i % OS_LIST.length;

        // Spread over realistic UK MNCs (EE=30, Vodafone=15, O2=10, Three=20)
        String[] mncs = {"30", "15", "10", "20", "30"};
        String mnc = mncs[i % mncs.length];
        int lac    = 1000 + (i % 900);
        int cellId = 10000 + i;

        String cgi = String.format("234-%s-%d-%05d", mnc, lac, cellId);

        // Vary lat/lon slightly around region centre so records look realistic
        double jitterLat = ((i * 7) % 100 - 50) / 1000.0;
        double jitterLon = ((i * 13) % 100 - 50) / 1000.0;

        // Vary timestamp: spread over last 400 days so age-off tests work
        long ageDays = i % 400;
        long ts = Instant.now().minus(ageDays, ChronoUnit.DAYS).toEpochMilli();

        return HandsetRecord.builder()
                .cgi(cgi)
                .manufacturer(MANUFACTURERS[mfrIdx])
                .model(MODELS[mfrIdx][i % MODELS[mfrIdx].length])
                .technology(TECHNOLOGIES[techIdx])
                .imei(String.format("%015d", (long) (i + 1) * 310000000L))
                .operatingSystem(OS_LIST[osIdx])
                .latitude(LAT[regIdx] + jitterLat)
                .longitude(LON[regIdx] + jitterLon)
                .cellId(String.valueOf(cellId))
                .lac(String.valueOf(lac))
                .mcc("234")
                .mnc(mnc)
                .country("UK")
                .region(REGIONS[regIdx])
                .signalStrength((-60 - (i % 40)) + "dBm")
                .networkType(TECHNOLOGIES[techIdx].equals("4G") ? "LTE" :
                             TECHNOLOGIES[techIdx].equals("5G") ? "NR"  : "HSPA")
                .isp(ISPS[ispIdx])
                .subscriberTier(TIERS[tierIdx])
                .additionalAttributes(Map.of(
                        "band",      "Band-" + (i % 7 + 1),
                        "roaming",   i % 5 == 0 ? "true" : "false",
                        "dataSource","BB"))
                .lastUpdatedEpochMs(ts)
                .dataSource(i % 2 == 0 ? "BB" : "IS")
                .build();
    }

    private int writeBatched(HandsetRepository repo, List<HandsetRecord> all, int batchSize) {
        int written = 0;
        for (int i = 0; i < all.size(); i += batchSize) {
            List<HandsetRecord> batch = all.subList(i, Math.min(i + batchSize, all.size()));
            repo.saveAll(batch);
            written += batch.size();
            log.debug("[{}] wrote batch {}/{}", repo.backendName(), written, all.size());
        }
        return written;
    }

    // ── Summary record ────────────────────────────────────────────────────────

    @lombok.Data
    @lombok.Builder
    public static class GenerationSummary {
        private int requested;
        private int dynamoWritten;
        private int auroraWritten;
        private int oracleWritten;
        private long durationMs;
        private String sampleCgi;
        private Map<String, Long> byTechnology;
        private Map<String, Long> byRegion;
        private Map<String, Long> byManufacturer;
    }
}
