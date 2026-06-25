package com.canet.fileproc;

import com.canet.fileproc.ingest.CsvIngester;
import com.canet.fileproc.model.IngestRecord;
import com.canet.fileproc.model.ProcessingOutcome;
import com.canet.fileproc.model.SourceType;
import com.canet.fileproc.model.ValidationResult;
import com.canet.fileproc.pipeline.FilePipeline;
import com.canet.fileproc.pipeline.ValidationPipeline;
import com.canet.fileproc.processing.AgeOffProcessor;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.List;
import java.util.stream.Collectors;

import static org.assertj.core.api.Assertions.assertThat;

class FileProcessingPipelineTest {

    private final CsvIngester csvIngester = new CsvIngester();
    private final ValidationPipeline validationPipeline = ValidationPipeline.standard();
    private final AgeOffProcessor ageOffProcessor = new AgeOffProcessor();

    @TempDir
    Path tempDir;

    private static final String HEADER = "cgi,manufacturer,model,technology,region,signalStrength,timestamp";

    @Test
    void fullPipeline_countsCorrectly() throws IOException {
        long recentTs = Instant.now().minusSeconds(3600).toEpochMilli();
        long oldTs = Instant.now().minusSeconds(400L * 86400).toEpochMilli();

        String csv = String.join("\n",
                HEADER,
                // valid, recent
                "001-02-0003-00004,Nokia,Model1,LTE,EU," + recentTs + "," + recentTs,
                // valid, old (age-off candidate)
                "001-02-0003-00005,Nokia,Model2,LTE,EU,-70," + oldTs,
                // invalid cgi format
                "BADCGI,Nokia,Model3,LTE,EU,-65," + recentTs,
                // malformed (too few columns)
                "001-02-0003-00006,Nokia",
                // blank line
                ""
        );

        Path csvFile = tempDir.resolve("test.csv");
        Files.writeString(csvFile, csv);

        int retentionDays = 365;

        ProcessingOutcome outcome = FilePipeline.of(SourceType.BB)
                .parse(csvIngester::parse)
                .validate(validationPipeline)
                .filter(ageOffProcessor.ageOffFilter(SourceType.BB, retentionDays))
                .sink(stream -> stream.forEach(ignored -> {}))
                .execute(csvFile);

        assertThat(outcome.parsed()).isEqualTo(3);
        assertThat(outcome.valid()).isEqualTo(1);
        assertThat(outcome.invalid()).isEqualTo(2);
        assertThat(outcome.written()).isEqualTo(1);
    }

    @Test
    void validationPipeline_collectsAllErrors() {
        IngestRecord badRecord = new IngestRecord("", null, "LTE", null, -1L, SourceType.BB, "test");
        ValidationResult result = validationPipeline.validate(badRecord, "test.csv");

        assertThat(result.valid()).isFalse();
        assertThat(result.errors()).contains("cgi-not-blank", "cgi-format", "timestamp-positive", "value-not-null");
    }

    @Test
    void ageOffProcessor_hsNeverFilters() {
        long oldTs = Instant.now().minusSeconds(1000L * 86400).toEpochMilli();
        IngestRecord oldHsRecord = new IngestRecord("001-02-0003-00004", "cell", "geo", "1.0,2.0", oldTs, SourceType.HS, "src");

        boolean kept = ageOffProcessor.ageOffFilter(SourceType.HS, 30).test(oldHsRecord);
        assertThat(kept).isTrue();
    }

    @Test
    void ageOffProcessor_bbFiltersOldRecords() {
        long oldTs = Instant.now().minusSeconds(400L * 86400).toEpochMilli();
        IngestRecord oldRecord = new IngestRecord("001-02-0003-00004", "cell", "LTE", "-70", oldTs, SourceType.BB, "src");

        boolean kept = ageOffProcessor.ageOffFilter(SourceType.BB, 365).test(oldRecord);
        assertThat(kept).isFalse();
    }

    @Test
    void csvIngester_skipsBlankAndMalformedLines() throws IOException {
        String csv = HEADER + "\n" +
                "\n" +
                "toofew,columns\n" +
                "001-02-0003-00004,Nokia,M1,LTE,EU,-70," + Instant.now().toEpochMilli() + "\n";

        Path csvFile = tempDir.resolve("partial.csv");
        Files.writeString(csvFile, csv);

        List<IngestRecord> records = csvIngester.parse(csvFile).collect(Collectors.toList());
        assertThat(records).hasSize(1);
        assertThat(records.get(0).cgi()).isEqualTo("001-02-0003-00004");
    }
}
