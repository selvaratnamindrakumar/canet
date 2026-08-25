package com.canet.validator.repository;

import com.canet.validator.entity.CapturedMessage;
import org.springframework.data.repository.CrudRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface CapturedMessageRepository extends CrudRepository<CapturedMessage, Long> {

    Optional<CapturedMessage> findFirstByFileHashOrderByIdDesc(String fileHash);
}
