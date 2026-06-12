package com.canet.poc.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.Map;

/**
 * Canonical domain object for a BB/IS handset reference record.
 *
 * This is backend-agnostic — both DynamoDB and Aurora PostgreSQL repositories
 * return this same type, so the NEO API layer and business logic are identical
 * regardless of which store is queried.
 *
 * Accumulo mapping:
 *   row key = CGI
 *   column families: "device", "location", "enrichment"
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class HandsetRecord {

    // ── Identity ─────────────────────────────────────────────────────────────
    /** Cell Global Identity — primary lookup key (was Accumulo row key). */
    private String cgi;

    // ── Device Attributes (Accumulo CF: "device") ────────────────────────────
    private String manufacturer;
    private String model;
    private String technology;       // 2G / 3G / 4G / 5G
    private String imei;
    private String operatingSystem;

    // ── Location Attributes (Accumulo CF: "location") ────────────────────────
    private Double latitude;
    private Double longitude;
    private String cellId;
    private String lac;              // Location Area Code
    private String mcc;              // Mobile Country Code
    private String mnc;              // Mobile Network Code
    private String country;
    private String region;

    // ── Enrichment / IS fields (Accumulo CF: "enrichment") ───────────────────
    private String signalStrength;
    private String networkType;
    private String isp;
    private String subscriberTier;

    /**
     * Flexible extension bag for fields not yet promoted to first-class columns.
     * In DynamoDB: stored as a nested JSON attribute.
     * In Aurora: stored as a JSONB column.
     */
    private Map<String, String> additionalAttributes;

    // ── Audit ─────────────────────────────────────────────────────────────────
    private Long lastUpdatedEpochMs;
    private String dataSource;       // "BB" | "IS" | "ENRICHMENT"
}
