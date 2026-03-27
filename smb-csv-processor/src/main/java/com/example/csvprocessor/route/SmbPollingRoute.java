package com.example.csvprocessor.route;

import com.example.csvprocessor.config.SmbProperties;
import com.example.csvprocessor.service.SmbDownloadService;
import org.apache.camel.LoggingLevel;
import org.apache.camel.builder.RouteBuilder;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

/**
 * Camel route that periodically polls the SMB share and triggers file download.
 *
 * <p>The Camel timer fires every {@code smb.polling-interval-ms} milliseconds.
 * On each tick it delegates to {@link SmbDownloadService#downloadNewFiles()} which
 * uses jcifs-ng to list the remote directory and stream any new ZIP files to the
 * local {@code inputZip} directory.
 *
 * <p>The downloaded files are picked up automatically by
 * {@link ZipExtractionRoute} because that route uses a Camel file consumer on the
 * same directory — no explicit channel or hand-off is needed.
 *
 * <p>Error handling: if the SMB server is unreachable the exception is caught,
 * logged, and the route continues polling on the next interval (non-fatal).
 */
@Component
public class SmbPollingRoute extends RouteBuilder {

    @Autowired
    private SmbProperties smbProperties;

    @Autowired
    private SmbDownloadService smbDownloadService;

    @Override
    public void configure() {

        onException(Exception.class)
                .routeId("smb-poll-error-handler")
                .log(LoggingLevel.ERROR,
                        "SMB polling error (will retry on next interval): ${exception.message}")
                .handled(true);

        from("timer:smbPoller?period=" + smbProperties.getPollingIntervalMs()
                + "&delay=5000")   // 5-second startup delay so all Spring beans are fully ready
                .routeId("smb-polling-route")
                .log(LoggingLevel.INFO, "SMB poll started — checking '${properties:smb.remote-directory}'")
                .bean(smbDownloadService, "downloadNewFiles")
                .choice()
                    .when(body().isNotNull())
                        .log(LoggingLevel.INFO, "Downloaded ${body.size()} file(s) from SMB")
                    .otherwise()
                        .log(LoggingLevel.DEBUG, "No new files on SMB share")
                .end();
    }
}
