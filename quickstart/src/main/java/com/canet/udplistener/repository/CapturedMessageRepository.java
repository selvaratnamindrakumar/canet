package com.canet.udplistener.repository;

import com.canet.udplistener.entity.CapturedMessage;
import org.springframework.data.repository.CrudRepository;
import org.springframework.stereotype.Repository;

/**
 * Generator write path.  Spring Data JPA — no stored procedures.
 *
 * existsByHashValue →
 *   SELECT COUNT(*) FROM captured_message WHERE hash_value = ?
 *
 * save() →
 *   INSERT INTO captured_message (...) VALUES (...)
 *
 * The composite check (hash + dstIp + dstPort) is the primary dedup guard;
 * existsByHashValue is kept as a fast short-circuit before the full check.
 */
@Repository
public interface CapturedMessageRepository extends CrudRepository<CapturedMessage, Long> {

    boolean existsByHashValue(String hashValue);

    boolean existsByHashValueAndDstIpAndDstPort(String hashValue, String dstIp, Integer dstPort);
}
