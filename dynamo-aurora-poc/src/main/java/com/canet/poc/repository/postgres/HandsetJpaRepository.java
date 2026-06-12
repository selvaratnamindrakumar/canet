package com.canet.poc.repository.postgres;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface HandsetJpaRepository extends JpaRepository<HandsetEntity, String> {

    List<HandsetEntity> findByTechnology(String technology);

    List<HandsetEntity> findByRegion(String region);

    List<HandsetEntity> findByManufacturer(String manufacturer);

    List<HandsetEntity> findByRegionAndTechnology(String region, String technology);

    /**
     * Projection query — SELECT only the fields the caller needs.
     * Aurora can return partial rows efficiently; DynamoDB needs ProjectionExpression
     * and still reads the full item from storage (charged on full item size).
     */
    @Query("SELECT new com.canet.poc.repository.postgres.HandsetProjection(" +
           "h.cgi, h.manufacturer, h.model, h.technology, h.region, h.country, h.dataSource) " +
           "FROM HandsetEntity h WHERE h.cgi = :cgi")
    Optional<HandsetProjection> findSummaryByCgi(@Param("cgi") String cgi);
}
