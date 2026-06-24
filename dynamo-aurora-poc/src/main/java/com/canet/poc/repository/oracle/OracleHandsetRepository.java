package com.canet.poc.repository.oracle;

import com.canet.poc.model.HandsetRecord;
import com.canet.poc.repository.HandsetRepository;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;

/**
 * RDS Oracle implementation of HandsetRepository.
 *
 * Only activated when oracle.enabled=true — this allows the app to run with
 * just DynamoDB + Aurora in local/dev mode and add Oracle only in the sandbox.
 *
 * Key Oracle differences vs Aurora PostgreSQL:
 *   - additionalAttributes stored as JSON string in VARCHAR2(4000); deserialized in Java
 *   - Oracle uses NUMBER(10,7) for lat/lon; mapping is handled by JPA
 *   - Oracle SQL uses ROWNUM instead of LIMIT (handled by Spring Data JPA)
 *   - Schema created by Flyway using Oracle-compatible DDL
 */
@Slf4j
@Repository("oracleHandsetRepository")
@RequiredArgsConstructor
@ConditionalOnProperty(name = "oracle.enabled", havingValue = "true", matchIfMissing = false)
public class OracleHandsetRepository implements HandsetRepository {

    private final OracleHandsetJpaRepository jpa;
    private final ObjectMapper objectMapper;

    @Override
    public Optional<HandsetRecord> findByCgi(String cgi) {
        return jpa.findById(cgi).map(this::toDomain);
    }

    @Override
    public Optional<HandsetRecord> findByCgiWithFields(String cgi, List<String> fields) {
        if (fields != null && !fields.isEmpty() &&
                fields.stream().allMatch(f ->
                        List.of("cgi","manufacturer","model","technology","region","country","dataSource").contains(f))) {
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
        return jpa.findByRegionAndTechnology(region, technology).stream()
                .map(this::toDomain).collect(Collectors.toList());
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
    public String backendName() { return "RDS Oracle SE2"; }

    // ── Mapping ───────────────────────────────────────────────────────────────

    private OracleHandsetEntity toEntity(HandsetRecord r) {
        String attrsJson = null;
        if (r.getAdditionalAttributes() != null && !r.getAdditionalAttributes().isEmpty()) {
            try {
                attrsJson = objectMapper.writeValueAsString(r.getAdditionalAttributes());
            } catch (Exception e) {
                log.warn("Failed to serialise additionalAttributes for CGI={}", r.getCgi());
            }
        }
        return OracleHandsetEntity.builder()
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
                .additionalAttributesJson(attrsJson)
                .lastUpdatedEpochMs(r.getLastUpdatedEpochMs())
                .dataSource(r.getDataSource())
                .build();
    }

    private HandsetRecord toDomain(OracleHandsetEntity e) {
        Map<String, String> attrs = null;
        if (e.getAdditionalAttributesJson() != null && !e.getAdditionalAttributesJson().isBlank()) {
            try {
                attrs = objectMapper.readValue(e.getAdditionalAttributesJson(),
                        new TypeReference<Map<String, String>>() {});
            } catch (Exception ex) {
                log.warn("Failed to deserialise additionalAttributes for CGI={}", e.getCgi());
            }
        }
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
                .additionalAttributes(attrs)
                .lastUpdatedEpochMs(e.getLastUpdatedEpochMs())
                .dataSource(e.getDataSource())
                .build();
    }

    private HandsetRecord projectionToDomain(OracleHandsetProjection p) {
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
