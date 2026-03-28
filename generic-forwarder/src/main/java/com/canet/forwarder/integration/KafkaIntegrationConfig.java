package com.canet.forwarder.integration;

import java.util.HashMap;
import java.util.Map;

import org.apache.kafka.clients.CommonClientConfigs;
import org.apache.kafka.clients.consumer.ConsumerConfig;
import org.apache.kafka.common.config.SaslConfigs;
import org.apache.kafka.common.config.SslConfigs;
import org.apache.kafka.common.serialization.ByteArrayDeserializer;
import org.apache.kafka.common.serialization.StringDeserializer;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.integration.kafka.inbound.KafkaMessageDrivenChannelAdapter;
import org.springframework.kafka.core.ConsumerFactory;
import org.springframework.kafka.core.DefaultKafkaConsumerFactory;
import org.springframework.kafka.listener.ContainerProperties;
import org.springframework.kafka.listener.KafkaMessageListenerContainer;
import org.springframework.messaging.MessageChannel;

import com.canet.forwarder.config.ForwarderProperties;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * Spring Integration – Kafka inbound adapter.
 * Active only when {@code source.type=kafka}.
 */
@Slf4j
@Configuration
@RequiredArgsConstructor
@ConditionalOnProperty(prefix = "source", name = "type", havingValue = "kafka")
public class KafkaIntegrationConfig {

    private final ForwarderProperties props;
    private final MessageChannel forwarderInputChannel;

    @Bean
    public ConsumerFactory<String, byte[]> kafkaConsumerFactory() {
        ForwarderProperties.Kafka kafka = props.getSource().getKafka();
        ForwarderProperties.Ssl   ssl   = props.getSource().getSsl();

        Map<String, Object> config = new HashMap<>();

        // ── Connection ────────────────────────────────────────────────────────
        config.put(ConsumerConfig.BOOTSTRAP_SERVERS_CONFIG,      kafka.getBootstrapServers());
        config.put(ConsumerConfig.GROUP_ID_CONFIG,               kafka.getGroupId());
        config.put(ConsumerConfig.REQUEST_TIMEOUT_MS_CONFIG,     kafka.getRequestTimeoutMs());
        config.put(ConsumerConfig.CONNECTIONS_MAX_IDLE_MS_CONFIG, kafka.getConnectionMaxIdleMs());
        config.put(ConsumerConfig.RECONNECT_BACKOFF_MS_CONFIG,   kafka.getReconnectBackoffMs());
        config.put(ConsumerConfig.RECONNECT_BACKOFF_MAX_MS_CONFIG, kafka.getReconnectBackoffMaxMs());

        // ── Consumer behaviour ────────────────────────────────────────────────
        config.put(ConsumerConfig.AUTO_OFFSET_RESET_CONFIG,      kafka.getAutoOffsetReset());
        config.put(ConsumerConfig.ENABLE_AUTO_COMMIT_CONFIG,     false);
        config.put(ConsumerConfig.MAX_POLL_RECORDS_CONFIG,       kafka.getMaxPollRecords());
        config.put(ConsumerConfig.MAX_POLL_INTERVAL_MS_CONFIG,   kafka.getMaxPollIntervalMs());
        config.put(ConsumerConfig.SESSION_TIMEOUT_MS_CONFIG,     kafka.getSessionTimeoutMs());
        config.put(ConsumerConfig.HEARTBEAT_INTERVAL_MS_CONFIG,  kafka.getHeartbeatIntervalMs());

        // ── Fetch tuning ──────────────────────────────────────────────────────
        config.put(ConsumerConfig.FETCH_MIN_BYTES_CONFIG,        kafka.getFetchMinBytes());
        config.put(ConsumerConfig.FETCH_MAX_WAIT_MS_CONFIG,      kafka.getFetchMaxWaitMs());

        // ── Deserializers ─────────────────────────────────────────────────────
        config.put(ConsumerConfig.KEY_DESERIALIZER_CLASS_CONFIG,   StringDeserializer.class);
        config.put(ConsumerConfig.VALUE_DESERIALIZER_CLASS_CONFIG, ByteArrayDeserializer.class);

        // ── Security protocol ─────────────────────────────────────────────────
        String protocol = kafka.getSecurityProtocol();
        config.put(CommonClientConfigs.SECURITY_PROTOCOL_CONFIG, protocol);

        if (protocol.contains("SASL")) {
            log.info("Kafka SASL enabled, mechanism={}", kafka.getSaslMechanism());
            config.put(SaslConfigs.SASL_MECHANISM, kafka.getSaslMechanism());
            if (kafka.getSaslJaasConfig() != null && !kafka.getSaslJaasConfig().isBlank()) {
                config.put(SaslConfigs.SASL_JAAS_CONFIG, kafka.getSaslJaasConfig());
            }
        }

        if (protocol.contains("SSL") || ssl.isEnabled()) {
            log.info("Kafka SSL/TLS enabled");
            config.put(CommonClientConfigs.SECURITY_PROTOCOL_CONFIG,
                    protocol.contains("SASL") ? "SASL_SSL" : "SSL");
            config.put(SslConfigs.SSL_KEYSTORE_LOCATION_CONFIG,   ssl.getKeystorePath());
            config.put(SslConfigs.SSL_KEYSTORE_PASSWORD_CONFIG,   ssl.getKeystorePassword());
            config.put(SslConfigs.SSL_KEYSTORE_TYPE_CONFIG,       ssl.getKeystoreType());
            config.put(SslConfigs.SSL_TRUSTSTORE_LOCATION_CONFIG, ssl.getTruststorePath());
            config.put(SslConfigs.SSL_TRUSTSTORE_PASSWORD_CONFIG, ssl.getTruststorePassword());
            config.put(SslConfigs.SSL_TRUSTSTORE_TYPE_CONFIG,     ssl.getTruststoreType());
        }

        log.info("Kafka consumer factory: servers={}, topic={}, group={}, protocol={}",
                kafka.getBootstrapServers(), kafka.getTopic(), kafka.getGroupId(), protocol);

        return new DefaultKafkaConsumerFactory<>(config);
    }

    @Bean
    public KafkaMessageListenerContainer<String, byte[]> kafkaListenerContainer() {
        String topic = props.getSource().getKafka().getTopic();
        ContainerProperties containerProps = new ContainerProperties(topic);
        containerProps.setAckMode(ContainerProperties.AckMode.RECORD);
        return new KafkaMessageListenerContainer<>(kafkaConsumerFactory(), containerProps);
    }

    @Bean
    public KafkaMessageDrivenChannelAdapter<String, byte[]> kafkaInboundAdapter() {
        KafkaMessageDrivenChannelAdapter<String, byte[]> adapter =
                new KafkaMessageDrivenChannelAdapter<>(kafkaListenerContainer());
        adapter.setOutputChannel(forwarderInputChannel);
        return adapter;
    }
}
