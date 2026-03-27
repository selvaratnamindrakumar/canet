package com.example.csvprocessor.route;

import com.example.csvprocessor.config.DirectoryProperties;
import com.example.csvprocessor.config.TestModeProperties;
import org.apache.camel.LoggingLevel;
import org.apache.camel.builder.RouteBuilder;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import javax.annotation.PostConstruct;
import java.io.File;

/**
 * Test-mode Camel route that watches a drop directory for CSV and ZIP files
 * and injects them into the processing pipeline.
 *
 * <p>This route is <b>only active</b> when {@code processing.test-mode.enabled=true}.
 * It replaces the SMB polling route for local testing without requiring an SMB
 * or SFTP server.
 *
 * <p><b>Usage</b>: drop a {@code .csv} or {@code .zip} file into the directory
 * configured by {@code processing.test-mode.drop-directory} (default:
 * {@code /data/processor/test-drop}).  The file is moved into the appropriate
 * processing directory automatically:
 * <ul>
 *   <li>{@code .csv} files → input CSV directory (picked up by {@link CsvProcessingRoute})</li>
 *   <li>{@code .zip} files → input ZIP directory (picked up by {@link ZipExtractionRoute})</li>
 * </ul>
 * Processed files are moved to a {@code .done} sub-folder to prevent re-processing.
 */
@Component
@ConditionalOnProperty(name = "processing.test-mode.enabled", havingValue = "true")
public class TestDropRoute extends RouteBuilder {

    @Autowired
    private TestModeProperties testModeProperties;

    @Autowired
    private DirectoryProperties directories;

    @PostConstruct
    public void ensureDropDirectoryExists() {
        File dropDir = new File(testModeProperties.getDropDirectory());
        if (!dropDir.exists() && !dropDir.mkdirs()) {
            log.warn("Could not create test drop directory: {}", testModeProperties.getDropDirectory());
        }
    }

    @Override
    public void configure() {

        log.info("TEST MODE ACTIVE — SMB polling and SFTP upload are disabled. "
                + "Drop CSV/ZIP files into: {}", testModeProperties.getDropDirectory());

        onException(Exception.class)
                .log(LoggingLevel.ERROR,
                        "Test drop route error for '${file:name}': ${exception.message}")
                .handled(true);

        // Route for dropped CSV files → directly into the CSV processing directory
        from("file:" + testModeProperties.getDropDirectory()
                + "?include=(?i).*\\.csv"
                + "&readLock=changed"
                + "&readLockCheckInterval=2000"
                + "&readLockTimeout=300000"
                + "&move=.done"
                + "&moveFailed=.error"
                + "&maxMessagesPerPoll=1"
                + "&initialDelay=2000")
                .routeId("test-drop-csv-route")
                .log(LoggingLevel.INFO, "TEST MODE: Injecting CSV into pipeline: ${file:name}")
                .to("file:" + directories.getInputCsv());

        // Route for dropped ZIP files → into the ZIP extraction directory
        from("file:" + testModeProperties.getDropDirectory()
                + "?include=(?i).*\\.zip"
                + "&readLock=changed"
                + "&readLockCheckInterval=2000"
                + "&readLockTimeout=300000"
                + "&move=.done"
                + "&moveFailed=.error"
                + "&maxMessagesPerPoll=1"
                + "&initialDelay=2000")
                .routeId("test-drop-zip-route")
                .log(LoggingLevel.INFO, "TEST MODE: Injecting ZIP into pipeline: ${file:name}")
                .to("file:" + directories.getInputZip());
    }
}
