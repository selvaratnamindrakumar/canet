package com.canet.validator.db;

import com.canet.validator.entity.CapturedMessage;
import com.canet.validator.repository.CapturedMessageRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import java.util.Optional;

@Component
@ConditionalOnProperty(name = "validator.database.enabled", havingValue = "true", matchIfMissing = true)
@RequiredArgsConstructor
public class JpaDatabaseGateway implements DatabaseGateway {

    private final CapturedMessageRepository repository;

    @Override
    public CapturedMessage save(CapturedMessage message) {
        return repository.save(message);
    }

    @Override
    public Optional<CapturedMessage> findLatestByHash(String fileHash) {
        return repository.findFirstByFileHashOrderByIdDesc(fileHash);
    }
}
