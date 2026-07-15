package com.canet.udplistener.repository;

import com.canet.udplistener.entity.ValidatorRecord;
import org.springframework.data.repository.CrudRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

/**
 * Validator read/write path — separate from the generator write path so
 * Diosma's EXISTS queries do not contend with the generator's INSERTs.
 *
 * existsByHashValueAndDstIpAndDstPort →
 *   SELECT COUNT(*) FROM validator_record
 *   WHERE hash_value = ? AND dst_ip = ? AND dst_port = ?
 *
 * findFirstByHashValue →
 *   SELECT * FROM validator_record WHERE hash_value = ? LIMIT 1
 */
@Repository
public interface ValidatorRecordRepository extends CrudRepository<ValidatorRecord, Long> {

    boolean existsByHashValueAndDstIpAndDstPort(String hashValue, String dstIp, Integer dstPort);

    // Composite lookup — must match on all three keys so the returned record belongs
    // to this exact destination, not to a different destination that happens to share the hash.
    Optional<ValidatorRecord> findFirstByHashValueAndDstIpAndDstPort(
            String hashValue, String dstIp, Integer dstPort);
}
