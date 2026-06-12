package com.canet.poc.model;

import lombok.Builder;
import lombok.Data;

import java.util.Map;

/**
 * Response shape for the NEO API — returns only the fields the API contract requires,
 * with any post-retrieval business logic already applied.
 *
 * Having a dedicated response type (separate from HandsetRecord) means:
 * - The database schema can evolve without breaking the API contract.
 * - Post-retrieval transformations are explicit and testable.
 */
@Data
@Builder
public class NeoApiResponse {

    private String cgi;
    private String manufacturer;
    private String model;
    private String technology;

    // Derived field: full location label assembled post-retrieval
    private String locationLabel;   // e.g. "Building-A, London, UK"

    private Double latitude;
    private Double longitude;

    // Derived field: network tier classification applied after DB read
    private String networkTier;     // "PREMIUM" | "STANDARD" | "BASIC"

    private String dataSource;

    /** Any extended attributes the caller requested via ?fields= projection */
    private Map<String, String> extended;

    // ── Benchmark metadata (only populated on /compare endpoint) ─────────────
    private String backendUsed;
    private Long queryTimeMs;
}
