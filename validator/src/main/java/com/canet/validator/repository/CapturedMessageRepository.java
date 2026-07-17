package com.canet.validator.repository;

import com.canet.validator.entity.CapturedMessage;
import org.springframework.data.repository.CrudRepository;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.util.Optional;

/**
 * Spring Data JPA — no stored procedures.
 *
 * existsByHashValueAndReceivedAtAfter →
 *   SELECT COUNT(*) FROM captured_message
 *   WHERE hash_value = ? AND received_at > ?
 *
 * The cutoff (received_at >) bounds the search to a configurable time window
 * (dedup.window.seconds) so the query never degrades into a full table scan.
 */
@Repository
public interface CapturedMessageRepository extends CrudRepository<CapturedMessage, Long> {

    boolean existsByHashValueAndReceivedAtAfter(String hashValue, Instant cutoff);

    Optional<CapturedMessage> findFirstByHashValueAndReceivedAtAfter(String hashValue, Instant cutoff);
}
