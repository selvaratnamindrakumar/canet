package com.canet.poc.repository.oracle;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface OracleHandsetJpaRepository extends JpaRepository<OracleHandsetEntity, String> {

    List<OracleHandsetEntity> findByTechnology(String technology);

    List<OracleHandsetEntity> findByRegion(String region);

    List<OracleHandsetEntity> findByManufacturer(String manufacturer);

    List<OracleHandsetEntity> findByRegionAndTechnology(String region, String technology);

    /**
     * Oracle projection — SELECT only needed columns.
     * Oracle's cost-based optimiser can satisfy this entirely from an index
     * (index-only scan) if the index covers cgi + the projected columns.
     */
    @Query("SELECT new com.canet.poc.repository.oracle.OracleHandsetProjection(" +
           "h.cgi, h.manufacturer, h.model, h.technology, h.region, h.country, h.dataSource) " +
           "FROM OracleHandsetEntity h WHERE h.cgi = :cgi")
    Optional<OracleHandsetProjection> findSummaryByCgi(@Param("cgi") String cgi);
}
