package com.example.csvprocessor.route;

import com.example.csvprocessor.config.DirectoryProperties;
import com.example.csvprocessor.config.SftpProperties;
import org.apache.camel.LoggingLevel;
import org.apache.camel.builder.RouteBuilder;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

/**
 * Camel route that uploads completed success CSV files to a remote SFTP server.
 *
 * <p>Files in the {@code outputSuccess} directory are uploaded via the Camel FTP
 * component (SFTP mode, powered by JSch). After a successful upload the local
 * file is moved to the {@code .uploaded} sub-directory so the directory stays
 * clean and re-uploads are prevented.
 *
 * <p><b>Authentication</b>: private-key authentication is used when
 * {@code sftp.private-key-path} is set; otherwise password authentication is
 * used.  In production environments private-key auth with a {@code known_hosts}
 * file is strongly preferred.
 *
 * <p><b>Retry</b>: Camel's built-in error handler retries a failed upload up to
 * 3 times with exponential back-off before moving the file to {@code .uploadError}.
 */
@Component
public class SftpUploadRoute extends RouteBuilder {

    @Autowired
    private SftpProperties sftpProperties;

    @Autowired
    private DirectoryProperties directories;

    @Override
    public void configure() {

        // Retry SFTP errors up to 3 times before giving up
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

    /**
     * Constructs the Camel SFTP endpoint URI from injected properties.
     *
     * <p>URI format:
     * {@code sftp://host:port/path?username=…&privateKeyFile=…&disconnect=true}
     *
     * <p>Note: Camel's {@code ?password=} option is used as a fallback; in a
     * production deployment consider externalising credentials via Vault, a
     * secrets manager, or Spring's encrypted property sources instead of plain
     * application.yml values.
     */
    private String buildSftpUri() {
        StringBuilder uri = new StringBuilder("sftp://")
                .append(sftpProperties.getHost())
                .append(":").append(sftpProperties.getPort())
                .append(normalisePath(sftpProperties.getRemoteDirectory()))
                .append("?username=").append(encode(sftpProperties.getUsername()))
                .append("&disconnect=true")          // close connection after each file
                .append("&binary=true")              // binary transfer mode
                .append("&connectTimeout=").append(sftpProperties.getConnectTimeoutMs());

        // Authentication: prefer private key, fall back to password
        if (sftpProperties.getPrivateKeyPath() != null
                && !sftpProperties.getPrivateKeyPath().isBlank()) {
            uri.append("&privateKeyFile=").append(sftpProperties.getPrivateKeyPath());
            if (sftpProperties.getPrivateKeyPassphrase() != null
                    && !sftpProperties.getPrivateKeyPassphrase().isBlank()) {
                uri.append("&privateKeyPassphrase=RAW(")
                   .append(sftpProperties.getPrivateKeyPassphrase())
                   .append(")");
            }
        } else if (sftpProperties.getPassword() != null) {
            // RAW(...) prevents Camel from interpreting special characters in the password
            uri.append("&password=RAW(").append(sftpProperties.getPassword()).append(")");
        }

        // Known-hosts strict checking
        if (sftpProperties.getKnownHostsFile() != null
                && !sftpProperties.getKnownHostsFile().isBlank()) {
            uri.append("&knownHostsFile=").append(sftpProperties.getKnownHostsFile());
        } else {
            // Disable only when no known_hosts file is configured — NOT for production
            uri.append("&strictHostKeyChecking=no");
        }

        return uri.toString();
    }

    private String normalisePath(String path) {
        return path.startsWith("/") ? path : "/" + path;
    }

    private String encode(String value) {
        // Simple percent-encode for URI username — covers the common special chars
        return value.replace("@", "%40").replace(" ", "%20");
    }
}
