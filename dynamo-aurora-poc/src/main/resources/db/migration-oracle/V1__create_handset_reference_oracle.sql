-- RDS Oracle SE2 schema for handset_reference
-- Oracle-compatible DDL — no JSONB, no TEXT, no BIGINT, no IF NOT EXISTS
-- Run via Flyway with spring.flyway.locations=classpath:db/migration-oracle

CREATE TABLE handset_reference (
    -- Identity (Accumulo row key → Oracle primary key)
    cgi                     VARCHAR2(60)        NOT NULL,

    -- Device attributes (Accumulo CF: "device")
    manufacturer            VARCHAR2(100),
    model                   VARCHAR2(100),
    technology              VARCHAR2(10),        -- 2G / 3G / 4G / 5G
    imei                    VARCHAR2(20),
    operating_system        VARCHAR2(100),

    -- Location attributes (Accumulo CF: "location")
    latitude                NUMBER(10, 7),
    longitude               NUMBER(10, 7),
    cell_id                 VARCHAR2(20),
    lac                     VARCHAR2(10),
    mcc                     VARCHAR2(5),
    mnc                     VARCHAR2(5),
    country                 VARCHAR2(100),
    region                  VARCHAR2(100),

    -- Enrichment / IS fields (Accumulo CF: "enrichment")
    signal_strength         VARCHAR2(10),
    network_type            VARCHAR2(20),
    isp                     VARCHAR2(100),
    subscriber_tier         VARCHAR2(20),

    -- Extension bag: JSON string in VARCHAR2(4000)
    -- For Oracle 21c+ use: additional_attributes JSON
    -- For Oracle 19c (RDS default): VARCHAR2(4000) CONSTRAINT chk_json CHECK (additional_attributes IS JSON)
    additional_attributes   VARCHAR2(4000)
        CONSTRAINT chk_attrs_json CHECK (additional_attributes IS JSON),

    -- Audit
    last_updated_epoch_ms   NUMBER(19, 0),
    data_source             VARCHAR2(20),

    CONSTRAINT pk_handset_reference PRIMARY KEY (cgi)
);

-- Secondary indexes for NEO API access patterns
-- Each index here corresponds to a DynamoDB GSI that would be needed
CREATE INDEX idx_technology     ON handset_reference (technology);
CREATE INDEX idx_region         ON handset_reference (region);
CREATE INDEX idx_manufacturer   ON handset_reference (manufacturer);
CREATE INDEX idx_region_tech    ON handset_reference (region, technology);

-- Oracle JSON search index on additional_attributes (requires Oracle Text / JSON Search Index)
-- Uncomment if Oracle Text is enabled in the RDS parameter group:
-- CREATE SEARCH INDEX idx_attrs_json ON handset_reference (additional_attributes) FOR JSON;

-- Table and column comments (Oracle Data Dictionary)
COMMENT ON TABLE handset_reference IS
    'Handset reference data migrated from Apache Accumulo. CGI is PK (Accumulo row key).';
COMMENT ON COLUMN handset_reference.cgi IS 'Cell Global Identity — Accumulo row key';
COMMENT ON COLUMN handset_reference.technology IS '2G / 3G / 4G / 5G';
COMMENT ON COLUMN handset_reference.additional_attributes IS
    'Extension bag as JSON string. Use JSON_VALUE(additional_attributes, ''$.key'') to query.';
