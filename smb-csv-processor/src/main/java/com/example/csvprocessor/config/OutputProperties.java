package com.example.csvprocessor.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/**
 * Configurable output file naming settings.
 *
 * <p>All values can be overridden in {@code application.properties}:
 * <pre>
 * processing.output.success-prefix=success
 * processing.output.quarantine-prefix=quarantine
 * processing.output.timestamp-format=yyyyMMddHHmmss
 * processing.output.upload-error-retry-enabled=true
 * processing.output.upload-error-retry-interval-ms=900000
 * </pre>
 *
 * <p>Output files are named:  {@code <sourceBaseName>_<prefix>_<timestamp>.csv}
 * e.g. {@code cells_20240101120000_success.csv}
 */
@Component
@ConfigurationProperties(prefix = "processing.output")
public class OutputProperties {

    /**
     * Prefix inserted into the success output filename.
     * Default: {@code success}
     */
    private String successPrefix = "success";

    /**
     * Prefix inserted into the quarantine output filename.
     * Default: {@code quarantine}
     */
    private String quarantinePrefix = "quarantine";

    /**
     * {@link java.text.SimpleDateFormat} pattern used to stamp output filenames.
     * Default: {@code yyyyMMddHHmmss}
     */
    private String timestampFormat = "yyyyMMddHHmmss";

    /**
     * Whether the retry route for {@code .uploadError} files is active.
     * When {@code true}, files that failed SFTP upload are moved back to the
     * success output directory on the configured interval for re-upload.
     * Default: {@code true}
     */
    private boolean uploadErrorRetryEnabled = true;

    /**
     * How often (in milliseconds) the retry route polls the {@code .uploadError}
     * folder for files to move back.
     * Default: 900000 (15 minutes)
     */
    private long uploadErrorRetryIntervalMs = 900_000L;

    // -------------------------------------------------------------------------
    // Getters / Setters
    // -------------------------------------------------------------------------

    public String getSuccessPrefix() { return successPrefix; }
    public void setSuccessPrefix(String successPrefix) { this.successPrefix = successPrefix; }

    public String getQuarantinePrefix() { return quarantinePrefix; }
    public void setQuarantinePrefix(String quarantinePrefix) { this.quarantinePrefix = quarantinePrefix; }

    public String getTimestampFormat() { return timestampFormat; }
    public void setTimestampFormat(String timestampFormat) { this.timestampFormat = timestampFormat; }

    public boolean isUploadErrorRetryEnabled() { return uploadErrorRetryEnabled; }
    public void setUploadErrorRetryEnabled(boolean uploadErrorRetryEnabled) {
        this.uploadErrorRetryEnabled = uploadErrorRetryEnabled;
    }

    public long getUploadErrorRetryIntervalMs() { return uploadErrorRetryIntervalMs; }
    public void setUploadErrorRetryIntervalMs(long uploadErrorRetryIntervalMs) {
        this.uploadErrorRetryIntervalMs = uploadErrorRetryIntervalMs;
    }
}
