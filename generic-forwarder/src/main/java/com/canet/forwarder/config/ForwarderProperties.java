package com.canet.forwarder.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.NestedConfigurationProperty;

/**
 * All application configuration, bound from application.properties.
 *
 * Top-level prefixes:
 *   source.*      – inbound source
 *   endpoint.*    – outbound HTTP target
 */
@Data
@ConfigurationProperties
public class ForwarderProperties {

    @NestedConfigurationProperty
    private Source source = new Source();

    @NestedConfigurationProperty
    private Endpoint endpoint = new Endpoint();

    // ─────────────────────────────────────────────────────────
    // Source
    // ─────────────────────────────────────────────────────────

    @Data
    public static class Source {

        /** One of: kafka | rabbitmq | smb | file */
        private String type = "file";

        @NestedConfigurationProperty
        private Ssl ssl = new Ssl();

        @NestedConfigurationProperty
        private Kafka kafka = new Kafka();

        @NestedConfigurationProperty
        private Rabbitmq rabbitmq = new Rabbitmq();

        @NestedConfigurationProperty
        private Smb smb = new Smb();

        @NestedConfigurationProperty
        private FileSource file = new FileSource();
    }

    @Data
    public static class Kafka {
        private String bootstrapServers = "localhost:9092";
        private String topic = "forwarder-input";
        private String groupId = "generic-forwarder";
        /** AUTO_OFFSET_RESET: earliest | latest */
        private String autoOffsetReset = "earliest";
    }

    @Data
    public static class Rabbitmq {
        private String host = "localhost";
        private int port = 5672;
        private String username = "guest";
        private String password = "guest";
        private String virtualHost = "/";
        private String queue = "forwarder-input";
        /** Prefetch count for QoS */
        private int prefetchCount = 10;
    }

    @Data
    public static class Smb {
        private String host;
        private String share;
        /** Remote directory within the share, e.g. /incoming */
        private String directory = "/";
        private String username;
        private String password;
        private String domain = "";
        /** Ant-style pattern applied on the remote file names */
        private String filePattern = "*.*";
        /** Poll interval in milliseconds */
        private long pollDelay = 5000;
        /** Delete remote file after successful read */
        private boolean deleteAfterRead = false;
    }

    @Data
    public static class FileSource {
        /** Local directory to poll */
        private String directory = "/data/incoming";
        /** Regex pattern matched against file names */
        private String filePattern = "(?i).*\\.(log|xml|json|txt|csv|ndjson)$";
        /** Recurse into sub-directories */
        private boolean recursive = false;
        /** Poll interval in milliseconds */
        private long pollDelay = 5000;
        /** Move completed files to this sub-directory (empty = delete) */
        private String doneDirectory = ".done";
    }

    // ─────────────────────────────────────────────────────────
    // Endpoint
    // ─────────────────────────────────────────────────────────

    @Data
    public static class Endpoint {
        private String url = "https://localhost:8443/ingest";
        /** Sent as X-Feed HTTP header */
        private String feed = "default";
        /** Sent as X-Environment HTTP header */
        private String environment = "dev";
        private int connectTimeoutMs = 5_000;
        private int socketTimeoutMs  = 30_000;
        /** Content-Type sent to the endpoint */
        private String contentType = "application/octet-stream";
        /** Maximum retry attempts on transient HTTP errors */
        private int maxRetries = 3;
        /** Base delay between retries (ms) */
        private long retryDelayMs = 2_000;

        @NestedConfigurationProperty
        private Ssl ssl = new Ssl();
    }

    // ─────────────────────────────────────────────────────────
    // SSL / TLS (reused for both input and output)
    // ─────────────────────────────────────────────────────────

    @Data
    public static class Ssl {
        private boolean enabled = false;
        private String keystorePath;
        private String keystorePassword;
        private String keystoreType = "JKS";
        private String truststorePath;
        private String truststorePassword;
        private String truststoreType = "JKS";
    }
}
