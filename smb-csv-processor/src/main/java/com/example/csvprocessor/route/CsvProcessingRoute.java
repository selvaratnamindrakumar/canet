package com.example.csvprocessor.route;

import com.example.csvprocessor.config.DirectoryProperties;
import com.example.csvprocessor.model.ProcessingResult;
import com.example.csvprocessor.service.CsvRowProcessor;
import org.apache.camel.LoggingLevel;
import org.apache.camel.builder.RouteBuilder;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.io.File;

/**
 * Camel route that processes each extracted CSV file line-by-line through the
 * mapping/validation pipeline defined in {@code mapping.yml}.
 *
 * <p>Processing is fully streaming — no CSV file is loaded into memory. Valid rows
 * are written to {@code outputSuccess}; invalid rows are written with an appended
 * {@code QUARANTINE_REASON} column to {@code outputQuarantine}.
 *
 * <p>After processing:
 * <ul>
 *   <li>The CSV source file is deleted on success (to prevent accumulation) or moved
 *       to {@code .error} if an exception occurs during processing.</li>
 *   <li>Success files in {@code outputSuccess} are picked up by
 *       {@link SftpUploadRoute}.</li>
 * </ul>
 *
 * <p>The {@code readLockTimeout} is set to 2 hours to accommodate very large CSV
 * files.  Adjust {@code processing.directories.input-csv} read-lock settings in
 * {@code application.yml} if needed.
 */
@Component
public class CsvProcessingRoute extends RouteBuilder {

    @Autowired
    private DirectoryProperties directories;

    @Autowired
    private CsvRowProcessor csvRowProcessor;

    @Override
    public void configure() {

        onException(Exception.class)
                .log(LoggingLevel.ERROR,
                        "CSV processing failed for '${file:name}': ${exception.message}")
                .handled(true);

        from("file:" + directories.getInputCsv()
                + "?include=.*\\.csv"
                + "&readLock=changed"
                + "&readLockCheckInterval=5000"
                + "&readLockTimeout=7200000"   // 2-hour timeout for very large files
                + "&delete=true"
                + "&moveFailed=.error"
                + "&maxMessagesPerPoll=1"
                + "&sortBy=file:modified")
                .routeId("csv-processing-route")
                .log(LoggingLevel.INFO, "Processing CSV: ${file:name} (${file:size} bytes)")
                .process(exchange -> {
                    File csvFile = exchange.getIn().getBody(File.class);
                    ProcessingResult result = csvRowProcessor.processFile(csvFile);
                    // Place the result summary in the message body for downstream logging
                    exchange.getIn().setBody(result);
                })
                .log(LoggingLevel.INFO,
                        "CSV done: ${file:name} — "
                        + "total=${body.totalRows}, "
                        + "success=${body.successCount}, "
                        + "quarantine=${body.quarantineCount}");
    }
}
