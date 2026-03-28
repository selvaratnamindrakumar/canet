package com.canet.forwarder.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.integration.channel.DirectChannel;
import org.springframework.messaging.MessageChannel;

/**
 * Defines the single shared Spring Integration channel that all source
 * adapters write to.  The Camel route reads from this channel via the
 * camel-spring-integration component.
 */
@Configuration
public class ChannelConfig {

    /**
     * Bean name must match the URI used in {@code ForwarderRoute}:
     * {@code spring-integration:forwarderInputChannel}
     */
    @Bean
    public MessageChannel forwarderInputChannel() {
        return new DirectChannel();
    }
}
