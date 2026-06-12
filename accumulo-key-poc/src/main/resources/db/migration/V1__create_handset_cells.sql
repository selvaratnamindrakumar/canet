-- Aurora PostgreSQL schema: true Accumulo key model
-- (cgi, family, qualifier, ts) → value
--
-- This schema is structurally identical to Accumulo's (RowID, CF, CQ, Timestamp) key space.
-- Each Accumulo scanner pattern maps to a single WHERE clause with no application filtering.

CREATE TABLE IF NOT EXISTS handset_cells (

    -- Accumulo RowID — primary dimension
    cgi         VARCHAR(60)     NOT NULL,

    -- Accumulo ColumnFamily
    family      VARCHAR(50)     NOT NULL,

    -- Accumulo ColumnQualifier
    qualifier   VARCHAR(50)     NOT NULL,

    -- Accumulo Timestamp (epoch ms) — supports versioning
    ts          BIGINT          NOT NULL,

    -- Accumulo ColumnVisibility (stored; enforcement is application responsibility in this POC)
    visibility  VARCHAR(100),

    -- The cell value
    value       TEXT,

    -- Composite PK mirrors Accumulo's unique key guarantee
    CONSTRAINT pk_handset_cells PRIMARY KEY (cgi, family, qualifier, ts)
);

-- ── Indexes matching Accumulo locality group / column filter patterns ──────────

-- ROW_SCAN: scanner.setRange(cgi) — already covered by PK
-- FAMILY_SCAN: scanner.fetchColumnFamily(family)
CREATE INDEX IF NOT EXISTS idx_cells_family
    ON handset_cells (cgi, family);

-- CELL_LOOKUP: scanner.fetchColumn(family, qualifier)
CREATE INDEX IF NOT EXISTS idx_cells_qualifier
    ON handset_cells (cgi, family, qualifier);

-- VERSIONED_CELL_LOOKUP: setTimestampRange(from, to) — ts is numeric, BETWEEN is correct
CREATE INDEX IF NOT EXISTS idx_cells_ts
    ON handset_cells (cgi, family, qualifier, ts DESC);

-- ── Cross-row / reporting indexes (patterns 6-8: HARD in DynamoDB) ────────────
-- These support queries that DynamoDB cannot serve without GSIs or full-table scans.

-- "Find all CGIs with MODEL=iPhone16"
CREATE INDEX IF NOT EXISTS idx_cells_value_lookup
    ON handset_cells (family, qualifier, value);

-- "Find all records updated in last 24h"
CREATE INDEX IF NOT EXISTS idx_cells_recent
    ON handset_cells (ts DESC);

COMMENT ON TABLE handset_cells IS
    'Accumulo key-value model: (cgi, family, qualifier, ts) → value. '
    'Structurally identical to Accumulo key space — every scanner pattern maps to a WHERE clause.';

COMMENT ON COLUMN handset_cells.cgi IS 'Accumulo RowID — Cell Global Identity';
COMMENT ON COLUMN handset_cells.family IS 'Accumulo ColumnFamily (DEVICE, LOCATION, ENRICHMENT)';
COMMENT ON COLUMN handset_cells.qualifier IS 'Accumulo ColumnQualifier (MODEL, REGION, ISP, ...)';
COMMENT ON COLUMN handset_cells.ts IS 'Accumulo Timestamp epoch ms — supports versioned reads';
COMMENT ON COLUMN handset_cells.visibility IS 'Accumulo visibility label — application-enforced in this POC';
