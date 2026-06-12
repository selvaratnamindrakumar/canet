package com.canet.keypoc.repository.postgres;

import com.canet.keypoc.model.AccumuloCell;
import com.canet.keypoc.scanner.AccumuloScannerOp;
import com.canet.keypoc.scanner.CellStoreRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.stream.Collectors;

/**
 * Aurora PostgreSQL implementation of the Accumulo cell store.
 *
 * Schema: handset_cells (cgi, family, qualifier, ts, visibility, value)
 *   Composite PK: (cgi, family, qualifier, ts)  ← mirrors Accumulo's 4-part key exactly
 *
 * Every Accumulo scanner pattern maps to a single SQL WHERE clause.
 * No application-side filtering. Filtering is ALWAYS done by PostgreSQL.
 *
 * Multi-family queries (Accumulo: scanner.fetchColumnFamily x2) use IN (:families)
 * — a single SQL query vs DynamoDB's requirement for N separate Query calls.
 *
 * "Latest version" queries use a subquery MAX(ts) — equivalent to Accumulo's
 * default behaviour of returning the highest-timestamp cell per key.
 */
@Slf4j
@Repository("auroraCellStore")
@RequiredArgsConstructor
public class AuroraCellStoreRepository implements CellStoreRepository {

    private final CellJpaRepository jpa;

    @Override
    public List<AccumuloCell> fetchRow(String rowId) {
        log.debug("[Aurora] ROW_SCAN: SELECT * WHERE cgi='{}'", rowId);
        return toDomain(jpa.findByCgi(rowId));
    }

    @Override
    public List<AccumuloCell> fetchRowByFamily(String rowId, String family) {
        log.debug("[Aurora] FAMILY_SCAN: SELECT * WHERE cgi='{}' AND family='{}'", rowId, family);
        return toDomain(jpa.findByCgiAndFamily(rowId, family));
    }

    @Override
    public List<AccumuloCell> fetchCell(String rowId, String family, String qualifier) {
        log.debug("[Aurora] CELL_LOOKUP: SELECT * WHERE cgi='{}' AND family='{}' AND qualifier='{}'",
                rowId, family, qualifier);
        return toDomain(jpa.findByCgiAndFamilyAndQualifier(rowId, family, qualifier));
    }

    @Override
    public List<AccumuloCell> fetchCellVersions(String rowId, String family, String qualifier,
                                                 long timestampFrom, long timestampTo) {
        log.debug("[Aurora] VERSIONED: SELECT * WHERE cgi='{}' AND family='{}' AND qualifier='{}' AND ts BETWEEN {} AND {}",
                rowId, family, qualifier, timestampFrom, timestampTo);
        return toDomain(jpa.findByCgiAndFamilyAndQualifierAndTsBetween(
                rowId, family, qualifier, timestampFrom, timestampTo));
    }

    /**
     * MULTI_FAMILY: single SQL query with IN clause.
     * Aurora advantage: WHERE cgi=? AND family IN (?,?) — one round-trip to the database.
     * DynamoDB requires one Query per family value.
     */
    @Override
    public List<AccumuloCell> fetchRowByFamilies(String rowId, List<String> families) {
        log.debug("[Aurora] MULTI_FAMILY: SELECT * WHERE cgi='{}' AND family IN {}", rowId, families);
        return toDomain(jpa.findByCgiAndFamilyIn(rowId, families));
    }

    @Override
    public List<AccumuloCell> execute(AccumuloScannerOp op) {
        return switch (op.getType()) {
            case ROW_SCAN          -> fetchRow(op.getRowId());
            case FAMILY_SCAN       -> fetchRowByFamily(op.getRowId(), op.getColumnFamily());
            case CELL_LOOKUP       -> fetchCell(op.getRowId(), op.getColumnFamily(), op.getColumnQualifier());
            case VERSIONED_CELL_LOOKUP -> fetchCellVersions(op.getRowId(), op.getColumnFamily(),
                    op.getColumnQualifier(), op.getTimestampFrom(), op.getTimestampTo());
        };
    }

    @Override
    public void save(AccumuloCell cell) {
        jpa.save(toEntity(cell));
    }

    @Override
    public void saveAll(List<AccumuloCell> cells) {
        jpa.saveAll(cells.stream().map(this::toEntity).collect(Collectors.toList()));
    }

    @Override
    public void deleteAll() {
        jpa.deleteAll();
    }

    @Override
    public String backendName() { return "Aurora PostgreSQL (cgi, family, qualifier, ts, value)"; }

    // ── Mapping ───────────────────────────────────────────────────────────────

    private List<AccumuloCell> toDomain(List<CellEntity> entities) {
        return entities.stream().map(e ->
                AccumuloCell.of(e.getCgi(), e.getFamily(), e.getQualifier(),
                        e.getVisibility(), e.getTs(), e.getValue()))
                .collect(Collectors.toList());
    }

    private CellEntity toEntity(AccumuloCell c) {
        return CellEntity.builder()
                .cgi(c.getKey().getRowId())
                .family(c.getKey().getColumnFamily())
                .qualifier(c.getKey().getColumnQualifier())
                .ts(c.getKey().getTimestamp())
                .visibility(c.getKey().getColumnVisibility())
                .value(c.getValue())
                .build();
    }
}
