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
 * HS-format: pipe-delimited
 * cgi|manufacturer|model|cellId|lac|mcc|mnc|latitude|longitude
 */
@Component
public class HsIngester {

    private static final int EXPECTED_FIELDS = 9;

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
            String cgi = parts[0].trim();
            String manufacturer = parts[1].trim();
            String model = parts[2].trim();
            String cellId = parts[3].trim();
            String lac = parts[4].trim();
            String mcc = parts[5].trim();
            String mnc = parts[6].trim();
            String latitude = parts[7].trim();
            String longitude = parts[8].trim();

            String value = latitude + "," + longitude;
            String qualifier = "geo";
            String family = "cell";
            String dataSource = manufacturer + "/" + model;
            // HS records use current time since the format has no timestamp field
            long timestamp = System.currentTimeMillis();

            return Optional.of(new IngestRecord(cgi, family, qualifier, value, timestamp, SourceType.HS, dataSource));
        } catch (Exception e) {
            return Optional.empty();
        }
    }
}
