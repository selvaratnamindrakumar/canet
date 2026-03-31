package com.example.csvprocessor.service;

import com.example.csvprocessor.config.DirectoryProperties;
import com.example.csvprocessor.config.SmbProperties;
import jcifs.CIFSContext;
import jcifs.config.PropertyConfiguration;
import jcifs.context.BaseContext;
import jcifs.smb.NtlmPasswordAuthenticator;
import jcifs.smb.SmbException;
import jcifs.smb.SmbFile;
import jcifs.smb.SmbFilenameFilter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.List;
import java.util.Properties;

/**
 * Downloads ZIP files from an SMB share using jcifs-ng (SMB2/SMB3).
 *
 * <p>Spring Integration pattern: this service acts as a custom inbound adapter.
 * The {@link com.example.csvprocessor.route.SmbPollingRoute} invokes
 * {@link #downloadNewFiles()} on a scheduled timer; downloaded files are placed
 * in the local {@code inputZip} directory, which triggers the Camel file consumer
 * in {@link com.example.csvprocessor.route.ZipExtractionRoute}.
 *
 * <p><b>Large-file strategy</b>: files are streamed in 64 KB chunks directly to
 * disk — no heap accumulation regardless of file size.  A {@code .tmp} suffix is
 * used during download and renamed atomically on completion to avoid the Camel
 * consumer picking up incomplete files.
 *
 * <p><b>Upgrade path</b>: Spring Integration 6.x (Spring Boot 3.x) ships an
 * official {@code spring-integration-smb} module with {@code SmbInboundFileSynchronizer}.
 * When upgrading, replace this service with that module's configuration class.
 */
@Service
public class SmbDownloadService {

    private static final Logger log = LoggerFactory.getLogger(SmbDownloadService.class);
    private static final int BUFFER_SIZE = 65_536; // 64 KB

    @Autowired
    private SmbProperties smbProperties;

    @Autowired
    private DirectoryProperties directoryProperties;

    /** Shared authenticated CIFS context — thread-safe and reused across polls. */
    private CIFSContext cifsContext;

    @PostConstruct
    public void init() throws Exception {
        Properties props = new Properties();
        props.setProperty("jcifs.smb.client.minVersion", smbProperties.getMinSmbVersion());
        props.setProperty("jcifs.smb.client.maxVersion", smbProperties.getMaxSmbVersion());
        props.setProperty("jcifs.smb.client.connTimeout",
                String.valueOf(smbProperties.getConnectTimeoutMs()));
        props.setProperty("jcifs.smb.client.responseTimeout",
                String.valueOf(smbProperties.getResponseTimeoutMs()));
        // Disable NetBIOS name resolution; use direct TCP (port 445) only
        props.setProperty("jcifs.resolveOrder", "DNS");
        props.setProperty("jcifs.smb.client.dfs.disabled", "false");

        BaseContext baseCtx = new BaseContext(new PropertyConfiguration(props));

        NtlmPasswordAuthenticator auth = new NtlmPasswordAuthenticator(
                smbProperties.getDomain(),
                smbProperties.getUsername(),
                smbProperties.getPassword());

        cifsContext = baseCtx.withCredentials(auth);
        log.info("SMB context initialised for host '{}' share '{}'",
                smbProperties.getHost(), smbProperties.getShareName());
    }

    @PreDestroy
    public void shutdown() {
        if (cifsContext != null) {
            try {
                cifsContext.close();
            } catch (Exception e) {
                log.warn("Error closing CIFS context: {}", e.getMessage());
            }
        }
    }

    /**
     * Lists the remote SMB directory and downloads any ZIP file not yet present
     * locally.  Already-downloaded files (matched by name) are skipped to support
     * idempotent polling.
     *
     * @return list of absolute paths of newly downloaded local files
     */
    public List<String> downloadNewFiles() {
        List<String> downloaded = new ArrayList<>();
        String smbUrl = smbProperties.buildSmbUrl();

        log.debug("Polling SMB share: {}", smbUrl);
        try (SmbFile remoteDir = new SmbFile(smbUrl, cifsContext)) {

            SmbFilenameFilter filter = buildFilter(smbProperties.getFilePattern());
            SmbFile[] remoteFiles = remoteDir.listFiles(filter);

            if (remoteFiles == null || remoteFiles.length == 0) {
                log.debug("No matching files found in {}", smbUrl);
                return downloaded;
            }

            for (SmbFile remoteFile : remoteFiles) {
                try {
                    String localPath = downloadIfAbsent(remoteFile);
                    if (localPath != null) {
                        downloaded.add(localPath);
                    }
                } catch (IOException e) {
                    log.error("Failed to download '{}': {}", remoteFile.getName(), e.getMessage(), e);
                }
            }

        } catch (SmbException e) {
            log.error("SMB access error for '{}': {}", smbUrl, e.getMessage(), e);
        } catch (Exception e) {
            log.error("Unexpected error during SMB poll: {}", e.getMessage(), e);
        }

        if (!downloaded.isEmpty()) {
            log.info("Downloaded {} new file(s) from SMB: {}", downloaded.size(), downloaded);
        }
        return downloaded;
    }

    /**
     * Downloads {@code remoteFile} to the local inputZip directory only if a file
     * with the same name does not already exist.
     *
     * @return local file path, or {@code null} if skipped
     */
    private String downloadIfAbsent(SmbFile remoteFile) throws IOException {
        String fileName = remoteFile.getName();
        File localFinal = new File(directoryProperties.getInputZip(), fileName);

        if (localFinal.exists()) {
            log.debug("Skipping already-present file: {}", fileName);
            return null;
        }

        File localTmp = new File(directoryProperties.getInputZip(), fileName + ".tmp");
        log.info("Downloading '{}' ({} bytes) → {}", fileName, remoteFile.length(), localTmp);

        try (InputStream is = new BufferedInputStream(remoteFile.getInputStream(), BUFFER_SIZE);
             FileOutputStream fos = new FileOutputStream(localTmp);
             BufferedOutputStream bos = new BufferedOutputStream(fos, BUFFER_SIZE)) {

            byte[] buf = new byte[BUFFER_SIZE];
            long total = 0;
            int read;
            while ((read = is.read(buf)) != -1) {
                bos.write(buf, 0, read);
                total += read;
                if (total % (100L * 1024 * 1024) == 0) {
                    log.debug("  ... {} MB downloaded for '{}'", total / (1024 * 1024), fileName);
                }
            }
        }

        // Atomic rename — Camel file consumer will not see a partial file
        Files.move(localTmp.toPath(), localFinal.toPath(), StandardCopyOption.ATOMIC_MOVE);
        log.info("Download complete: {} ({} bytes)", localFinal.getAbsolutePath(), localFinal.length());
        return localFinal.getAbsolutePath();
    }

    // -------------------------------------------------------------------------
    // Helpers
    // -------------------------------------------------------------------------

    /**
     * Converts a simple glob pattern (only {@code *} wildcard) into an
     * {@link SmbFilenameFilter}.
     */
    private SmbFilenameFilter buildFilter(String pattern) {
        // Convert simple glob to regex: escape dots, replace * with .*
        final String regex = "(?i)" + pattern.replace(".", "\\.").replace("*", ".*");
        return (dir, name) -> name.matches(regex);
    }
}
