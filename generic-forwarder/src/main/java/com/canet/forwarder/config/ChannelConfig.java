package com.canet.forwarder.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.integration.channel.QueueChannel;
import org.springframework.messaging.MessageChannel;

/**
 * Defines the single shared Spring Integration channel that all source
 * adapters write to.  {@link com.canet.forwarder.routes.CamelBridge}
 * subscribes via {@code @ServiceActivator} and hands messages to Camel.
 *
 * QueueChannel is used so that source adapters can deposit messages
 * before the @ServiceActivator subscriber is fully registered.
 */
@Configuration
public class ChannelConfig {

    @Bean
    public MessageChannel forwarderInputChannel() {
        return new QueueChannel(1000);   // buffer up to 1 000 messages
    }
}
