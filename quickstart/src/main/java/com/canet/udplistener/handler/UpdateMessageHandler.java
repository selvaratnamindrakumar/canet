package com.canet.udplistener.handler;

import com.canet.udplistener.entity.CapturedMessage;
import com.canet.udplistener.repository.CapturedMessageRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
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

    private final CapturedMessageRepository repository;

    /**
     * Duplicate detection window.  Only messages whose received_at falls within
     * the last N seconds are considered when checking whether a hash already exists.
     * Set to 0 to disable time-windowed dedup (check all records — not recommended
     * for high-volume deployments as it causes full table scans on the hash index).
     *
     * Examples:
     *   dedup.window.seconds=60     — 1-minute window
     *   dedup.window.seconds=3600   — 1-hour window (default)
     *   dedup.window.seconds=86400  — 24-hour window
     */
    @Value("${dedup.window.seconds:3600}")
    private long dedupWindowSeconds;

    // MD5 — maintained as per existing design.
    // ThreadLocal reuses the MessageDigest instance per worker thread to avoid
    // allocation overhead under high packet rates.
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
     * @param dstIp          destination IP (may be null if not IPv4)
     * @param sequenceNumber monotonic counter stamped at capture time in the pcap thread
     * @param receivedAt     wall-clock instant stamped at capture time in the pcap thread
     */
    public void handleMessage(byte[] payloadBytes,
                              int dstPort,
                              String dstIp,
                              long sequenceNumber,
                              Instant receivedAt) {

        long start = System.currentTimeMillis();
        String threadName = Thread.currentThread().getName();

        try {
            String hash = computeMd5(payloadBytes);

            log.debug("Thread={} seq={} hash={} port={} dstIp={}",
                    threadName, sequenceNumber, hash, dstPort, dstIp);

            Instant cutoff = Instant.now().minusSeconds(dedupWindowSeconds);

            if (repository.existsByHashValueAndReceivedAtAfter(hash, cutoff)) {
                log.debug("Thread={} seq={} duplicate hash within {}s window — skipped",
                        threadName, sequenceNumber, dedupWindowSeconds);
                return;
            }

            String id = UUID.randomUUID().toString();
            String payloadJson = buildJson(payloadBytes, dstPort, dstIp, sequenceNumber, receivedAt);

            CapturedMessage message = CapturedMessage.builder()
                    .sequenceNumber(sequenceNumber)
                    .receivedAt(receivedAt)
                    .hashValue(hash)
                    .ggasId(id)
                    .name(id)
                    .dstIp(dstIp)
                    .dstPort(dstPort)
                    .payloadJson(payloadJson)
                    .build();

            repository.save(message);

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

    private String buildJson(byte[] payloadBytes, int dstPort, String dstIp,
                             long sequenceNumber, Instant receivedAt) {
        String payload = new String(payloadBytes, StandardCharsets.UTF_8)
                .replace("\\", "\\\\")
                .replace("\"", "\\\"");
        return String.format(
                "{\"seq\":%d,\"receivedAt\":\"%s\",\"dstIp\":\"%s\",\"dstPort\":%d,\"payload\":\"%s\"}",
                sequenceNumber, receivedAt, dstIp, dstPort, payload);
    }
}
