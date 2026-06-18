package com.canet.udplistener.handler;

import com.canet.udplistener.entity.CapturedMessage;
import com.canet.udplistener.repository.CapturedMessageRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;

@Slf4j
@Component
@RequiredArgsConstructor
public class UpdateMessageHandler {

    private final CapturedMessageRepository repository;

    // SHA-256 is thread-safe when instantiated per-call; pool via ThreadLocal avoids allocation cost.
    private static final ThreadLocal<MessageDigest> SHA256 = ThreadLocal.withInitial(() -> {
        try {
            return MessageDigest.getInstance("SHA-256");
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 not available", e);
        }
    });

    public void handleMessage(byte[] payloadBytes, int dstPort, String dstIp) {
        long start = System.currentTimeMillis();
        String threadName = Thread.currentThread().getName();

        try {
            String hash = computeHash(payloadBytes);
            String payloadJson = buildJson(payloadBytes, dstPort, dstIp);

            log.debug("Thread={} hash={} port={} ip={}", threadName, hash, dstPort, dstIp);

            if (repository.existsByHashValue(hash)) {
                log.debug("Thread={} duplicate hash={} — skipped", threadName, hash);
                return;
            }

            CapturedMessage message = CapturedMessage.builder()
                    .hashValue(hash)
                    .dstIp(dstIp)
                    .dstPort(dstPort)
                    .payloadJson(payloadJson)
                    .build();

            repository.save(message);

            long elapsed = System.currentTimeMillis() - start;
            if (elapsed > 200) {
                log.warn("Thread={} slow handleMessage elapsed={}ms hash={}", threadName, elapsed, hash);
            }

        } catch (Exception e) {
            log.error("Thread={} handleMessage failed", threadName, e);
        }
    }

    private String computeHash(byte[] data) {
        MessageDigest digest = SHA256.get();
        digest.reset();
        byte[] hashBytes = digest.digest(data);
        return HexFormat.of().formatHex(hashBytes);
    }

    private String buildJson(byte[] payloadBytes, int dstPort, String dstIp) {
        String payload = new String(payloadBytes, StandardCharsets.UTF_8)
                .replace("\\", "\\\\")
                .replace("\"", "\\\"");
        return String.format(
                "{\"dstIp\":\"%s\",\"dstPort\":%d,\"payload\":\"%s\"}",
                dstIp, dstPort, payload);
    }
}
