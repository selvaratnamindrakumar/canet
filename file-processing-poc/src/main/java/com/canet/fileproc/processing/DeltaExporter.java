package com.canet.fileproc.processing;

import com.canet.fileproc.model.IngestRecord;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import java.util.stream.Stream;

@Component
public class DeltaExporter {

    public List<IngestRecord> exportDelta(Map<String, IngestRecord> previous, Map<String, IngestRecord> current) {
        return current.entrySet().stream()
                .filter(e -> isNewOrChanged(e.getKey(), e.getValue(), previous))
                .map(Map.Entry::getValue)
                .collect(Collectors.toList());
    }

    private boolean isNewOrChanged(String key, IngestRecord current, Map<String, IngestRecord> previous) {
        IngestRecord prev = previous.get(key);
        return prev == null || !prev.value().equals(current.value()) || prev.timestamp() != current.timestamp();
    }

    public Map<String, IngestRecord> toSnapshot(Stream<IngestRecord> records) {
        return records.collect(Collectors.toMap(
                IngestRecord::snapshotKey,
                r -> r,
                (existing, replacement) -> replacement
        ));
    }
}
