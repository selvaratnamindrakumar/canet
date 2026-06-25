package com.canet.fileproc.processing;

import com.canet.fileproc.model.IngestRecord;
import com.canet.fileproc.model.SourceType;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.function.Predicate;

@Component
public class AgeOffProcessor {

    public Predicate<IngestRecord> ageOffFilter(SourceType sourceType, int retentionDays) {
        if (sourceType == SourceType.HS) {
            return r -> true;
        }

        Instant cutoff = Instant.now().minusSeconds((long) retentionDays * 86400);
        return r -> Instant.ofEpochMilli(r.timestamp()).isAfter(cutoff);
    }

    public long countEligibleForRemoval(java.util.stream.Stream<IngestRecord> records, SourceType sourceType, int retentionDays) {
        Predicate<IngestRecord> keepFilter = ageOffFilter(sourceType, retentionDays);
        return records.filter(keepFilter.negate()).count();
    }
}
