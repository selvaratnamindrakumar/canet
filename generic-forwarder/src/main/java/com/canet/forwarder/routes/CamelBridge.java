package com.canet.forwarder.routes;

import java.io.File;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Map;

import org.apache.camel.ProducerTemplate;
import org.springframework.integration.annotation.ServiceActivator;
import org.springframework.messaging.Message;
import org.springframework.stereotype.Component;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * Bridges Spring Integration → Apache Camel without any extra artifact.
 *
 * The {@code @ServiceActivator} subscribes to {@code forwarderInputChannel}
 * (populated by whichever source adapter is active) and hands each message
 * to the Camel route via {@code ProducerTemplate}.
 *
 * The Camel route starts at {@code direct:forwarderInput}.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class CamelBridge {

    private final ProducerTemplate producerTemplate;

    @ServiceActivator(inputChannel = "forwarderInputChannel")
    public void forward(Message<?> message) {
        Object payload = message.getPayload();

        // Promote useful Spring Integration headers into Camel exchange headers
        Map<String, Object> headers = new HashMap<>();
        message.getHeaders().forEach((k, v) -> {
            if (!"id".equals(k) && !"timestamp".equals(k)) {
                headers.put("SI_" + k, v);
            }
        });

        // File payloads: keep as java.io.File so Camel can stream them
        if (payload instanceof File file) {
            headers.put("SI_filename", file.getName());
            producerTemplate.sendBodyAndHeaders("direct:forwarderInput", file, headers);
        } else {
            producerTemplate.sendBodyAndHeaders("direct:forwarderInput", toBytes(payload), headers);
        }
    }

    private static byte[] toBytes(Object value) {
        if (value instanceof byte[] b) return b;
        if (value instanceof String s)  return s.getBytes(StandardCharsets.UTF_8);
        return value.toString().getBytes(StandardCharsets.UTF_8);
    }
}
