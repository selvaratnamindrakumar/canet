package com.canet.generator.handler;

import com.canet.generator.client.ValidatorClient;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Instant;
import java.util.Base64;
import java.util.HexFormat;
import java.util.UUID;

/**
 * Processes each captured UDP packet:
 *  1. Computes MD5 hash of the raw payload.
 *  2. Encodes the payload as hex (always) and optionally as Base64.
 *  3. Calls the validator's POST /api/validator/record endpoint.
 *
 * No database access occurs here — all persistence is delegated to the validator.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class UpdateMessageHandler {

    private final ValidatorClient validatorClient;

    /**
     * When true, Base64-encodes the raw payload bytes and includes them in
     * the request body alongside the hex encoding.
     * Set enable.base64.payload=true in application.properties.
     */
    @Value("${enable.base64.payload:false}")
    private boolean enableBase64Payload;

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
     * @param payloadBytes   raw UDP payload bytes
     * @param srcPort        source port from the UDP header
     * @param srcIp          source IP address (null if not IPv4)
     * @param dstPort        destination port from the UDP header
     * @param dstIp          destination IP address (null if not IPv4)
     * @param sequenceNumber capture-time monotonic sequence number
     * @param receivedAt     capture-time wall-clock instant
     */
    public void handleMessage(byte[] payloadBytes,
                              int    srcPort,
                              String srcIp,
                              int    dstPort,
                              String dstIp,
                              long   sequenceNumber,
                              Instant receivedAt) {

        String threadName = Thread.currentThread().getName();
        long start = System.currentTimeMillis();

        try {
            String hash       = computeMd5(payloadBytes);
            String ggasId     = UUID.randomUUID().toString();
            String payloadHex = HexFormat.of().formatHex(payloadBytes);
            String payloadB64 = enableBase64Payload
                    ? Base64.getEncoder().encodeToString(payloadBytes)
                    : null;

            log.debug("Thread={} seq={} hash={} src={}:{} dst={}:{} payloadBytes={} base64={}",
                    threadName, sequenceNumber, hash,
                    srcIp, srcPort, dstIp, dstPort,
                    payloadBytes.length, enableBase64Payload);

            ValidatorClient.RegistrationResult result =
                    validatorClient.register(
                            hash, ggasId,
                            srcIp, srcPort,
                            dstIp, dstPort,
                            payloadHex, payloadB64,
                            sequenceNumber, receivedAt);

            switch (result) {
                case CREATED   -> log.debug("seq={} registered hash={}", sequenceNumber, hash);
                case DUPLICATE -> log.debug("seq={} duplicate hash={} — skipped", sequenceNumber, hash);
                case ERROR     -> log.warn("seq={} validator registration failed hash={}", sequenceNumber, hash);
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
