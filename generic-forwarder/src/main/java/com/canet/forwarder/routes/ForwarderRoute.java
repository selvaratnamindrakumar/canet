package com.canet.forwarder.routes;

import java.io.File;
import java.nio.charset.StandardCharsets;

import org.apache.camel.Exchange;
import org.apache.camel.LoggingLevel;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.component.http.HttpComponent;
import org.apache.camel.support.jsse.SSLContextParameters;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Component;

import com.canet.forwarder.config.ForwarderProperties;

import lombok.extern.slf4j.Slf4j;

/**
 * Main Camel route.
 *
 * Flow:
 *   spring-integration:forwarderInputChannel
 *     → normalise payload
 *     → enrich HTTP headers (X-Feed, X-Environment, Content-Type)
 *     → POST to endpoint.url (HTTPS with optional mTLS)
 *
 * The Spring Integration channel is populated by whichever source adapter
 * is active (Kafka / RabbitMQ / SMB / File).
 */
@Slf4j
@Component
public class ForwarderRoute extends RouteBuilder {

    private final ForwarderProperties props;
    private final SSLContextParameters outputSslContextParameters;

    /**
     * outputSslContextParameters is optional – only present when
     * {@code endpoint.ssl.enabled=true}.
     */
    public ForwarderRoute(
            ForwarderProperties props,
            @Autowired(required = false)
            @Qualifier("outputSslContextParameters")
            SSLContextParameters outputSslContextParameters) {
        this.props = props;
        this.outputSslContextParameters = outputSslContextParameters;
    }

    @Override
    public void configure() {

        // ── SSL on the Camel HTTP component ──────────────────────────────
        if (outputSslContextParameters != null) {
            HttpComponent http = getContext().getComponent("https", HttpComponent.class);
            http.setSslContextParameters(outputSslContextParameters);
            log.info("Output SSL configured on Camel HTTPS component");
        }

        // ── Error / retry policy ─────────────────────────────────────────
        ForwarderProperties.Endpoint ep = props.getEndpoint();
        errorHandler(defaultErrorHandler()
                .maximumRedeliveries(ep.getMaxRetries())
                .redeliveryDelay(ep.getRetryDelayMs())
                .retryAttemptedLogLevel(LoggingLevel.WARN)
                .useExponentialBackOff());

        // ── Main route ───────────────────────────────────────────────────
        from("spring-integration:forwarderInputChannel")
            .routeId("forwarder-main")
            .log(LoggingLevel.DEBUG, log.getName(), "Received message from source [${headers}]")

            // Normalise payload -------------------------------------------------
            .process(exchange -> {
                Object body = exchange.getIn().getBody();

                // Spring Integration wraps payloads in its own Message type
                if (body instanceof org.springframework.messaging.Message<?> siMsg) {
                    Object payload = siMsg.getPayload();
                    exchange.getIn().setBody(toBytes(payload));
                    // Preserve any SI headers that may be useful
                    siMsg.getHeaders().forEach((k, v) -> {
                        if (!"id".equals(k) && !"timestamp".equals(k)) {
                            exchange.getIn().setHeader("SI_" + k, v);
                        }
                    });
                } else if (body instanceof File file) {
                    // File integration sends java.io.File objects
                    exchange.getIn().setBody(file);
                    exchange.getIn().setHeader(Exchange.FILE_NAME, file.getName());
                } else {
                    exchange.getIn().setBody(toBytes(body));
                }
            })

            // HTTP headers -------------------------------------------------------
            .setHeader(Exchange.HTTP_METHOD,  constant("POST"))
            .setHeader("Content-Type",        constant(ep.getContentType()))
            .setHeader("X-Feed",              constant(ep.getFeed()))
            .setHeader("X-Environment",       constant(ep.getEnvironment()))

            // Remove headers that must not be forwarded to HTTP
            .removeHeaders("SI_*")
            .removeHeader("breadcrumbId")

            // Forward to endpoint ------------------------------------------------
            .toD(resolveEndpointUri())

            .log(LoggingLevel.INFO, log.getName(),
                    "Message forwarded → " + ep.getUrl()
                    + " [feed=" + ep.getFeed() + ", env=" + ep.getEnvironment() + "]");
    }

    // ─── helpers ────────────────────────────────────────────────────────────

    /**
     * Builds the Camel HTTP URI from configured URL, adding required options.
     * Handles both http:// and https:// schemes.
     */
    private String resolveEndpointUri() {
        ForwarderProperties.Endpoint ep = props.getEndpoint();
        String url = ep.getUrl();

        // Strip leading scheme so we can use toD() with the camel http component
        // Camel's http/https component URI: https://host/path?options
        String separator = url.contains("?") ? "&" : "?";
        return url
                + separator
                + "bridgeEndpoint=true"
                + "&throwExceptionOnFailure=true"
                + "&connectTimeout=" + ep.getConnectTimeoutMs()
                + "&socketTimeout="  + ep.getSocketTimeoutMs();
    }

    private static byte[] toBytes(Object value) {
        if (value instanceof byte[] b)   return b;
        if (value instanceof String s)   return s.getBytes(StandardCharsets.UTF_8);
        return value.toString().getBytes(StandardCharsets.UTF_8);
    }
}
