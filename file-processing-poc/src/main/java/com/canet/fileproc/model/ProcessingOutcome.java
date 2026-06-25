package com.canet.fileproc.model;

public record ProcessingOutcome(
        String filename,
        int parsed,
        int valid,
        int invalid,
        int written,
        long durationMs
) {}
