package com.canet.generator.handler;

import com.canet.generator.client.DiosmaClient;
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
 *  2. Encodes the payload as hex (optionally Base64); strips all whitespace.
 *  3. POST /api/validator/create  — persists the hash.
 *  4. On 201, POST to Diosma     — sends the payload for independent verification.
 *     Diosma recalculates the hash from the payload and calls /racs to confirm.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class UpdateMessageHandler {

    private final ValidatorClient validatorClient;
    private final DiosmaClient    diosmaClient;

    @Value("${enable.base64.payload:false}")
    private boolean enableBase64Payload;

    // ThreadLocal reuses the digest per worker thread.
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
                              int     srcPort,
                              String  srcIp,
                              int     dstPort,
                              String  dstIp,
                              long    sequenceNumber,
                              Instant receivedAt) {

        String threadName = Thread.currentThread().getName();
        long start = System.currentTimeMillis();

        try {
            String hash       = computeMd5(payloadBytes);
            String uuid       = UUID.randomUUID().toString();
            String payloadHex = HexFormat.of().formatHex(payloadBytes);   // always hex
            String payload    = buildPayload(payloadBytes);                // hex or base64 per config

            log.info("Thread={} seq={} hash={} src={}:{} dst={}:{} payloadBytes={}",
                    threadName, sequenceNumber, hash,
                    srcIp, srcPort, dstIp, dstPort, payloadBytes.length);

            // Step 1 — register with validator
            ValidatorClient.RegistrationResult result =
                    validatorClient.create(hash, uuid, receivedAt, uuid, srcIp, srcPort, payload);

            if (result == ValidatorClient.RegistrationResult.CREATED) {
                log.info("seq={} registered hash={} uuid={}", sequenceNumber, hash, uuid);

                // Step 2 — notify Diosma only after confirmed storage.
                // Always send hex so Diosma can decode bytes and recompute MD5.
                diosmaClient.postPayload(payloadHex, uuid, srcIp, srcPort, receivedAt);

            } else {
                log.warn("seq={} validator /create failed hash={} — Diosma NOT notified", sequenceNumber, hash);
            }

            long elapsed = System.currentTimeMillis() - start;
            if (elapsed > 200) {
                log.warn("Thread={} seq={} slow handleMessage elapsed={}ms", threadName, sequenceNumber, elapsed);
            }

        } catch (Exception e) {
            log.error("Thread={} seq={} handleMessage failed", threadName, sequenceNumber, e);
        }
    }

    /**
     * Encodes payload as hex (default) or Base64 and strips all whitespace/
     * control characters so the value is safe inside a JSON field.
     */
    private String buildPayload(byte[] payloadBytes) {
        String raw = enableBase64Payload
                ? Base64.getEncoder().encodeToString(payloadBytes)
                : HexFormat.of().formatHex(payloadBytes);
        return raw.replaceAll("[\\s\\r\\n\\t]", "");
    }

    private String computeMd5(byte[] data) {
        MessageDigest digest = MD5.get();
        digest.reset();
        return HexFormat.of().formatHex(digest.digest(data));
    }
}
