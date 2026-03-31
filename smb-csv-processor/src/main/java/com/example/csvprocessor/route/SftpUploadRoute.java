package com.example.csvprocessor.route;

import com.example.csvprocessor.config.DirectoryProperties;
import com.example.csvprocessor.config.SftpProperties;
import org.apache.camel.LoggingLevel;
import org.apache.camel.builder.RouteBuilder;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

/**
 * Camel route that uploads completed success CSV files to a remote SFTP server.
 *
 * <p>This route is <b>disabled</b> when {@code processing.test-mode.enabled=true},
 * so that end-to-end pipeline testing does not require a live SFTP server.
 * Success files remain in the output directory for manual inspection.
 *
 * <p><b>Retry</b>: failed uploads are retried up to 3 times with exponential
 * back-off before the file is moved to {@code .uploadError}.
 */
@Component
@ConditionalOnProperty(name = "processing.test-mode.enabled", havingValue = "false", matchIfMissing = true)
public class SftpUploadRoute extends RouteBuilder {

    @Autowired
    private SftpProperties sftpProperties;

    @Autowired
    private DirectoryProperties directories;

    @Override
    public void configure() {

        onException(Exception.class)
                .maximumRedeliveries(3)
                .redeliveryDelay(10_000)
                .backOffMultiplier(2)
                .log(LoggingLevel.ERROR,
                        "SFTP upload failed for '${file:name}' after retries: ${exception.message}")
                .handled(true);

        from("file:" + directories.getOutputSuccess()
                + "?include=.*_success_.*\\.csv"
                + "&readLock=changed"
                + "&readLockCheckInterval=5000"
                + "&readLockTimeout=600000"
                + "&move=.uploaded"
                + "&moveFailed=.uploadError"
                + "&maxMessagesPerPoll=5")
                .routeId("sftp-upload-route")
                .log(LoggingLevel.INFO, "Uploading to SFTP: ${file:name}")
                .to(buildSftpUri())
                .log(LoggingLevel.INFO, "SFTP upload complete: ${file:name}");
    }

    private String buildSftpUri() {
        StringBuilder uri = new StringBuilder("sftp://")
                .append(sftpProperties.getHost())
                .append(":").append(sftpProperties.getPort())
                .append(normalisePath(sftpProperties.getRemoteDirectory()))
                .append("?username=").append(encode(sftpProperties.getUsername()))
                .append("&disconnect=true")
                .append("&binary=true")
                .append("&connectTimeout=").append(sftpProperties.getConnectTimeoutMs());

        boolean useKeyAuth = sftpProperties.getPrivateKeyPath() != null
                && !sftpProperties.getPrivateKeyPath().isBlank();

        if (useKeyAuth) {
            uri.append("&privateKeyFile=").append(sftpProperties.getPrivateKeyPath());
            if (sftpProperties.getPrivateKeyPassphrase() != null
                    && !sftpProperties.getPrivateKeyPassphrase().isBlank()) {
                uri.append("&privateKeyPassphrase=RAW(")
                   .append(sftpProperties.getPrivateKeyPassphrase()).append(")");
            }
            uri.append("&preferredAuthentications=publickey");
        } else if (sftpProperties.getPassword() != null
                && !sftpProperties.getPassword().isBlank()) {
            uri.append("&password=RAW(").append(sftpProperties.getPassword()).append(")")
               .append("&preferredAuthentications=password");
        }

        if (sftpProperties.getKnownHostsFile() != null
                && !sftpProperties.getKnownHostsFile().isBlank()) {
            uri.append("&knownHostsFile=").append(sftpProperties.getKnownHostsFile());
        } else {
            uri.append("&strictHostKeyChecking=no");
        }

        return uri.toString();
    }

    private String normalisePath(String path) {
        return path.startsWith("/") ? path : "/" + path;
    }

    private String encode(String value) {
        return value.replace("@", "%40").replace(" ", "%20");
    }
}
