package com.canet.fileproc.processing;

import com.canet.fileproc.model.IngestRecord;

import java.util.List;

public record DiffResult(
        List<IngestRecord> added,
        List<IngestRecord> modified,
        List<IngestRecord> deleted
) {
    public int totalChanges() {
        return added.size() + modified.size() + deleted.size();
    }
}
