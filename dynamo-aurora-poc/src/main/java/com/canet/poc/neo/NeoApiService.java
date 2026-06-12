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
 * NEO API service — encapsulates:
 *   1. The database read (via the HandsetRepository abstraction)
 *   2. Post-retrieval business logic currently applied after reading from Accumulo
 *   3. Response shaping (return only the fields the API contract requires)
 *
 * This layer is identical regardless of DynamoDB or Aurora being used underneath,
 * demonstrating that the migration is transparent to the NEO API caller.
 */
@Slf4j
@Service
public class NeoApiService {

    private final HandsetRepository dynamoRepo;
    private final HandsetRepository auroraRepo;

    public NeoApiService(
            @Qualifier("dynamoHandsetRepository")  HandsetRepository dynamoRepo,
            @Qualifier("auroraHandsetRepository")  HandsetRepository auroraRepo) {
        this.dynamoRepo = dynamoRepo;
        this.auroraRepo = auroraRepo;
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

    // ── Post-retrieval business logic ─────────────────────────────────────────
    // This is exactly the logic currently applied after reading from Accumulo.
    // It lives in the service layer and is backend-agnostic.

    private NeoApiResponse toNeoResponse(HandsetRecord r, String backendName, long queryMs) {
        return NeoApiResponse.builder()
                .cgi(r.getCgi())
                .manufacturer(r.getManufacturer())
                .model(r.getModel())
                .technology(r.getTechnology())
                .latitude(r.getLatitude())
                .longitude(r.getLongitude())
                .locationLabel(buildLocationLabel(r))      // post-retrieval derivation
                .networkTier(classifyNetworkTier(r))       // post-retrieval business logic
                .dataSource(r.getDataSource())
                .extended(r.getAdditionalAttributes())
                .backendUsed(backendName)
                .queryTimeMs(queryMs)
                .build();
    }

    /**
     * Post-retrieval: compose human-readable location from raw DB fields.
     * Currently done after reading from Accumulo; unchanged after migration.
     */
    private String buildLocationLabel(HandsetRecord r) {
        StringBuilder sb = new StringBuilder();
        if (r.getRegion() != null)  sb.append(r.getRegion()).append(", ");
        if (r.getCountry() != null) sb.append(r.getCountry());
        if (r.getCellId() != null)  sb.append(" (Cell ").append(r.getCellId()).append(")");
        return sb.toString().isBlank() ? null : sb.toString().trim();
    }

    /**
     * Post-retrieval: classify subscriber network tier from signal + subscriber data.
     * Business logic that currently runs in the application after the Accumulo read.
     */
    private String classifyNetworkTier(HandsetRecord r) {
        if ("5G".equalsIgnoreCase(r.getTechnology()) || "PREMIUM".equalsIgnoreCase(r.getSubscriberTier())) {
            return "PREMIUM";
        }
        if ("4G".equalsIgnoreCase(r.getTechnology()) || "STANDARD".equalsIgnoreCase(r.getSubscriberTier())) {
            return "STANDARD";
        }
        return "BASIC";
    }

    private HandsetRepository resolveRepo(String backend) {
        if ("aurora".equalsIgnoreCase(backend) || "postgres".equalsIgnoreCase(backend)) {
            return auroraRepo;
        }
        return dynamoRepo; // default
    }
}
