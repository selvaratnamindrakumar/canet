package com.example.csvprocessor.service;

import com.example.csvprocessor.config.DirectoryProperties;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

/**
 * Extracts CSV files from a (potentially very large) ZIP archive using streaming.
 *
 * <p><b>Memory strategy</b>: entries are processed one at a time via
 * {@link ZipInputStream}; at no point is more than one 64 KB I/O buffer held
 * in memory.  The entire archive can be several gigabytes — this service will
 * not run out of heap.
 *
 * <p>Only {@code *.csv} entries are extracted; all other entries are skipped.
 * Path-traversal attacks ({@code ../}) in entry names are rejected.
 *
 * <p>Each extracted file is first written with a {@code .tmp} suffix and renamed
 * atomically on completion so the Camel consumer in
 * {@link com.example.csvprocessor.route.CsvProcessingRoute} never picks up an
 * incomplete file.
 */
@Service
public class ZipExtractorService {

    private static final Logger log = LoggerFactory.getLogger(ZipExtractorService.class);
    private static final int BUFFER_SIZE = 65_536; // 64 KB

    @Autowired
    private DirectoryProperties directoryProperties;

    /**
     * Extracts all CSV entries from {@code zipFile} into the configured
     * {@code inputCsv} directory.
     *
     * @param zipFile the source ZIP archive
     * @return list of absolute paths of extracted CSV files
     * @throws IOException on I/O errors
     */
    public List<String> extractZip(File zipFile) throws IOException {
        List<String> extracted = new ArrayList<>();
        File outputDir = new File(directoryProperties.getInputCsv());

        log.info("Extracting '{}' → {}", zipFile.getName(), outputDir.getAbsolutePath());

        try (FileInputStream fis = new FileInputStream(zipFile);
             BufferedInputStream bis = new BufferedInputStream(fis, BUFFER_SIZE);
             ZipInputStream zis = new ZipInputStream(bis)) {

            ZipEntry entry;
            while ((entry = zis.getNextEntry()) != null) {
                try {
                    if (entry.isDirectory()) {
                        continue;
                    }

                    String safeName = sanitiseName(entry.getName());
                    if (safeName == null) {
                        log.warn("Skipping potentially unsafe ZIP entry: '{}'", entry.getName());
                        continue;
                    }

                    if (!safeName.toLowerCase().endsWith(".csv")) {
                        log.debug("Skipping non-CSV entry: {}", safeName);
                        continue;
                    }

                    File tmpFile = new File(outputDir, safeName + ".tmp");
                    File finalFile = new File(outputDir, safeName);

                    extractEntry(zis, tmpFile);

                    // Atomic rename — prevents Camel from consuming a partial file
                    if (!tmpFile.renameTo(finalFile)) {
                        // renameTo can fail across file-systems; fall back to Files.move
                        java.nio.file.Files.move(tmpFile.toPath(), finalFile.toPath(),
                                java.nio.file.StandardCopyOption.REPLACE_EXISTING);
                    }

                    extracted.add(finalFile.getAbsolutePath());
                    log.info("Extracted: {} ({} bytes)", safeName, finalFile.length());

                } finally {
                    zis.closeEntry();
                }
            }
        }

        log.info("Extraction complete — {} CSV file(s) extracted from '{}'",
                extracted.size(), zipFile.getName());
        return extracted;
    }

    private void extractEntry(ZipInputStream zis, File outputFile) throws IOException {
        outputFile.getParentFile().mkdirs();
        try (FileOutputStream fos = new FileOutputStream(outputFile);
             BufferedOutputStream bos = new BufferedOutputStream(fos, BUFFER_SIZE)) {
            byte[] buf = new byte[BUFFER_SIZE];
            int read;
            long total = 0;
            while ((read = zis.read(buf)) != -1) {
                bos.write(buf, 0, read);
                total += read;
                if (total % (500L * 1024 * 1024) == 0) {
                    log.debug("  ... {} MB written for '{}'", total / (1024 * 1024), outputFile.getName());
                }
            }
        }
    }

    /**
     * Returns just the filename (no directory components) after verifying there is
     * no path-traversal attempt.  Returns {@code null} if the entry name is unsafe.
     */
    private String sanitiseName(String entryName) {
        Path entryPath = Paths.get(entryName).normalize();
        // Reject absolute paths or any traversal component
        if (entryPath.isAbsolute() || entryPath.startsWith("..")) {
            return null;
        }
        String fileName = entryPath.getFileName() != null
                ? entryPath.getFileName().toString()
                : entryName;
        // Basic sanity: no null bytes
        if (fileName.contains("\0")) {
            return null;
        }
        return fileName;
    }
}
