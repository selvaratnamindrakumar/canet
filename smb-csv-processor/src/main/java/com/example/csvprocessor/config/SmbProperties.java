package com.example.csvprocessor.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/**
 * SMB connection settings.  Bound from the {@code smb.*} namespace in application.yml.
 */
@Component
@ConfigurationProperties(prefix = "smb")
public class SmbProperties {

    /** Hostname or IP of the SMB / CIFS server. */
    private String host;

    /** SMB port (default 445). */
    private int port = 445;

    /** Windows domain / workgroup.  Leave blank for local accounts. */
    private String domain = "";

    /** SMB username. */
    private String username;

    /** SMB password. */
    private String password;

    /**
     * Share name, e.g. {@code shared} (do NOT include leading/trailing slashes).
     */
    private String shareName;

    /**
     * Remote directory path inside the share, e.g. {@code /uploads/daily}.
     * Must start with {@code /}.
     */
    private String remoteDirectory;

    /**
     * Glob-style file pattern for files to download (e.g. {@code *.zip}).
     * Evaluated locally after listing the remote directory.
     */
    private String filePattern = "*.zip";

    /**
     * How often (ms) to poll the SMB share.  Default 5 minutes.
     */
    private long pollingIntervalMs = 300_000L;

    /**
     * Minimum SMB protocol version sent during negotiation.
     * Accepted values match jcifs.DialectVersion enum: SMB1, SMB202, SMB210,
     * SMB300, SMB302, SMB311.
     * SMB202 is the minimum for SMB 2.x — do NOT use the old alias "SMB2".
     */
    private String minSmbVersion = "SMB202";

    /** Maximum SMB protocol version. */
    private String maxSmbVersion = "SMB311";

    /** Socket connection timeout in milliseconds. */
    private int connectTimeoutMs = 30_000;

    /** Read/response timeout in milliseconds. */
    private int responseTimeoutMs = 60_000;

    // -------------------------------------------------------------------------
    // Derived helpers
    // -------------------------------------------------------------------------

    /**
     * Builds the base smb:// URL for the remote directory.
     * Format: {@code smb://host/shareName/remoteDirectory/}
     */
    public String buildSmbUrl() {
        String dir = remoteDirectory.startsWith("/") ? remoteDirectory : "/" + remoteDirectory;
        if (!dir.endsWith("/")) dir = dir + "/";
        return "smb://" + host + "/" + shareName + dir;
    }

    // -------------------------------------------------------------------------
    // Getters / Setters
    // -------------------------------------------------------------------------

    public String getHost() { return host; }
    public void setHost(String host) { this.host = host; }

    public int getPort() { return port; }
    public void setPort(int port) { this.port = port; }

    public String getDomain() { return domain; }
    public void setDomain(String domain) { this.domain = domain; }

    public String getUsername() { return username; }
    public void setUsername(String username) { this.username = username; }

    public String getPassword() { return password; }
    public void setPassword(String password) { this.password = password; }

    public String getShareName() { return shareName; }
    public void setShareName(String shareName) { this.shareName = shareName; }

    public String getRemoteDirectory() { return remoteDirectory; }
    public void setRemoteDirectory(String remoteDirectory) { this.remoteDirectory = remoteDirectory; }

    public String getFilePattern() { return filePattern; }
    public void setFilePattern(String filePattern) { this.filePattern = filePattern; }

    public long getPollingIntervalMs() { return pollingIntervalMs; }
    public void setPollingIntervalMs(long pollingIntervalMs) { this.pollingIntervalMs = pollingIntervalMs; }

    public String getMinSmbVersion() { return minSmbVersion; }
    public void setMinSmbVersion(String minSmbVersion) { this.minSmbVersion = minSmbVersion; }

    public String getMaxSmbVersion() { return maxSmbVersion; }
    public void setMaxSmbVersion(String maxSmbVersion) { this.maxSmbVersion = maxSmbVersion; }

    public int getConnectTimeoutMs() { return connectTimeoutMs; }
    public void setConnectTimeoutMs(int connectTimeoutMs) { this.connectTimeoutMs = connectTimeoutMs; }

    public int getResponseTimeoutMs() { return responseTimeoutMs; }
    public void setResponseTimeoutMs(int responseTimeoutMs) { this.responseTimeoutMs = responseTimeoutMs; }
}
