package com.example.csvprocessor.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/**
 * Configuration for the modular test mode that bypasses SMB and SFTP.
 *
 * <p>When {@code processing.test-mode.enabled=true}:
 * <ul>
 *   <li>The SMB polling route is disabled (no SMB server required)</li>
 *   <li>The SFTP upload route is disabled (no SFTP server required)</li>
 *   <li>A test drop route watches {@code drop-directory} for CSV or ZIP files
 *       and copies them into the processing pipeline automatically</li>
 * </ul>
 *
 * <p>To test: drop a {@code .csv} or {@code .zip} file into the configured
 * {@code drop-directory} and observe the success/quarantine output files.
 */
@Component
@ConfigurationProperties(prefix = "processing.test-mode")
public class TestModeProperties {

    /**
     * Set {@code true} to enable test mode (disables SMB poll and SFTP upload).
     */
    private boolean enabled = false;

    /**
     * Directory watched for test input files when {@code enabled=true}.
     * Drop a {@code .csv} or {@code .zip} here to inject files into the pipeline.
     */
    private String dropDirectory = "/data/processor/test-drop";

    // -------------------------------------------------------------------------
    // Getters / Setters
    // -------------------------------------------------------------------------

    public boolean isEnabled() { return enabled; }
    public void setEnabled(boolean enabled) { this.enabled = enabled; }

    public String getDropDirectory() { return dropDirectory; }
    public void setDropDirectory(String dropDirectory) { this.dropDirectory = dropDirectory; }
}
