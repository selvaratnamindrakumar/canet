-- Aurora PostgreSQL schema for handset_reference
-- Mirrors Accumulo column families as typed columns
-- Option 2 from the POC spec

CREATE TABLE IF NOT EXISTS handset_reference (
    -- Identity (was Accumulo row key)
    cgi                     TEXT        NOT NULL PRIMARY KEY,

    -- Device attributes (was Accumulo CF: "device")
    manufacturer            TEXT,
    model                   TEXT,
    technology              VARCHAR(10),    -- 2G / 3G / 4G / 5G
    imei                    VARCHAR(20),
    operating_system        TEXT,

    -- Location attributes (was Accumulo CF: "location")
    latitude                DOUBLE PRECISION,
    longitude               DOUBLE PRECISION,
    cell_id                 TEXT,
    lac                     VARCHAR(10),    -- Location Area Code
    mcc                     VARCHAR(5),     -- Mobile Country Code
    mnc                     VARCHAR(5),     -- Mobile Network Code
    country                 TEXT,
    region                  TEXT,

    -- Enrichment / IS fields (was Accumulo CF: "enrichment")
    signal_strength         VARCHAR(10),
    network_type            VARCHAR(20),
    isp                     TEXT,
    subscriber_tier         VARCHAR(20),

    -- Extension bag: covers arbitrary Accumulo column qualifiers without schema change
    -- PostgreSQL JSONB: queryable, indexable, schema-free
    additional_attributes   JSONB,

    -- Audit
    last_updated_epoch_ms   BIGINT,
    data_source             VARCHAR(20)     -- BB | IS | ENRICHMENT
);

-- Indexes for secondary NEO API access patterns
-- (Each index corresponds to a DynamoDB GSI that would be needed)
CREATE INDEX IF NOT EXISTS idx_technology    ON handset_reference (technology);
CREATE INDEX IF NOT EXISTS idx_region        ON handset_reference (region);
CREATE INDEX IF NOT EXISTS idx_manufacturer  ON handset_reference (manufacturer);

-- Composite index for the region+technology query pattern
CREATE INDEX IF NOT EXISTS idx_region_tech   ON handset_reference (region, technology);

-- GIN index on JSONB for extension attribute queries
-- e.g. WHERE additional_attributes @> '{"deviceType": "IoT"}'
CREATE INDEX IF NOT EXISTS idx_additional_attrs ON handset_reference USING GIN (additional_attributes);

COMMENT ON TABLE handset_reference IS
    'Handset reference data migrated from Apache Accumulo. '
    'CGI is the primary key (was Accumulo row key). '
    'JSONB column covers arbitrary column qualifiers.';
