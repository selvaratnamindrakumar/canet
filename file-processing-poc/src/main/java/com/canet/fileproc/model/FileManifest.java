package com.canet.fileproc.model;

import java.time.Instant;

public record FileManifest(
        String path,
        String filename,
        SourceType sourceType,
        long sizeBytes,
        String checksum,
        Instant arrivedAt
) {}
