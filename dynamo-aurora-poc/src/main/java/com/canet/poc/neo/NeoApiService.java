package com.canet.poc.neo;

import com.canet.poc.model.HandsetRecord;
import com.canet.poc.model.NeoApiResponse;
import com.canet.poc.repository.HandsetRepository;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Optional;

/**
 * NEO API service — backend-agnostic.
 * Post-retrieval business logic runs identically regardless of which backend is queried.
 * Supports DynamoDB, Aurora PostgreSQL, and RDS Oracle via the same interface.
 */
@Slf4j
@Service
public class NeoApiService {

    private final HandsetRepository dynamoRepo;
    private final HandsetRepository auroraRepo;
    private final Optional<HandsetRepository> oracleRepo;

    public NeoApiService(
            @Qualifier("dynamoHandsetRepository")  HandsetRepository dynamoRepo,
            @Qualifier("auroraHandsetRepository")  HandsetRepository auroraRepo,
            @Qualifier("oracleHandsetRepository")  Optional<HandsetRepository> oracleRepo) {
        this.dynamoRepo  = dynamoRepo;
        this.auroraRepo  = auroraRepo;
        this.oracleRepo  = oracleRepo;
    }

    public Optional<NeoApiResponse> lookupByCgi(String cgi, String backend) {
        HandsetRepository repo = resolveRepo(backend);
        long t0 = System.currentTimeMillis();
        Optional<HandsetRecord> record = repo.findByCgi(cgi);
        long elapsed = System.currentTimeMillis() - t0;
        return record.map(r -> toNeoResponse(r, repo.backendName(), elapsed));
    }

    public Optional<NeoApiResponse> lookupByCgiProjected(String cgi, List<String> fields, String backend) {
        HandsetRepository repo = resolveRepo(backend);
        long t0 = System.currentTimeMillis();
        Optional<HandsetRecord> record = repo.findByCgiWithFields(cgi, fields);
        long elapsed = System.currentTimeMillis() - t0;
        return record.map(r -> toNeoResponse(r, repo.backendName(), elapsed));
    }

    public List<HandsetRecord> findByTechnology(String technology, String backend) {
        return resolveRepo(backend).findByTechnology(technology);
    }

    public List<HandsetRecord> findByRegion(String region, String backend) {
        return resolveRepo(backend).findByRegion(region);
    }

    public List<HandsetRecord> findByRegionAndTechnology(String region, String technology, String backend) {
        return resolveRepo(backend).findByRegionAndTechnology(region, technology);
    }

    public boolean isOracleEnabled() {
        return oracleRepo.isPresent();
    }

    // ── Post-retrieval business logic (backend-agnostic) ──────────────────────

    private NeoApiResponse toNeoResponse(HandsetRecord r, String backendName, long queryMs) {
        return NeoApiResponse.builder()
                .cgi(r.getCgi())
                .manufacturer(r.getManufacturer())
                .model(r.getModel())
                .technology(r.getTechnology())
                .latitude(r.getLatitude())
                .longitude(r.getLongitude())
                .locationLabel(buildLocationLabel(r))
                .networkTier(classifyNetworkTier(r))
                .dataSource(r.getDataSource())
                .extended(r.getAdditionalAttributes())
                .backendUsed(backendName)
                .queryTimeMs(queryMs)
                .build();
    }

    private String buildLocationLabel(HandsetRecord r) {
        StringBuilder sb = new StringBuilder();
        if (r.getRegion() != null)  sb.append(r.getRegion()).append(", ");
        if (r.getCountry() != null) sb.append(r.getCountry());
        if (r.getCellId() != null)  sb.append(" (Cell ").append(r.getCellId()).append(")");
        return sb.toString().isBlank() ? null : sb.toString().trim();
    }

    private String classifyNetworkTier(HandsetRecord r) {
        if ("5G".equalsIgnoreCase(r.getTechnology()) || "PREMIUM".equalsIgnoreCase(r.getSubscriberTier()))
            return "PREMIUM";
        if ("4G".equalsIgnoreCase(r.getTechnology()) || "STANDARD".equalsIgnoreCase(r.getSubscriberTier()))
            return "STANDARD";
        return "BASIC";
    }

    private HandsetRepository resolveRepo(String backend) {
        return switch (backend == null ? "dynamo" : backend.toLowerCase()) {
            case "aurora", "postgres"   -> auroraRepo;
            case "oracle", "rds-oracle" -> oracleRepo.orElseThrow(() ->
                    new IllegalStateException("Oracle backend not enabled — set oracle.enabled=true"));
            default                     -> dynamoRepo;
        };
    }
}
