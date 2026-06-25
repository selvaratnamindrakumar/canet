package com.canet.fileproc.model;

public record IngestRecord(
        String cgi,
        String family,
        String qualifier,
        String value,
        long timestamp,
        SourceType source,
        String dataSource
) {
    public String snapshotKey() {
        return cgi + "|" + qualifier;
    }
}
