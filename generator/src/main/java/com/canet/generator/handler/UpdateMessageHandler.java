package com.canet.generator.handler;

import com.canet.generator.client.ValidatorClient;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Instant;
import java.util.HexFormat;
import java.util.UUID;

/**
 * Processes each captured UDP packet:
 *  1. Computes MD5 hash of the raw payload.
 *  2. Calls the validator's POST /api/validator/record endpoint.
 *  3. The validator performs duplicate detection and persists the record.
 *
 * No database access occurs here — all persistence is delegated to the validator.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class UpdateMessageHandler {

    private final ValidatorClient validatorClient;

    // MD5 — maintained as per existing design.
    // ThreadLocal reuses the digest per worker thread to avoid per-packet allocation.
    private static final ThreadLocal<MessageDigest> MD5 = ThreadLocal.withInitial(() -> {
        try {
            return MessageDigest.getInstance("MD5");
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("MD5 not available", e);
        }
    });

    /**
     * @param payloadBytes   raw UDP payload
     * @param dstPort        destination port
     * @param dstIp          destination IP (null if not IPv4)
     * @param sequenceNumber capture-time monotonic sequence number
     * @param receivedAt     capture-time wall-clock instant
     */
    public void handleMessage(byte[] payloadBytes,
                              int dstPort,
                              String dstIp,
                              long sequenceNumber,
                              Instant receivedAt) {

        String threadName = Thread.currentThread().getName();
        long start = System.currentTimeMillis();

        try {
            String hash  = computeMd5(payloadBytes);
            String ggasId = UUID.randomUUID().toString();

            log.debug("Thread={} seq={} hash={} dstIp={} dstPort={}",
                    threadName, sequenceNumber, hash, dstIp, dstPort);

            ValidatorClient.RegistrationResult result =
                    validatorClient.register(hash, ggasId, dstIp, dstPort, sequenceNumber, receivedAt);

            switch (result) {
                case CREATED    -> log.debug("seq={} registered hash={}", sequenceNumber, hash);
                case DUPLICATE  -> log.debug("seq={} duplicate hash={} — skipped", sequenceNumber, hash);
                case ERROR      -> log.warn("seq={} validator registration failed hash={}", sequenceNumber, hash);
            }

            long elapsed = System.currentTimeMillis() - start;
            if (elapsed > 200) {
                log.warn("Thread={} seq={} slow handleMessage elapsed={}ms", threadName, sequenceNumber, elapsed);
            }

        } catch (Exception e) {
            log.error("Thread={} seq={} handleMessage failed", threadName, sequenceNumber, e);
        }
    }

    private String computeMd5(byte[] data) {
        MessageDigest digest = MD5.get();
        digest.reset();
        return HexFormat.of().formatHex(digest.digest(data));
    }
}
