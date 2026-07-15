package com.canet.udplistener.handler;

import com.canet.udplistener.entity.CapturedMessage;
import com.canet.udplistener.entity.ValidatorRecord;
import com.canet.udplistener.repository.CapturedMessageRepository;
import com.canet.udplistener.repository.ValidatorRecordRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Instant;
import java.util.HexFormat;
import java.util.UUID;

@Slf4j
@Component
@RequiredArgsConstructor
public class UpdateMessageHandler {

    private final CapturedMessageRepository capturedMessageRepository;
    private final ValidatorRecordRepository validatorRecordRepository;

    // ThreadLocal avoids allocating a new MessageDigest per packet while keeping thread safety.
    private static final ThreadLocal<MessageDigest> SHA256 = ThreadLocal.withInitial(() -> {
        try {
            return MessageDigest.getInstance("SHA-256");
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 not available", e);
        }
    });

    /**
     * @param payloadBytes   raw UDP payload
     * @param dstPort        destination port
     * @param dstIp          destination IP (may be null if not IPv4)
     * @param srcIp          source IP (may be null if not IPv4)
     * @param sequenceNumber monotonic counter stamped at capture time in the pcap thread
     * @param receivedAt     wall-clock instant stamped at capture time in the pcap thread
     */
    public void handleMessage(byte[] payloadBytes,
                              int dstPort,
                              String dstIp,
                              String srcIp,
                              long sequenceNumber,
                              Instant receivedAt) {

        long start = System.currentTimeMillis();
        String threadName = Thread.currentThread().getName();

        try {
            String hash = computeHash(payloadBytes);

            log.debug("Thread={} seq={} hash={} port={} dstIp={} srcIp={}",
                    threadName, sequenceNumber, hash, dstPort, dstIp, srcIp);

            // Composite dedup: same content to the same destination counts as a duplicate.
            // Same content to a DIFFERENT destination is a distinct message and must pass.
            if (capturedMessageRepository.existsByHashValueAndDstIpAndDstPort(hash, dstIp, dstPort)) {
                log.debug("Thread={} seq={} duplicate (hash+dst) — skipped", threadName, sequenceNumber);
                return;
            }

            String ggasId = UUID.randomUUID().toString();
            String payloadJson = buildJson(payloadBytes, dstPort, dstIp, srcIp, sequenceNumber, receivedAt);

            CapturedMessage message = CapturedMessage.builder()
                    .sequenceNumber(sequenceNumber)
                    .receivedAt(receivedAt)
                    .hashValue(hash)
                    .ggasId(ggasId)
                    .srcIp(srcIp)
                    .dstIp(dstIp)
                    .dstPort(dstPort)
                    .payloadJson(payloadJson)
                    .build();

            capturedMessageRepository.save(message);

            // Publish the same record to the validator table so Diosma's EXISTS
            // queries hit a read-optimised table rather than the write-heavy capture table.
            ValidatorRecord vr = ValidatorRecord.builder()
                    .sequenceNumber(sequenceNumber)
                    .receivedAt(receivedAt)
                    .hashValue(hash)
                    .ggasId(ggasId)
                    .srcIp(srcIp)
                    .dstIp(dstIp)
                    .dstPort(dstPort)
                    .build();

            validatorRecordRepository.save(vr);

            long elapsed = System.currentTimeMillis() - start;
            if (elapsed > 200) {
                log.warn("Thread={} seq={} slow handleMessage elapsed={}ms", threadName, sequenceNumber, elapsed);
            }

        } catch (Exception e) {
            log.error("Thread={} seq={} handleMessage failed", threadName, sequenceNumber, e);
        }
    }

    private String computeHash(byte[] data) {
        MessageDigest digest = SHA256.get();
        digest.reset();
        return HexFormat.of().formatHex(digest.digest(data));
    }

    private String buildJson(byte[] payloadBytes, int dstPort, String dstIp,
                             String srcIp, long sequenceNumber, Instant receivedAt) {
        String payload = new String(payloadBytes, StandardCharsets.UTF_8)
                .replace("\\", "\\\\")
                .replace("\"", "\\\"");
        return String.format(
                "{\"seq\":%d,\"receivedAt\":\"%s\",\"srcIp\":\"%s\",\"dstIp\":\"%s\","
                + "\"dstPort\":%d,\"payload\":\"%s\"}",
                sequenceNumber, receivedAt, srcIp, dstIp, dstPort, payload);
    }
}
