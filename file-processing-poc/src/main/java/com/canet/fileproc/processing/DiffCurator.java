package com.canet.fileproc.processing;

import com.canet.fileproc.model.IngestRecord;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import java.util.stream.Stream;

@Component
public class DiffCurator {

    public DiffResult diff(Map<String, IngestRecord> previous, Map<String, IngestRecord> current) {
        List<IngestRecord> added = current.entrySet().stream()
                .filter(e -> !previous.containsKey(e.getKey()))
                .map(Map.Entry::getValue)
                .collect(Collectors.toList());

        List<IngestRecord> modified = current.entrySet().stream()
                .filter(e -> previous.containsKey(e.getKey()))
                .filter(e -> !e.getValue().value().equals(previous.get(e.getKey()).value()))
                .map(Map.Entry::getValue)
                .collect(Collectors.toList());

        List<IngestRecord> deleted = previous.entrySet().stream()
                .filter(e -> !current.containsKey(e.getKey()))
                .map(Map.Entry::getValue)
                .collect(Collectors.toList());

        return new DiffResult(added, modified, deleted);
    }

    public Map<String, IngestRecord> toSnapshot(Stream<IngestRecord> records) {
        return records.collect(Collectors.toMap(
                IngestRecord::snapshotKey,
                r -> r,
                (existing, replacement) -> replacement
        ));
    }
}
