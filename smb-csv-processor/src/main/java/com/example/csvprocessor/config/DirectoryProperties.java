package com.example.csvprocessor.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

import jakarta.annotation.PostConstruct;
import java.io.File;

/**
 * Local working-directory paths.  All directories are created at startup if absent.
 * Bound from the {@code processing.directories.*} namespace.
 */
@Component
@ConfigurationProperties(prefix = "processing.directories")
public class DirectoryProperties {

    /** Downloaded ZIP files land here before extraction. */
    private String inputZip = "/data/processor/input/zip";

    /** Extracted CSV files waiting to be processed. */
    private String inputCsv = "/data/processor/input/csv";

    /** Valid (success) output CSV files, ready for SFTP upload. */
    private String outputSuccess = "/data/processor/output/success";

    /** Invalid rows collected here as quarantine files. */
    private String outputQuarantine = "/data/processor/output/quarantine";

    /** Originals moved here after successful processing. */
    private String outputArchive = "/data/processor/output/archive";

    @PostConstruct
    public void ensureDirectoriesExist() {
        for (String path : new String[]{inputZip, inputCsv, outputSuccess, outputQuarantine, outputArchive}) {
            File dir = new File(path);
            if (!dir.exists() && !dir.mkdirs()) {
                throw new IllegalStateException("Cannot create directory: " + path);
            }
        }
    }

    // -------------------------------------------------------------------------
    // Getters / Setters
    // -------------------------------------------------------------------------

    public String getInputZip() { return inputZip; }
    public void setInputZip(String inputZip) { this.inputZip = inputZip; }

    public String getInputCsv() { return inputCsv; }
    public void setInputCsv(String inputCsv) { this.inputCsv = inputCsv; }

    public String getOutputSuccess() { return outputSuccess; }
    public void setOutputSuccess(String outputSuccess) { this.outputSuccess = outputSuccess; }

    public String getOutputQuarantine() { return outputQuarantine; }
    public void setOutputQuarantine(String outputQuarantine) { this.outputQuarantine = outputQuarantine; }

    public String getOutputArchive() { return outputArchive; }
    public void setOutputArchive(String outputArchive) { this.outputArchive = outputArchive; }
}
