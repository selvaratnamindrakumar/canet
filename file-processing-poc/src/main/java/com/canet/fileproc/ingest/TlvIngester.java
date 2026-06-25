package com.canet.fileproc.ingest;

import com.canet.fileproc.model.IngestRecord;
import com.canet.fileproc.model.SourceType;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Optional;
import java.util.stream.Stream;

/**
 * Simulates TLV (Tag-Length-Value) parsing using pipe-delimited lines:
 * TAG|LENGTH|cgi|family|qualifier|value|timestamp
 */
@Component
public class TlvIngester {

    private static final int EXPECTED_FIELDS = 7;

    public Stream<IngestRecord> parse(Path path) {
        try {
            return Files.lines(path)
                    .filter(line -> !line.isBlank())
                    .map(this::parseLine)
                    .flatMap(Optional::stream);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    public Stream<IngestRecord> parseContent(String content) {
        return content.lines()
                .filter(line -> !line.isBlank())
                .map(this::parseLine)
                .flatMap(Optional::stream);
    }

    private Optional<IngestRecord> parseLine(String line) {
        String[] parts = line.split("\\|", -1);

        if (parts.length < EXPECTED_FIELDS) {
            return Optional.empty();
        }

        try {
            String tag = parts[0].trim();
            int declaredLength = Integer.parseInt(parts[1].trim());
            String cgi = parts[2].trim();
            String family = parts[3].trim();
            String qualifier = parts[4].trim();
            String value = parts[5].trim();
            long timestamp = Long.parseLong(parts[6].trim());

            // LENGTH field validates that value content is within expected bounds
            if (value.length() > declaredLength) {
                return Optional.empty();
            }

            return Optional.of(new IngestRecord(cgi, family, qualifier, value, timestamp, SourceType.IS, tag));
        } catch (NumberFormatException e) {
            return Optional.empty();
        }
    }
}
