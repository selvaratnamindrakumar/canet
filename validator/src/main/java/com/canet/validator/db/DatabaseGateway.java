package com.canet.validator.db;

import com.canet.validator.entity.CapturedMessage;

import java.util.Optional;

public interface DatabaseGateway {
    CapturedMessage save(CapturedMessage message);
    Optional<CapturedMessage> findLatestByHash(String fileHash);
}
