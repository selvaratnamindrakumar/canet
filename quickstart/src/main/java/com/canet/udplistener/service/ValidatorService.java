package com.canet.udplistener.service;

import com.canet.udplistener.entity.ValidatorRecord;
import com.canet.udplistener.repository.ValidatorRecordRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.Optional;

@Slf4j
@Service
@RequiredArgsConstructor
public class ValidatorService {

    private final ValidatorRecordRepository repository;

    /**
     * Called by the generator to register a validated record.
     * The composite unique index on (hash_value, dst_ip, dst_port) makes this
     * idempotent — a duplicate insert will throw a constraint violation, which
     * the caller should treat as "already registered" rather than an error.
     */
    @Transactional
    public ValidatorRecord registerRecord(String hashValue,
                                          String ggasId,
                                          String srcIp,
                                          String dstIp,
                                          Integer dstPort,
                                          Long sequenceNumber,
                                          Instant receivedAt) {
        ValidatorRecord record = ValidatorRecord.builder()
                .hashValue(hashValue)
                .ggasId(ggasId)
                .srcIp(srcIp)
                .dstIp(dstIp)
                .dstPort(dstPort)
                .sequenceNumber(sequenceNumber)
                .receivedAt(receivedAt)
                .build();

        return repository.save(record);
    }

    /**
     * Composite exists check used by Diosma to decide whether to allow traffic.
     *
     * Checking hash alone (SELECT COUNT(*) WHERE hash = ?) is insufficient when
     * the same payload reaches different destinations — only the combination of
     * hash + dstIp + dstPort identifies a unique, already-validated message.
     *
     * This prevents RACS from allowing a replay of the same content through a
     * different port or IP simply because the hash was seen before.
     */
    @Transactional(readOnly = true)
    public boolean exists(String hashValue, String dstIp, Integer dstPort) {
        return repository.existsByHashValueAndDstIpAndDstPort(hashValue, dstIp, dstPort);
    }

    /**
     * Returns the record for a specific composite key so the caller gets the
     * sequenceNumber and receivedAt that belong to this exact destination.
     * A hash-only lookup is wrong when the same payload was sent to multiple
     * destinations — it would return a record for a different dstIp/dstPort.
     */
    @Transactional(readOnly = true)
    public Optional<ValidatorRecord> findByCompositeKey(String hashValue, String dstIp, Integer dstPort) {
        return repository.findFirstByHashValueAndDstIpAndDstPort(hashValue, dstIp, dstPort);
    }
}
