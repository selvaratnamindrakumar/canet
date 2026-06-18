package com.canet.udplistener.repository;

import com.canet.udplistener.entity.CapturedMessage;
import org.springframework.data.repository.CrudRepository;
import org.springframework.stereotype.Repository;

/**
 * Spring Data JPA repository — no stored procedures.
 * existsByHashValue generates: SELECT COUNT(*) FROM captured_message WHERE hash_value = ?
 * save() generates:            INSERT INTO captured_message (...) VALUES (...)
 */
@Repository
public interface CapturedMessageRepository extends CrudRepository<CapturedMessage, Long> {

    boolean existsByHashValue(String hashValue);
}
