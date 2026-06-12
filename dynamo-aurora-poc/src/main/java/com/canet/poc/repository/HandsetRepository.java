package com.canet.poc.repository;

import com.canet.poc.model.HandsetRecord;

import java.util.List;
import java.util.Optional;

/**
 * Backend-agnostic data access interface.
 * Both DynamoDB and Aurora PostgreSQL satisfy this contract.
 * The NEO API service layer depends only on this interface.
 */
public interface HandsetRepository {

    /** Lookup by CGI — the primary NEO API access pattern. */
    Optional<HandsetRecord> findByCgi(String cgi);

    /** Projection query: return only the requested fields (reduces data transfer). */
    Optional<HandsetRecord> findByCgiWithFields(String cgi, List<String> fields);

    /** Lookup by technology (2G/3G/4G/5G) — secondary access pattern. */
    List<HandsetRecord> findByTechnology(String technology);

    /** Lookup by region — ad-hoc / reporting access pattern. */
    List<HandsetRecord> findByRegion(String region);

    /** Lookup by manufacturer — future extensibility scenario. */
    List<HandsetRecord> findByManufacturer(String manufacturer);

    /** Multi-field filter: region + technology. */
    List<HandsetRecord> findByRegionAndTechnology(String region, String technology);

    void save(HandsetRecord record);

    void saveAll(List<HandsetRecord> records);

    List<HandsetRecord> findAll();

    void deleteAll();

    String backendName();
}
