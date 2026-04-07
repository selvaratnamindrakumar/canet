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

    // ─────────────────────────────────────────────────────────
    // Kafka
    // ─────────────────────────────────────────────────────────

    @Data
    public static class Kafka {

        // ── Connection ────────────────────────────────────────
        /** Comma-separated list of host:port pairs */
        private String bootstrapServers = "localhost:9092";
        /** Topic to consume from */
        private String topic = "forwarder-input";
        /** Consumer group ID */
        private String groupId = "generic-forwarder";

        // ── Consumer behaviour ────────────────────────────────
        /** earliest | latest | none */
        private String autoOffsetReset = "earliest";
        /** Max records returned per poll() call */
        private int maxPollRecords = 500;
        /** Max time (ms) between two consecutive poll() calls before the broker
         *  considers the consumer dead */
        private int maxPollIntervalMs = 300_000;
        /** Timeout (ms) for detecting consumer failures via heartbeats */
        private int sessionTimeoutMs = 30_000;
        /** How often (ms) the consumer sends heartbeats to the broker */
        private int heartbeatIntervalMs = 10_000;

        // ── Network / timeouts ────────────────────────────────
        /** Timeout (ms) for client requests to the broker */
        private int requestTimeoutMs = 30_000;
        /** Close idle connections after this many ms */
        private long connectionMaxIdleMs = 540_000;
        /** Base back-off (ms) between reconnect attempts */
        private long reconnectBackoffMs = 50;
        /** Maximum back-off (ms) between reconnect attempts */
        private long reconnectBackoffMaxMs = 1_000;

        // ── Fetch tuning ──────────────────────────────────────
        /** Minimum data (bytes) the broker should return per fetch */
        private int fetchMinBytes = 1;
        /** Max time (ms) the broker waits to accumulate fetch.min.bytes */
        private int fetchMaxWaitMs = 500;

        // ── Security ──────────────────────────────────────────
        /**
         * Kafka security protocol.
         * One of: PLAINTEXT | SSL | SASL_PLAINTEXT | SASL_SSL
         */
        private String securityProtocol = "PLAINTEXT";

        /**
         * SASL mechanism when securityProtocol is SASL_PLAINTEXT or SASL_SSL.
         * Common values: PLAIN | SCRAM-SHA-256 | SCRAM-SHA-512 | GSSAPI
         */
        private String saslMechanism = "PLAIN";

        /**
         * Full JAAS config string, e.g.:
         *   org.apache.kafka.common.security.plain.PlainLoginModule required
         *   username="user" password="pass";
         */
        private String saslJaasConfig;
    }

    // ─────────────────────────────────────────────────────────
    // RabbitMQ
    // ─────────────────────────────────────────────────────────

    @Data
    public static class Rabbitmq {

        // ── Connection ────────────────────────────────────────
        private String host = "localhost";
        /** Plain AMQP port. Use 5671 when SSL is enabled (AMQPS). */
        private int port = 5672;
        private String username = "guest";
        private String password = "guest";
        private String virtualHost = "/";
        /** Queue to consume from */
        private String queue = "forwarder-input";

        // ── Keep-alive / heartbeat ────────────────────────────
        /** AMQP heartbeat interval in seconds (0 = disabled) */
        private int requestedHeartbeat = 60;
        /** Maximum number of channels per connection (0 = broker limit) */
        private int requestedChannelMax = 0;

        // ── Connection timeouts ───────────────────────────────
        /** TCP connection timeout in ms (0 = infinite) */
        private int connectionTimeoutMs = 10_000;
        // ── Consumer concurrency ──────────────────────────────
        /** Prefetch (QoS): max unacknowledged messages per consumer */
        private int prefetchCount = 10;
        /** Minimum number of listener threads */
        private int concurrentConsumers = 1;
        /** Maximum number of listener threads (scales up under load) */
        private int maxConcurrentConsumers = 5;

        // ── Reliability ───────────────────────────────────────
        /** Interval (ms) between automatic connection recovery attempts */
        private long recoveryIntervalMs = 5_000;
    }

    // ─────────────────────────────────────────────────────────
    // SMB
    // ─────────────────────────────────────────────────────────

    @Data
    public static class Smb {

        // ── Connection ────────────────────────────────────────
        private String host;
        /** SMB/CIFS port (default 445; use 139 for legacy NetBIOS) */
        private int port = 445;
        /** Share name, e.g. myshare */
        private String share;
        /** Remote directory within the share, e.g. /incoming */
        private String directory = "/";
        private String username;
        private String password;
        /** Windows domain or workgroup name */
        private String domain = "";

        // ── Protocol ──────────────────────────────────────────
        /** Minimum SMB protocol version: SMB1 | SMB2 | SMB3 */
        private String minVersion = "SMB2";
        /** Maximum SMB protocol version: SMB1 | SMB2 | SMB3 */
        private String maxVersion = "SMB3";
        // ── Polling / file handling ───────────────────────────
        /** Ant-style pattern applied on remote file names */
        private String filePattern = "*.*";
        /** Poll interval in milliseconds */
        private long pollDelay = 5_000;
        /** Max files to synchronise per poll cycle (0 = unlimited) */
        private int maxFilesPerPoll = 0;
        /** Delete remote file after successful read */
        private boolean deleteAfterRead = false;
        /** Local staging directory for synchronised files */
        private String localStagingDirectory = "";   // empty → java.io.tmpdir/smb-staging
    }

    // ─────────────────────────────────────────────────────────
    // File source
    // ─────────────────────────────────────────────────────────

    @Data
    public static class FileSource {

        // ── Location ──────────────────────────────────────────
        /** Local directory to poll */
        private String directory = "/data/incoming";
        /** Java regex pattern matched against file names */
        private String filePattern = "(?i).*\\.(log|xml|json|txt|csv|ndjson)$";
        /** Recurse into sub-directories */
        private boolean recursive = false;

        // ── Polling ───────────────────────────────────────────
        /** Poll interval in milliseconds */
        private long pollDelay = 5_000;
        /** Max files to hand off per poll cycle (0 = unlimited) */
        private int maxFilesPerPoll = 0;

        // ── Post-processing ───────────────────────────────────
        /**
         * Sub-directory (relative to {@code directory}) where processed files
         * are moved.  Empty string or omit to delete instead.
         */
        private String doneDirectory = ".done";

        // ── Size guard ────────────────────────────────────────
        /**
         * Skip files larger than this threshold in bytes.
         * 0 = no limit.
         */
        private long maxFileSizeBytes = 0;
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
    // SSL / TLS  (reused for both input and output)
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
        /**
         * DEV ONLY — skip all certificate and hostname validation.
         * Never set this in production. Activate via the "dev" Spring profile
         * or explicitly with endpoint.ssl.dev-trust-all=true.
         */
        private boolean devTrustAll = false;
    }
}
