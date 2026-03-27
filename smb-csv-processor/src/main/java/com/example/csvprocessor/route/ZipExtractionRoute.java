package com.example.csvprocessor.route;

import com.example.csvprocessor.config.DirectoryProperties;
import com.example.csvprocessor.service.ZipExtractorService;
import org.apache.camel.LoggingLevel;
import org.apache.camel.builder.RouteBuilder;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.io.File;

/**
 * Camel route that watches the local ZIP input directory and extracts CSV files.
 *
 * <p>Camel's file component provides:
 * <ul>
 *   <li><b>readLock=changed</b> — waits until the file stops growing before
 *       consuming, preventing partial downloads from being processed.</li>
 *   <li><b>move=.done</b> — atomically renames the ZIP to
 *       {@code .done/filename.zip} after successful extraction.</li>
 *   <li><b>moveFailed=.error</b> — moves the ZIP to the {@code .error} folder
 *       if extraction fails, so it is not retried in an infinite loop.</li>
 *   <li><b>maxMessagesPerPoll=1</b> — processes one ZIP at a time to keep
 *       memory footprint predictable.</li>
 * </ul>
 *
 * <p>Extracted CSVs land in the {@code inputCsv} directory and are automatically
 * picked up by {@link CsvProcessingRoute}.
 */
@Component
public class ZipExtractionRoute extends RouteBuilder {

    @Autowired
    private DirectoryProperties directories;

    @Autowired
    private ZipExtractorService zipExtractorService;

    @Override
    public void configure() {

        onException(Exception.class)
                .log(LoggingLevel.ERROR,
                        "ZIP extraction failed for '${file:name}': ${exception.message}")
                .handled(true);

        from("file:" + directories.getInputZip()
                + "?include=.*\\.zip"
                + "&readLock=changed"
                + "&readLockCheckInterval=5000"
                + "&readLockTimeout=3600000"
                + "&move=.done"
                + "&moveFailed=.error"
                + "&maxMessagesPerPoll=1"
                + "&sortBy=file:modified")   // oldest ZIP first
                .routeId("zip-extraction-route")
                .log(LoggingLevel.INFO, "Extracting ZIP: ${file:name} (${file:size} bytes)")
                .process(exchange -> {
                    File zipFile = exchange.getIn().getBody(File.class);
                    zipExtractorService.extractZip(zipFile);
                })
                .log(LoggingLevel.INFO, "ZIP extraction complete: ${file:name}");
    }
}
