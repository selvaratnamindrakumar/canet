package com.example.csvprocessor;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.integration.config.EnableIntegration;
import org.springframework.scheduling.annotation.EnableScheduling;

/**
 * Entry point for the SMB → CSV processing pipeline.
 *
 * <p>Pipeline stages:
 * <ol>
 *   <li>SMB polling — jcifs-ng downloads *.zip files to {@code processing.directories.input-zip}</li>
 *   <li>ZIP extraction — Camel file route extracts *.csv into {@code processing.directories.input-csv}</li>
 *   <li>CSV processing — line-by-line mapping/validation via {@code mapping.yml};
 *       valid rows → success dir, invalid rows → quarantine dir</li>
 *   <li>SFTP upload — Camel FTP route pushes success files to the remote SFTP server</li>
 * </ol>
 */
@SpringBootApplication
@EnableIntegration
@EnableScheduling
public class CsvProcessorApplication {

    public static void main(String[] args) {
        SpringApplication.run(CsvProcessorApplication.class, args);
    }
}
