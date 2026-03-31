package com.canet.forwarder.integration;

import org.springframework.amqp.core.AcknowledgeMode;
import org.springframework.amqp.rabbit.connection.CachingConnectionFactory;
import org.springframework.amqp.rabbit.connection.RabbitConnectionFactoryBean;
import org.springframework.amqp.rabbit.listener.SimpleMessageListenerContainer;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.integration.amqp.inbound.AmqpInboundChannelAdapter;
import org.springframework.messaging.MessageChannel;

import com.canet.forwarder.config.ForwarderProperties;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * Spring Integration – RabbitMQ inbound adapter.
 * Active only when {@code source.type=rabbitmq}.
 */
@Slf4j
@Configuration
@RequiredArgsConstructor
@ConditionalOnProperty(prefix = "source", name = "type", havingValue = "rabbitmq")
public class RabbitMqIntegrationConfig {

    private final ForwarderProperties props;
    private final MessageChannel forwarderInputChannel;

    @Bean
    public CachingConnectionFactory rabbitConnectionFactory() throws Exception {
        ForwarderProperties.Rabbitmq rabbit = props.getSource().getRabbitmq();
        ForwarderProperties.Ssl      ssl    = props.getSource().getSsl();

        RabbitConnectionFactoryBean factoryBean = new RabbitConnectionFactoryBean();

        // ── Connection ─────────────────────────────────────────────────────
        factoryBean.setHost(rabbit.getHost());
        factoryBean.setPort(rabbit.getPort());
        factoryBean.setUsername(rabbit.getUsername());
        factoryBean.setPassword(rabbit.getPassword());
        factoryBean.setVirtualHost(rabbit.getVirtualHost());

        // ── Timeouts ───────────────────────────────────────────────────────
        factoryBean.setConnectionTimeout(rabbit.getConnectionTimeoutMs());

        // ── Keep-alive / heartbeat ─────────────────────────────────────────
        factoryBean.setRequestedHeartbeat(rabbit.getRequestedHeartbeat());
        factoryBean.setRequestedChannelMax(rabbit.getRequestedChannelMax());

        // ── SSL / AMQPS ────────────────────────────────────────────────────
        if (ssl.isEnabled()) {
            log.info("RabbitMQ SSL/TLS enabled");
            factoryBean.setUseSSL(true);
            if (ssl.getKeystorePath() != null && !ssl.getKeystorePath().isBlank()) {
                factoryBean.setKeyStore(ssl.getKeystorePath());
                factoryBean.setKeyStorePassphrase(ssl.getKeystorePassword());
                factoryBean.setKeyStoreType(ssl.getKeystoreType());
            }
            if (ssl.getTruststorePath() != null && !ssl.getTruststorePath().isBlank()) {
                factoryBean.setTrustStore(ssl.getTruststorePath());
                factoryBean.setTrustStorePassphrase(ssl.getTruststorePassword());
                factoryBean.setTrustStoreType(ssl.getTruststoreType());
            }
        }

        factoryBean.afterPropertiesSet();

        CachingConnectionFactory ccf = new CachingConnectionFactory(factoryBean.getObject());

        log.info("RabbitMQ connection factory: host={}:{}, vhost={}, queue={}, ssl={}",
                rabbit.getHost(), rabbit.getPort(), rabbit.getVirtualHost(),
                rabbit.getQueue(), ssl.isEnabled());
        return ccf;
    }

    @Bean
    public SimpleMessageListenerContainer rabbitListenerContainer() throws Exception {
        ForwarderProperties.Rabbitmq rabbit = props.getSource().getRabbitmq();

        SimpleMessageListenerContainer container = new SimpleMessageListenerContainer();
        container.setConnectionFactory(rabbitConnectionFactory());
        container.setQueueNames(rabbit.getQueue());
        container.setAcknowledgeMode(AcknowledgeMode.AUTO);

        // ── QoS / concurrency ──────────────────────────────────────────────
        container.setPrefetchCount(rabbit.getPrefetchCount());
        container.setConcurrentConsumers(rabbit.getConcurrentConsumers());
        container.setMaxConcurrentConsumers(rabbit.getMaxConcurrentConsumers());

        // ── Recovery ───────────────────────────────────────────────────────
        container.setRecoveryInterval(rabbit.getRecoveryIntervalMs());

        return container;
    }

    @Bean
    public AmqpInboundChannelAdapter rabbitInboundAdapter() throws Exception {
        AmqpInboundChannelAdapter adapter = new AmqpInboundChannelAdapter(rabbitListenerContainer());
        adapter.setOutputChannel(forwarderInputChannel);
        return adapter;
    }
}
