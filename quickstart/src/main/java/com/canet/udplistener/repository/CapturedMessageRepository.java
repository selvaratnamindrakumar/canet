package com.canet.udplistener.repository;

import com.canet.udplistener.entity.CapturedMessage;
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
 * The cutoff instant is computed as Instant.now().minusSeconds(dedup.window.seconds)
 * so duplicate detection is limited to a configurable time window rather than
 * scanning the entire table indefinitely.
 *
 * save() →
 *   INSERT INTO captured_message (...) VALUES (...)
 */
@Repository
public interface CapturedMessageRepository extends CrudRepository<CapturedMessage, Long> {

    boolean existsByHashValueAndReceivedAtAfter(String hashValue, Instant cutoff);

    Optional<CapturedMessage> findFirstByHashValueAndReceivedAtAfter(String hashValue, Instant cutoff);
}
