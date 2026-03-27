package com.example.csvprocessor.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/**
 * SFTP upload settings.  Bound from the {@code sftp.*} namespace in application.yml.
 *
 * <p>Authentication preference order:
 * <ol>
 *   <li>Private key ({@code private-key-path} + optional {@code private-key-passphrase})</li>
 *   <li>Password ({@code password})</li>
 * </ol>
 * Private-key authentication is strongly preferred in secure environments.
 */
@Component
@ConfigurationProperties(prefix = "sftp")
public class SftpProperties {

    private String host;

    private int port = 22;

    private String username;

    /** Password — used only when {@code privateKeyPath} is not set. */
    private String password;

    /**
     * Absolute path to the private key file on the local filesystem.
     * e.g. {@code /opt/app/.ssh/id_rsa}
     */
    private String privateKeyPath;

    /** Passphrase for the private key (leave blank if the key is unprotected). */
    private String privateKeyPassphrase;

    /**
     * Absolute path to the known_hosts file.
     * Omit (or leave blank) to disable strict host-key checking — NOT recommended
     * for production.
     */
    private String knownHostsFile;

    /**
     * Remote directory where success files are uploaded.
     * Must start with {@code /}.
     */
    private String remoteDirectory;

    /** Connection timeout in milliseconds. */
    private int connectTimeoutMs = 30_000;

    // -------------------------------------------------------------------------
    // Getters / Setters
    // -------------------------------------------------------------------------

    public String getHost() { return host; }
    public void setHost(String host) { this.host = host; }

    public int getPort() { return port; }
    public void setPort(int port) { this.port = port; }

    public String getUsername() { return username; }
    public void setUsername(String username) { this.username = username; }

    public String getPassword() { return password; }
    public void setPassword(String password) { this.password = password; }

    public String getPrivateKeyPath() { return privateKeyPath; }
    public void setPrivateKeyPath(String privateKeyPath) { this.privateKeyPath = privateKeyPath; }

    public String getPrivateKeyPassphrase() { return privateKeyPassphrase; }
    public void setPrivateKeyPassphrase(String privateKeyPassphrase) { this.privateKeyPassphrase = privateKeyPassphrase; }

    public String getKnownHostsFile() { return knownHostsFile; }
    public void setKnownHostsFile(String knownHostsFile) { this.knownHostsFile = knownHostsFile; }

    public String getRemoteDirectory() { return remoteDirectory; }
    public void setRemoteDirectory(String remoteDirectory) { this.remoteDirectory = remoteDirectory; }

    public int getConnectTimeoutMs() { return connectTimeoutMs; }
    public void setConnectTimeoutMs(int connectTimeoutMs) { this.connectTimeoutMs = connectTimeoutMs; }
}
