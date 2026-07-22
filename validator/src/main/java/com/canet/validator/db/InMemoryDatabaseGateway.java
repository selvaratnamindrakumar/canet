package com.canet.validator.db;

import com.canet.validator.entity.CapturedMessage;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicLong;

/**
 * No-database fallback — holds records in memory for the lifetime of the process.
 * Activated by validator.database.enabled=false (spring profile: no-db).
 * Data is lost on restart; intended for secure environments without a DB.
 */
@Slf4j
@Component
@ConditionalOnProperty(name = "validator.database.enabled", havingValue = "false")
public class InMemoryDatabaseGateway implements DatabaseGateway {

    private final AtomicLong idSequence = new AtomicLong(1);
    private final ConcurrentHashMap<String, CopyOnWriteArrayList<CapturedMessage>> byHash =
            new ConcurrentHashMap<>();

    @Override
    public CapturedMessage save(CapturedMessage message) {
        message.setId(idSequence.getAndIncrement());
        byHash.computeIfAbsent(message.getFileHash(), k -> new CopyOnWriteArrayList<>())
              .add(message);
        log.debug("in-memory save id={} hash={}", message.getId(), message.getFileHash());
        return message;
    }

    @Override
    public Optional<CapturedMessage> findLatestByHash(String fileHash) {
        List<CapturedMessage> records = byHash.get(fileHash);
        if (records == null || records.isEmpty()) return Optional.empty();
        return records.stream()
                .max(Comparator.comparingLong(CapturedMessage::getId));
    }
}
