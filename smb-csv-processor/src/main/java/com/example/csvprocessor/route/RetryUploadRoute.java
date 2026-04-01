package com.example.csvprocessor.route;

import com.example.csvprocessor.config.DirectoryProperties;
import com.example.csvprocessor.config.OutputProperties;
import org.apache.camel.LoggingLevel;
import org.apache.camel.builder.RouteBuilder;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import java.io.File;

/**
 * Camel route that moves files from the {@code .uploadError} sub-folder back to
 * the success output directory so they are picked up again by {@link SftpUploadRoute}.
 *
 * <p>This route is active when {@code processing.output.upload-error-retry-enabled=true}
 * (the default).  It polls the {@code .uploadError} folder on the configured interval
 * (default 15 minutes) and moves any CSV files found back to the parent success
 * directory for another upload attempt.
 *
 * <p>The {@code .uploadError} folder sits under {@code processing.directories.output-success}:
 * <pre>
 *   /data/processor/output/success/
 *     ├── cells_success_20240101120000.csv   ← waiting for SFTP upload
 *     └── .uploadError/
 *           └── cells_success_20231231.csv  ← failed upload → retry from here
 * </pre>
 *
 * <p>Disable this route by setting:
 * <pre>processing.output.upload-error-retry-enabled=false</pre>
 */
@Component
@ConditionalOnProperty(
        name = "processing.output.upload-error-retry-enabled",
        havingValue = "true",
        matchIfMissing = true)
public class RetryUploadRoute extends RouteBuilder {

    @Autowired
    private DirectoryProperties directories;

    @Autowired
    private OutputProperties outputProperties;

    @Override
    public void configure() {

        String uploadErrorDir = directories.getOutputSuccess()
                + File.separator + ".uploadError";

        from("file:" + uploadErrorDir
                + "?include=.*\\.csv"
                + "&delay=" + outputProperties.getUploadErrorRetryIntervalMs()
                + "&move=" + directories.getOutputSuccess()
                + "&moveFailed=.retryFailed"
                + "&maxMessagesPerPoll=10"
                + "&sortBy=file:modified")
                .routeId("retry-upload-route")
                .log(LoggingLevel.INFO,
                        "Retry: moving '${file:name}' from .uploadError back to success directory");
    }
}
