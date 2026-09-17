package com.example.csvprocessor.route;

import com.example.csvprocessor.config.DirectoryProperties;
import com.example.csvprocessor.model.ProcessingResult;
import com.example.csvprocessor.service.CsvRowProcessor;
import org.apache.camel.LoggingLevel;
import org.apache.camel.builder.RouteBuilder;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.io.File;
import java.util.List;

/**
 * Camel route that batches all CSV files from one poll cycle and processes
 * them together, producing a single merged success CSV and a single merged
 * quarantine CSV.
 *
 * <p>Why batching: the source ZIP typically contains multiple CSVs split by
 * MNC. Processing them together (via {@code completionFromBatchConsumer})
 * gives the downstream consumer one file to upload rather than one per MNC.
 *
 * <p>{@code maxMessagesPerPoll=100} ensures all files present at poll time
 * are consumed in one batch. {@code completionFromBatchConsumer()} tells the
 * aggregator to trigger as soon as the file component has delivered the whole
 * poll batch — no artificial wait is added when files arrive together.
 * {@code completionTimeout(30_000)} is a safety net for the rare case where
 * the batch-size signal is not delivered.
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
                        "CSV batch processing failed: ${exception.message}")
                .handled(true);

        from("file:" + directories.getInputCsv()
                + "?include=.*\\.csv"
                + "&readLock=changed"
                + "&readLockCheckInterval=5000"
                + "&readLockTimeout=7200000"
                + "&delete=true"
                + "&moveFailed=.error"
                + "&maxMessagesPerPoll=100"
                + "&sortBy=file:modified")
                .routeId("csv-processing-route")
                .log(LoggingLevel.INFO, "Queuing for batch: ${file:name} (${file:size} bytes)")
                .aggregate(constant("batch"), new CsvFileAggregationStrategy())
                    .completionFromBatchConsumer()
                    .completionTimeout(30_000)
                .log(LoggingLevel.INFO, "Processing CSV batch: ${body.size()} file(s)")
                .process(exchange -> {
                    @SuppressWarnings("unchecked")
                    List<File> csvFiles = exchange.getIn().getBody(List.class);
                    ProcessingResult result = csvRowProcessor.processFiles(csvFiles);
                    exchange.getIn().setBody(result);
                })
                .log(LoggingLevel.INFO,
                        "Batch done: ${body.sourceFileName} — "
                        + "total=${body.totalRows}, "
                        + "success=${body.successCount}, "
                        + "quarantine=${body.quarantineCount}");
    }
}
