package com.canet.poc.repository.oracle;

import jakarta.persistence.*;
import lombok.*;

/**
 * RDS Oracle entity for the handset_reference table.
 *
 * Oracle differences from PostgreSQL:
 *   - No JSONB → additional_attributes stored as VARCHAR2(4000) JSON string
 *   - No TEXT → VARCHAR2(255) or CLOB
 *   - No DOUBLE PRECISION → NUMBER(10,7)
 *   - No BIGINT → NUMBER(19,0)
 *   - No BOOLEAN → NUMBER(1,0) (not used here)
 *   - Column definitions use Oracle types explicitly
 *
 * The application logic (NeoApiService, comparison) is identical — only this
 * entity and the Flyway SQL differ from the Aurora PostgreSQL backend.
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
public class OracleHandsetEntity {

    @Id
    @Column(name = "cgi", nullable = false, length = 60)
    private String cgi;

    @Column(name = "manufacturer",   length = 100)
    private String manufacturer;

    @Column(name = "model",          length = 100)
    private String model;

    @Column(name = "technology",     length = 10)
    private String technology;

    @Column(name = "imei",           length = 20)
    private String imei;

    @Column(name = "operating_system", length = 100)
    private String operatingSystem;

    @Column(name = "latitude")
    private Double latitude;

    @Column(name = "longitude")
    private Double longitude;

    @Column(name = "cell_id",   length = 20)
    private String cellId;

    @Column(name = "lac",       length = 10)
    private String lac;

    @Column(name = "mcc",       length = 5)
    private String mcc;

    @Column(name = "mnc",       length = 5)
    private String mnc;

    @Column(name = "country",   length = 100)
    private String country;

    @Column(name = "region",    length = 100)
    private String region;

    @Column(name = "signal_strength", length = 10)
    private String signalStrength;

    @Column(name = "network_type",    length = 20)
    private String networkType;

    @Column(name = "isp",             length = 100)
    private String isp;

    @Column(name = "subscriber_tier", length = 20)
    private String subscriberTier;

    /**
     * Oracle stores the extension JSON as a VARCHAR2(4000) with an IS JSON constraint.
     * This is equivalent to PostgreSQL's JSONB but without server-side JSON indexing.
     * For production Oracle, use a JSON column (Oracle 21c+) or CLOB with JSON constraint.
     * Stored/retrieved as a raw JSON string; deserialized in the repository layer.
     */
    @Column(name = "additional_attributes", length = 4000)
    private String additionalAttributesJson;

    @Column(name = "last_updated_epoch_ms")
    private Long lastUpdatedEpochMs;

    @Column(name = "data_source", length = 20)
    private String dataSource;
}
