package com.canet.poc.repository.postgres;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.util.Map;

/**
 * Aurora PostgreSQL entity — Option 2 from the POC spec.
 *
 * Each Accumulo column family becomes a group of typed columns.
 * The additionalAttributes Map uses PostgreSQL JSONB for the extension bag,
 * giving SQL query access into the JSON keys if needed in future.
 *
 * Table: handset_reference
 * PK:    cgi (text, not null)
 */
@Entity
@Table(name = "handset_reference",
        indexes = {
                @Index(name = "idx_technology", columnList = "technology"),
                @Index(name = "idx_region",     columnList = "region"),
                @Index(name = "idx_manufacturer",columnList = "manufacturer")
        })
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class HandsetEntity {

    @Id
    @Column(name = "cgi", nullable = false)
    private String cgi;

    // ── Device attributes ─────────────────────────────────────────────────────
    @Column(name = "manufacturer")
    private String manufacturer;

    @Column(name = "model")
    private String model;

    @Column(name = "technology", length = 10)
    private String technology;

    @Column(name = "imei", length = 20)
    private String imei;

    @Column(name = "operating_system")
    private String operatingSystem;

    // ── Location attributes ───────────────────────────────────────────────────
    @Column(name = "latitude")
    private Double latitude;

    @Column(name = "longitude")
    private Double longitude;

    @Column(name = "cell_id")
    private String cellId;

    @Column(name = "lac", length = 10)
    private String lac;

    @Column(name = "mcc", length = 5)
    private String mcc;

    @Column(name = "mnc", length = 5)
    private String mnc;

    @Column(name = "country")
    private String country;

    @Column(name = "region")
    private String region;

    // ── Enrichment / IS fields ────────────────────────────────────────────────
    @Column(name = "signal_strength", length = 10)
    private String signalStrength;

    @Column(name = "network_type", length = 20)
    private String networkType;

    @Column(name = "isp")
    private String isp;

    @Column(name = "subscriber_tier", length = 20)
    private String subscriberTier;

    /**
     * JSONB column — stores extension fields without schema changes.
     * Queryable in PostgreSQL: additional_attributes->>'key'
     */
    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "additional_attributes", columnDefinition = "jsonb")
    private Map<String, String> additionalAttributes;

    @Column(name = "last_updated_epoch_ms")
    private Long lastUpdatedEpochMs;

    @Column(name = "data_source", length = 20)
    private String dataSource;
}
