package com.canet.validator.repository;

import com.canet.validator.entity.CapturedMessage;
import org.springframework.data.repository.CrudRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

/**
 * Spring Data JPA — no stored procedures.
 *
 * findFirstByFileHash →
 *   SELECT * FROM captured_message WHERE file_hash = ? LIMIT 1
 *
 * Duplicates are allowed; findFirst returns the most recently inserted
 * row (by auto-increment id) when more than one row shares the same hash.
 */
@Repository
public interface CapturedMessageRepository extends CrudRepository<CapturedMessage, Long> {

    Optional<CapturedMessage> findFirstByFileHashOrderByIdDesc(String fileHash);
}
