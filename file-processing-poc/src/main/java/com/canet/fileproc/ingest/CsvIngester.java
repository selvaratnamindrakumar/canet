package com.canet.fileproc.ingest;

import com.canet.fileproc.model.IngestRecord;
import com.canet.fileproc.model.SourceType;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.Optional;
import java.util.stream.Stream;

@Component
public class CsvIngester {

    private static final int EXPECTED_COLUMNS = 7;

    public Stream<IngestRecord> parse(Path path) {
        try {
            return Files.lines(path)
                    .skip(1)
                    .filter(line -> !line.isBlank())
                    .map(this::parseLine)
                    .flatMap(Optional::stream);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    public Stream<IngestRecord> parseContent(String content) {
        return content.lines()
                .skip(1)
                .filter(line -> !line.isBlank())
                .map(this::parseLine)
                .flatMap(Optional::stream);
    }

    private Optional<IngestRecord> parseLine(String line) {
        String[] parts = Arrays.stream(line.split(",", -1))
                .map(String::trim)
                .toArray(String[]::new);

        if (parts.length < EXPECTED_COLUMNS) {
            return Optional.empty();
        }

        try {
            String cgi = parts[0];
            String manufacturer = parts[1];
            String model = parts[2];
            String technology = parts[3];
            String region = parts[4];
            String signalStrength = parts[5];
            long timestamp = Long.parseLong(parts[6]);

            String value = signalStrength;
            String qualifier = technology;
            String family = "cell";
            String dataSource = manufacturer + "/" + model + "/" + region;

            return Optional.of(new IngestRecord(cgi, family, qualifier, value, timestamp, SourceType.BB, dataSource));
        } catch (NumberFormatException e) {
            return Optional.empty();
        }
    }
}
