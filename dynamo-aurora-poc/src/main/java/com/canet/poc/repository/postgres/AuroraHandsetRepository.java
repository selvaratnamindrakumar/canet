package com.canet.poc.repository.postgres;

import com.canet.poc.model.HandsetRecord;
import com.canet.poc.repository.HandsetRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

@Repository("auroraHandsetRepository")
@RequiredArgsConstructor
public class AuroraHandsetRepository implements HandsetRepository {

    private final HandsetJpaRepository jpa;

    @Override
    public Optional<HandsetRecord> findByCgi(String cgi) {
        return jpa.findById(cgi).map(this::toDomain);
    }

    /**
     * Aurora projection: the SQL query only fetches the requested columns.
     * This is structurally more efficient for partial-record access than DynamoDB,
     * where the full item is always read from storage even with ProjectionExpression.
     */
    @Override
    public Optional<HandsetRecord> findByCgiWithFields(String cgi, List<String> fields) {
        if (fields != null && !fields.isEmpty() &&
                fields.stream().allMatch(f -> List.of("cgi","manufacturer","model","technology","region","country","dataSource").contains(f))) {
            return jpa.findSummaryByCgi(cgi).map(this::projectionToDomain);
        }
        return findByCgi(cgi);
    }

    @Override
    public List<HandsetRecord> findByTechnology(String technology) {
        return jpa.findByTechnology(technology).stream().map(this::toDomain).collect(Collectors.toList());
    }

    @Override
    public List<HandsetRecord> findByRegion(String region) {
        return jpa.findByRegion(region).stream().map(this::toDomain).collect(Collectors.toList());
    }

    @Override
    public List<HandsetRecord> findByManufacturer(String manufacturer) {
        return jpa.findByManufacturer(manufacturer).stream().map(this::toDomain).collect(Collectors.toList());
    }

    @Override
    public List<HandsetRecord> findByRegionAndTechnology(String region, String technology) {
        return jpa.findByRegionAndTechnology(region, technology).stream().map(this::toDomain).collect(Collectors.toList());
    }

    @Override
    public void save(HandsetRecord record) {
        jpa.save(toEntity(record));
    }

    @Override
    public void saveAll(List<HandsetRecord> records) {
        jpa.saveAll(records.stream().map(this::toEntity).collect(Collectors.toList()));
    }

    @Override
    public List<HandsetRecord> findAll() {
        return jpa.findAll().stream().map(this::toDomain).collect(Collectors.toList());
    }

    @Override
    public void deleteAll() {
        jpa.deleteAll();
    }

    @Override
    public String backendName() { return "Aurora PostgreSQL"; }

    // ── Mapping ───────────────────────────────────────────────────────────────

    private HandsetEntity toEntity(HandsetRecord r) {
        return HandsetEntity.builder()
                .cgi(r.getCgi())
                .manufacturer(r.getManufacturer())
                .model(r.getModel())
                .technology(r.getTechnology())
                .imei(r.getImei())
                .operatingSystem(r.getOperatingSystem())
                .latitude(r.getLatitude())
                .longitude(r.getLongitude())
                .cellId(r.getCellId())
                .lac(r.getLac())
                .mcc(r.getMcc())
                .mnc(r.getMnc())
                .country(r.getCountry())
                .region(r.getRegion())
                .signalStrength(r.getSignalStrength())
                .networkType(r.getNetworkType())
                .isp(r.getIsp())
                .subscriberTier(r.getSubscriberTier())
                .additionalAttributes(r.getAdditionalAttributes())
                .lastUpdatedEpochMs(r.getLastUpdatedEpochMs())
                .dataSource(r.getDataSource())
                .build();
    }

    private HandsetRecord toDomain(HandsetEntity e) {
        return HandsetRecord.builder()
                .cgi(e.getCgi())
                .manufacturer(e.getManufacturer())
                .model(e.getModel())
                .technology(e.getTechnology())
                .imei(e.getImei())
                .operatingSystem(e.getOperatingSystem())
                .latitude(e.getLatitude())
                .longitude(e.getLongitude())
                .cellId(e.getCellId())
                .lac(e.getLac())
                .mcc(e.getMcc())
                .mnc(e.getMnc())
                .country(e.getCountry())
                .region(e.getRegion())
                .signalStrength(e.getSignalStrength())
                .networkType(e.getNetworkType())
                .isp(e.getIsp())
                .subscriberTier(e.getSubscriberTier())
                .additionalAttributes(e.getAdditionalAttributes())
                .lastUpdatedEpochMs(e.getLastUpdatedEpochMs())
                .dataSource(e.getDataSource())
                .build();
    }

    private HandsetRecord projectionToDomain(HandsetProjection p) {
        return HandsetRecord.builder()
                .cgi(p.getCgi())
                .manufacturer(p.getManufacturer())
                .model(p.getModel())
                .technology(p.getTechnology())
                .region(p.getRegion())
                .country(p.getCountry())
                .dataSource(p.getDataSource())
                .build();
    }
}
