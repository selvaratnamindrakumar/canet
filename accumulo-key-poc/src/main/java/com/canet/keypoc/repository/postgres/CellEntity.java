package com.canet.keypoc.repository.postgres;

import jakarta.persistence.*;
import lombok.*;

/**
 * Aurora PostgreSQL entity for a single Accumulo cell.
 *
 * Table: handset_cells
 *   (cgi, family, qualifier, ts)  →  value
 *
 * This schema is structurally identical to Accumulo's key space.
 * Every Accumulo scanner query translates directly to a SQL WHERE clause
 * with no application-side filtering.
 *
 * ROW_SCAN:    WHERE cgi = ?
 * FAMILY_SCAN: WHERE cgi = ? AND family = ?
 * CELL_LOOKUP: WHERE cgi = ? AND family = ? AND qualifier = ?
 * VERSIONED:   WHERE cgi = ? AND family = ? AND qualifier = ? AND ts BETWEEN ? AND ?
 */
@Entity
@Table(name = "handset_cells",
        uniqueConstraints = @UniqueConstraint(
                name = "uq_cell_key",
                columnNames = {"cgi", "family", "qualifier", "ts"}),
        indexes = {
                @Index(name = "idx_cells_cgi",        columnList = "cgi"),
                @Index(name = "idx_cells_family",     columnList = "cgi, family"),
                @Index(name = "idx_cells_qualifier",  columnList = "cgi, family, qualifier"),
                @Index(name = "idx_cells_ts",         columnList = "cgi, family, qualifier, ts")
        })
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@IdClass(CellEntityId.class)
public class CellEntity {

    /** Accumulo RowID → partition dimension */
    @Id
    @Column(name = "cgi", nullable = false, length = 60)
    private String cgi;

    /** Accumulo ColumnFamily */
    @Id
    @Column(name = "family", nullable = false, length = 50)
    private String family;

    /** Accumulo ColumnQualifier */
    @Id
    @Column(name = "qualifier", nullable = false, length = 50)
    private String qualifier;

    /** Accumulo Timestamp (epoch ms) — supports versioning */
    @Id
    @Column(name = "ts", nullable = false)
    private Long ts;

    /** Accumulo ColumnVisibility — stored but not enforced by the DB in this POC */
    @Column(name = "visibility", length = 100)
    private String visibility;

    /** The cell value */
    @Column(name = "value", columnDefinition = "text")
    private String value;
}
