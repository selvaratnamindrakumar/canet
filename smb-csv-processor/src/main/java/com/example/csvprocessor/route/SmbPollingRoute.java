package com.example.csvprocessor.route;

import com.example.csvprocessor.config.SmbProperties;
import com.example.csvprocessor.service.SmbDownloadService;
import org.apache.camel.LoggingLevel;
import org.apache.camel.builder.RouteBuilder;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

/**
 * Camel route that periodically polls the SMB share and triggers file download.
 *
 * <p>This route is <b>disabled</b> when {@code processing.test-mode.enabled=true},
 * allowing the pipeline to be tested without an SMB server.  In test mode, files
 * are injected via {@link TestDropRoute} instead.
 *
 * <p>Error handling: SMB connectivity errors are caught, logged, and the route
 * continues polling on the next interval (non-fatal).
 */
@Component
@ConditionalOnProperty(name = "processing.test-mode.enabled", havingValue = "false", matchIfMissing = true)
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

        from("timer:smbPoller?period=" + smbProperties.getPollingIntervalMs() + "&delay=5000")
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
