package com.canet.keypoc.repository.postgres;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;

public interface CellJpaRepository extends JpaRepository<CellEntity, CellEntityId> {

    /** ROW_SCAN: all cells for a CGI */
    List<CellEntity> findByCgi(String cgi);

    /** FAMILY_SCAN: equivalent to scanner.fetchColumnFamily() */
    List<CellEntity> findByCgiAndFamily(String cgi, String family);

    /** CELL_LOOKUP: equivalent to scanner.fetchColumn(family, qualifier) */
    List<CellEntity> findByCgiAndFamilyAndQualifier(String cgi, String family, String qualifier);

    /** VERSIONED_CELL_LOOKUP: equivalent to fetchColumn + setTimestampRange */
    List<CellEntity> findByCgiAndFamilyAndQualifierAndTsBetween(
            String cgi, String family, String qualifier, long tsFrom, long tsTo);

    /** MULTI_FAMILY: single query with IN clause — no multi-query needed (Aurora advantage over DynamoDB) */
    @Query("SELECT c FROM CellEntity c WHERE c.cgi = :cgi AND c.family IN :families ORDER BY c.family, c.qualifier, c.ts DESC")
    List<CellEntity> findByCgiAndFamilyIn(@Param("cgi") String cgi, @Param("families") List<String> families);

    /**
     * Latest version only — most recent timestamp per (cgi, family, qualifier).
     * Equivalent to Accumulo's default behaviour: returns highest timestamp cell.
     * In DynamoDB this requires fetching all versions and taking max(ts) in the application.
     */
    @Query("SELECT c FROM CellEntity c WHERE c.cgi = :cgi AND c.family = :family AND c.qualifier = :qualifier " +
           "AND c.ts = (SELECT MAX(c2.ts) FROM CellEntity c2 WHERE c2.cgi = :cgi AND c2.family = :family AND c2.qualifier = :qualifier)")
    List<CellEntity> findLatestByCgiAndFamilyAndQualifier(
            @Param("cgi") String cgi, @Param("family") String family, @Param("qualifier") String qualifier);
}
