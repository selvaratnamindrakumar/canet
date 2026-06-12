package com.canet.poc.service;

import com.canet.poc.model.HandsetRecord;
import com.canet.poc.repository.HandsetRepository;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.List;
import java.util.Map;

/**
 * Seeds the 5 sample BB/IS records from the POC spec into both backends.
 * These records cover: CGI, device attributes, location attributes, enrichment fields.
 */
@Slf4j
@Service
public class SeedDataService {

    private final HandsetRepository dynamoRepo;
    private final HandsetRepository auroraRepo;

    public SeedDataService(
            @Qualifier("dynamoHandsetRepository")  HandsetRepository dynamoRepo,
            @Qualifier("auroraHandsetRepository")  HandsetRepository auroraRepo) {
        this.dynamoRepo = dynamoRepo;
        this.auroraRepo = auroraRepo;
    }

    public void seedAll() {
        List<HandsetRecord> records = buildSampleRecords();
        log.info("Seeding {} records into DynamoDB and Aurora", records.size());
        dynamoRepo.saveAll(records);
        auroraRepo.saveAll(records);
        log.info("Seed complete");
    }

    private List<HandsetRecord> buildSampleRecords() {
        long now = Instant.now().toEpochMilli();
        return List.of(

            // Record 1: 4G handset, London
            HandsetRecord.builder()
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
                .additionalAttributes(Map.of(
                    "band", "Band-3",
                    "roaming", "false",
                    "bbDataSource", "HANDSET_REF_v2"
                ))
                .lastUpdatedEpochMs(now)
                .dataSource("BB")
                .build(),

            // Record 2: 5G handset, Manchester
            HandsetRecord.builder()
                .cgi("234-20-2345-67890")
                .manufacturer("Apple")
                .model("iPhone 15 Pro")
                .technology("5G")
                .imei("356938035643809")
                .operatingSystem("iOS 17")
                .latitude(53.4808)
                .longitude(-2.2426)
                .cellId("67890")
                .lac("2345")
                .mcc("234")
                .mnc("20")
                .country("UK")
                .region("Manchester")
                .signalStrength("-65dBm")
                .networkType("NR")
                .isp("Vodafone")
                .subscriberTier("PREMIUM")
                .additionalAttributes(Map.of(
                    "band", "n78",
                    "roaming", "false",
                    "isDataSource", "IS_ENRICHMENT_v3"
                ))
                .lastUpdatedEpochMs(now)
                .dataSource("IS")
                .build(),

            // Record 3: 3G legacy handset, Birmingham
            HandsetRecord.builder()
                .cgi("234-30-3456-78901")
                .manufacturer("Nokia")
                .model("3310 3G")
                .technology("3G")
                .imei("012345678901234")
                .operatingSystem("Series 30+")
                .latitude(52.4862)
                .longitude(-1.8904)
                .cellId("78901")
                .lac("3456")
                .mcc("234")
                .mnc("30")
                .country("UK")
                .region("Birmingham")
                .signalStrength("-85dBm")
                .networkType("UMTS")
                .isp("O2")
                .subscriberTier("BASIC")
                .additionalAttributes(Map.of(
                    "legacy", "true",
                    "migrationFlag", "PENDING"
                ))
                .lastUpdatedEpochMs(now)
                .dataSource("BB")
                .build(),

            // Record 4: 4G handset, Edinburgh (Scotland region)
            HandsetRecord.builder()
                .cgi("234-50-4567-89012")
                .manufacturer("Google")
                .model("Pixel 8")
                .technology("4G")
                .imei("867530912345678")
                .operatingSystem("Android 14")
                .latitude(55.9533)
                .longitude(-3.1883)
                .cellId("89012")
                .lac("4567")
                .mcc("234")
                .mnc("50")
                .country("UK")
                .region("Scotland")
                .signalStrength("-70dBm")
                .networkType("LTE")
                .isp("Three")
                .subscriberTier("STANDARD")
                .additionalAttributes(Map.of(
                    "band", "Band-20",
                    "voWifi", "true"
                ))
                .lastUpdatedEpochMs(now)
                .dataSource("BB")
                .build(),

            // Record 5: 2G legacy device, London (IoT / M2M)
            HandsetRecord.builder()
                .cgi("234-10-1234-11111")
                .manufacturer("Sierra Wireless")
                .model("RV55")
                .technology("2G")
                .imei("111122223333444")
                .operatingSystem("RTOS")
                .latitude(51.5033)
                .longitude(-0.1195)
                .cellId("11111")
                .lac("1234")
                .mcc("234")
                .mnc("10")
                .country("UK")
                .region("London")
                .signalStrength("-90dBm")
                .networkType("GSM")
                .isp("EE")
                .subscriberTier("BASIC")
                .additionalAttributes(Map.of(
                    "deviceType", "IoT",
                    "m2m", "true",
                    "sunsetFlag", "2G_SUNSET_2025"
                ))
                .lastUpdatedEpochMs(now)
                .dataSource("IS")
                .build()
        );
    }
}
